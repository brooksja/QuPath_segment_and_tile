// Enter path for tiles
def targetDir = "/mnt/JD/LIVER/VIENNA_NEW/NAFLD_patches/"

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.Padding;
import qupath.lib.images.servers.PixelType;
import qupath.lib.objects.PathObjects;
import qupath.lib.roi.ROIs;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.projects.Projects;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.roi.RoiTools;
import qupath.lib.io.GsonTools;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.analysis.DistanceTools;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.roi.GeometryTools;
import qupath.imagej.tools.IJTools;
import qupath.opencv.tools.OpenCVTools;
import qupath.opencv.dnn.DnnTools;
import qupath.lib.images.writers.TileExporter;
import qupath.lib.images.servers.ServerTools;
import qupath.opencv.ml.pixel.PixelClassifierTools;
import qupath.opencv.ops.ImageOps;
import qupath.lib.analysis.DelaunayTools;
import qupath.lib.objects.CellTools;
import qupath.lib.analysis.images.ContourTracing;
import qupath.opencv.tools.GroovyCV;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjectPredicates;
import qupath.lib.io.PathIO;
import qupath.lib.io.PointIO;
import qupath.lib.projects.ProjectIO;
import qupath.lib.io.UriUpdater;
import java.awt.image.BufferedImage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.charts.Charts;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.servers.LabeledImageServer;
import qupath.lib.gui.logging.LogManager;
import javafx.application.Platform;
import qupath.lib.gui.scripting.QPEx;
import static qupath.lib.gui.scripting.QPEx.*

import javax.imageio.ImageIO
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.DataBufferByte
import qupath.lib.algorithms.TilerPlugin

// file = path to .json that defines the QuPath classifier
scriptDir = new File(".").getCanonicalPath()
def file = "QuPath_tissue_segment_and_tile/TissueClass1.json"
// open the file and extract the text
def text = new File(scriptDir,file).text
// use the text to build the classifier
def classifier = GsonTools.getInstance().fromJson(text, qupath.opencv.ml.pixel.OpenCVPixelClassifier)
// run classifier to get annotation that defines tissue areas
createAnnotationsFromPixelClassifier(classifier, 0.0, 0.0)


// Tiling...
// From https://forum.image.sc/t/create-tiles-from-annoted-image-in-qupath/68220
def export = true
def extract_um = 256
def tile_px = 224


setImageType('BRIGHTFIELD_H_E');
setColorDeconvolutionStains('{"Name" : "H&E default", "Stain 1" : "Hematoxylin", "Values 1" : "0.65111 0.70119 0.29049 ", "Stain 2" : "Eosin", "Values 2" : "0.2159 0.8012 0.5581 ", "Background" : " 255 255 255 "}');
selectAnnotations();
runPlugin('qupath.lib.algorithms.TilerPlugin', String.format('{"tileSizeMicrons": %d, "trimToROI": false, "makeAnnotations": true, "removeParentAnnotation": true}', extract_um));

def imageData = QPEx.getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def annotations = hierarchy.getFlattenedObjectList(null).findAll {it.isAnnotation()}
def server = getCurrentServer()

// def name = getCurrentImageData().getServer().getPath().replace(".mrxs","")–> returns the image name without the extension

// ATTENTION! Please replace the file type (e.g .mrxs) in the next line to prevent this from being printed
// as extention for the folders and the tiles
def name = server.getMetadata().getName()//.replace(".svs","")
name.take(name.lastIndexOf('.'))
print(name)
// only process images that do not include label or overview in their name
if (!(name.contains("label")) && !(name.contains("overview"))){
    def home_dir = targetDir + name.toString() // to.String() was added!
    QPEx.mkdirs(home_dir)
    def path = buildFilePath(home_dir, String.format("Tile_coords_%s.txt", name))
    def ann_path = buildFilePath(home_dir, String.format("%s.qptxt", name))
    def tile_file = new File(path)
    def ann_file = new File(ann_path)
    tile_file.text = ''
    ann_file.text = ''

    for (obj in annotations) {
        if (obj.isAnnotation()) {
            def roi = obj.getROI()
            // Ignore empty annotations
            if (roi == null) {
                continue
            }
            // If small rectangle, assume image tile, export
            if (roi.getClass() == qupath.lib.roi.RectangleROI && roi.getBoundsWidth()<=(10*extract_um)) {
                def region = RegionRequest.createInstance(server.getPath(), 1.0, roi)
                String tile_name = String.format('%s_(%d,%d)',
                    name,
                    region.getX(),
                    region.getY(),
                )
                def old_img = server.readBufferedImage(region)
                int width_old = old_img.getWidth()
                int height_old = old_img.getHeight()
    
                // Check if tile is mostly background
                // If >50% of pixels >230, then discard
                def gray_list = []
                for (int i=0; i < width_old; i++) {
                    for (int j=0; j < height_old; j++) {
                        int gray = old_img.getRGB(i, j)& 0xFF;
                        gray_list << gray
                    }
                }
                int median_px_i = (width_old * width_old) / 2
                median_px = gray_list.sort()[median_px_i]
                if (median_px > 230) { 
                    print("Tile has >50% brightness >230, discarding")
                    continue
                }
                // Write image tile coords to text file
                tile_file << roi.getAllPoints() << System.lineSeparator()
                BufferedImage img = new BufferedImage(tile_px, tile_px, old_img.getType())
                if (export) {
                    // Resize tile
                    AffineTransform resize = new AffineTransform()
                    resize_factor = tile_px / width_old
                    resize.scale(resize_factor, resize_factor)
                    AffineTransformOp resizeOp = new AffineTransformOp(resize, AffineTransformOp.TYPE_BILINEAR)
                    resizeOp.filter(old_img, img)
                    w = img.getWidth()
                    h = img.getHeight()

                    def fileImage = new File(home_dir, tile_name + ".jpg")
                    print("Writing image tiles for tile " + tile_name)
                    ImageIO.write(img, "jpg", fileImage)
                }

                ann_file << "end" << System.lineSeparator()
            }
        }
    }
    print("Finished processing " + name)
}
