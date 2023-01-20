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

// file = path to .json that defines the QuPath classifier
scriptDir = new File(getClass().protectionDomain.codeSource.location.path).parent
def file = "TissueClass1.json"
// open the file and extract the text
def text = new File(scriptDir,file).text
// use the text to build the classifier
def classifier = GsonTools.getInstance().fromJson(text, qupath.opencv.ml.pixel.OpenCVPixelClassifier)
println classifier
//createAnnotationsFromPixelClassifier(classifier, 0.0, 0.0)