package ch.epfl.biop.bdv.qupath;

import bdv.util.BigWarpHelper;
import bdv.util.QuPathBdvHelper;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.command.register.Wizard2DWholeScanRegisterCommand;
import ch.epfl.biop.spimdata.qupath.QuPathEntryEntity;
import com.google.gson.stream.JsonReader;
import ij.IJ;
import jitk.spline.ThinPlateR2LogRSplineKernelTransform;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.*;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.scijava.Context;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.persist.ScijavaGsonHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Edit QuPath Registration")
public class RegisterQuPathImagesEditCommand implements Command {

    private static Logger logger = LoggerFactory.getLogger(RegisterQuPathImagesEditCommand.class);

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false, style = "message")
    String message = "<html><h1>QuPath registration wizard</h1>Please select a moving and a fixed source<br></html>";

    @Parameter(label = "Fixed source", callback = "updateMessage")
    SourceAndConverter fixed_source;

    @Parameter(label = "Moving source", callback = "updateMessage")
    SourceAndConverter moving_source;

    @Parameter
    CommandService cs;

    @Parameter
    Context scijavaCtx;

    @Parameter
    boolean verbose = false;

    @Override
    public void run() {
        try {


            // - Are they different entries ?
            File moving_entry_folder = QuPathBdvHelper.getDataEntryFolder(moving_source);
            File fixed_entry_folder = QuPathBdvHelper.getDataEntryFolder(fixed_source);

            QuPathEntryEntity movingEntity = QuPathBdvHelper.getQuPathEntityFromSource(moving_source);
            QuPathEntryEntity fixedEntity = QuPathBdvHelper.getQuPathEntityFromSource(fixed_source);

            int moving_series_index = movingEntity.getId();
            int fixed_series_index = fixedEntity.getId();

            String movingToFixedLandmarkName = "transform_"+moving_series_index+"_"+fixed_series_index+".json";

            File result = new File(moving_entry_folder.getAbsolutePath(), movingToFixedLandmarkName);

            if (!result.exists()) {
                IJ.error("Registration file not found");
            } else {

                RealTransform rt = performBigWarpEdition(result);
            }

            IJ.log("Transformation file successfully written to QuPath project: "+result);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private RealTransform performBigWarpEdition(File result) throws Exception {
        JsonReader reader = new JsonReader(new FileReader(result));
        InvertibleRealTransformSequence irts = ScijavaGsonHelper.getGson(scijavaCtx).fromJson(reader, RealTransform.class);
        RealTransform transformation = RealTransformHelper.getTransformSequence(irts).get(1);

        ThinplateSplineTransform tst = (ThinplateSplineTransform)
                ((WrappedIterativeInvertibleRealTransform)
                        ((Wrapped2DTransformAs3D)transformation).getTransform())
                        .getTransform();

        ThinPlateR2LogRSplineKernelTransform kernel = ThinPlateSplineTransformAdapter.getKernel(tst);
        double[][] pts_src = ThinPlateSplineTransformAdapter.getSrcPts(kernel);
        double[][] pts_tgt = ThinPlateSplineTransformAdapter.getTgtPts(kernel);

        List<RealPoint> movingPts = new ArrayList<>();
        List<RealPoint> fixedPts = new ArrayList<>();

        for (int i = 0; i<kernel.getNumLandmarks(); i++) {
            RealPoint moving = new RealPoint(3);
            RealPoint fixed = new RealPoint(3);
            for (int d = 0; d<kernel.getNumDims();d++) { // num dims should be 2!
                moving.setPosition(pts_tgt[d][i], d);
                fixed.setPosition(pts_src[d][i], d);
                System.out.println("d:"+d);
            }
            movingPts.add(moving);
            fixedPts.add(fixed);
        }




        return null;
    }

    public void updateMessage() {

        String message = "<html><h1>QuPath registration wizard</h1>";

        if (fixed_source==null) {
            message+="Please select a fixed source <br>";
        } else {
            if (!QuPathBdvHelper.isSourceDirectlyLinkedToQuPath(fixed_source)) {
                message+="The fixed source is not originating from a QuPath project! <br>";
            } else {
                if (moving_source == null) {
                    message += "Please select a moving source <br>";
                } else {
                    if (!QuPathBdvHelper.isSourceDirectlyLinkedToQuPath(moving_source)) {
                        message += "The moving source is not originating from a QuPath project! <br>";
                    } else {
                        try {
                            String qupathProjectMoving = QuPathBdvHelper.getQuPathProjectFile(moving_source).getAbsolutePath();
                            String qupathProjectFixed = QuPathBdvHelper.getQuPathProjectFile(fixed_source).getAbsolutePath();
                            if (!qupathProjectMoving.equals(qupathProjectFixed)) {
                                message+="Error : the moving source and the fixed source are not from the same qupath project";
                            } else {
                                // - Are they different entries ?
                                File moving_entry_folder = QuPathBdvHelper.getDataEntryFolder(moving_source);
                                File fixed_entry_folder = QuPathBdvHelper.getDataEntryFolder(fixed_source);
                                if (moving_entry_folder.getAbsolutePath().equals(fixed_entry_folder.getAbsolutePath())) {
                                    message+="Error : moving and fixed source should belong to different qupath entries. <br>";
                                    message+="You can't move two channels of the same image, <br>";
                                    message+="unless you duplicate the images in QuPath. <br>";
                                    message+="<ul>";
                                    message += "<li>Fixed: "+fixed_source.getSpimSource().getName()+"</li>";
                                    message += "<li>Moving: "+moving_source.getSpimSource().getName()+"</li>";
                                    message+="<ul>";
                                } else {
                                    message += "Registration task properly set: <br>";

                                    message+="<ul>";
                                    message += "<li>Fixed: "+fixed_source.getSpimSource().getName()+"</li>";
                                    message += "<li>Moving: "+moving_source.getSpimSource().getName()+"</li>";
                                    message+="</ul>";

                                    QuPathEntryEntity movingEntity = QuPathBdvHelper.getQuPathEntityFromSource(moving_source);
                                    QuPathEntryEntity fixedEntity = QuPathBdvHelper.getQuPathEntityFromSource(fixed_source);

                                    int moving_series_index = movingEntity.getId();
                                    int fixed_series_index = fixedEntity.getId();

                                    String movingToFixedLandmarkName = "transform_"+moving_series_index+"_"+fixed_series_index+".json";

                                    File result = new File(moving_entry_folder.getAbsolutePath(), movingToFixedLandmarkName);
                                    if (!result.exists()) {
                                        message+="WARNING! REGISTRATION FILE NOT FOUND!<br>";
                                    }

                                    movingToFixedLandmarkName = "transform_"+fixed_series_index+"_"+moving_series_index+".json";

                                    result = new File(fixed_entry_folder.getAbsolutePath(), movingToFixedLandmarkName);
                                    if (result.exists()) {
                                        message+="WARNING! AN <b>INVERSE</b> REGISTRATION FILE ALREADY EXISTS! <br>";
                                        message+="Switch your fixed and moving selected source<br>";
                                    }

                                }
                            }
                        } catch (Exception e) {
                            message+= "Could not fetch the QuPath project error: "+e.getMessage()+"<br>";
                        }
                    }
                }
            }
        }

        message+="</html>";
        this.message = message;

    }


}
