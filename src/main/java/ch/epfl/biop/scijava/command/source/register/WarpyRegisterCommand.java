package ch.epfl.biop.scijava.command.source.register;

import bdv.util.QuPathBdvHelper;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.qupath.entity.QuPathEntryEntity;
import ij.IJ;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;
import org.apache.commons.io.FileUtils;
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
import java.nio.charset.Charset;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>QuPath - Create Warpy Registration")
public class WarpyRegisterCommand implements Command {

    private static Logger logger = LoggerFactory.getLogger(WarpyRegisterCommand.class);

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

            // Several checks before we even start the registration
            //  - Is there an associated qupath project ?
            if (!QuPathBdvHelper.isSourceDirectlyLinkedToQuPath(fixed_source)) {
                logger.error("Error : the fixed source is not associated to a QuPath project");
                IJ.error("Error : the fixed source is not associated to a QuPath project");
                return;
            }
            if (!QuPathBdvHelper.isSourceDirectlyLinkedToQuPath(moving_source)) {
                logger.error("Error : the moving source is not associated to a QuPath project");
                IJ.error("Error : the moving source is not associated to a QuPath project");
                return;
            }

            // - Is it the same project ?
            String qupathProjectMoving = QuPathBdvHelper.getQuPathProjectFile(moving_source).getAbsolutePath();
            String qupathProjectFixed = QuPathBdvHelper.getQuPathProjectFile(fixed_source).getAbsolutePath();

            if (!qupathProjectMoving.equals(qupathProjectFixed)) {
                logger.error("Error : the moving source and the fixed source are not from the same qupath project");
                IJ.error("Error : the moving source and the fixed source are not from the same qupath project");
                return;
            }

            // - Are they different entries ?
            File moving_entry_folder = QuPathBdvHelper.getDataEntryFolder(moving_source);
            File fixed_entry_folder = QuPathBdvHelper.getDataEntryFolder(fixed_source);

            if (moving_entry_folder.getAbsolutePath().equals(fixed_entry_folder.getAbsolutePath())) {
                logger.error("Error : the moving source and the fixed source should belong to different qupath entries (you can't move two channels of the same image, unless you duplicate the images in QuPath)");
                IJ.error("Error : the moving source and the fixed source should belong to different qupath entries (you can't move two channels of the same image, unless you duplicate the images in QuPath)");
                return;
            }

            CommandModule module = cs.run(Wizard2DWholeScanRegisterCommand.class, true,
                    "fixed", fixed_source,
                    "moving", moving_source,
                    "verbose", verbose,
                    "background_offset_value_moving", 0,
                    "background_offset_value_fixed", 0,
                    "sourcesToTransform", new SourceAndConverter[]{moving_source}
            ).get();

            // Get transform
            RealTransform rt = (RealTransform) module.getOutput("transformation");

            // We don't want to keep the transformed sources in memory
            SourceAndConverterServices
                    .getSourceAndConverterService()
                    .remove((SourceAndConverter[]) module.getOutput("transformedSources"));

            RealTransform transformSequence;

            // Because QuPath works in pixel coordinates and bdv playground in real space coordinates
            // We need to account for this

            AffineTransform3D movingToPixel = new AffineTransform3D();

            moving_source.getSpimSource().getSourceTransform(0,0,movingToPixel);

            AffineTransform3D fixedToPixel = new AffineTransform3D();

            fixed_source.getSpimSource().getSourceTransform(0,0,fixedToPixel);

            if (rt instanceof InvertibleRealTransform) {
                InvertibleRealTransformSequence irts = new InvertibleRealTransformSequence();

                irts.add(fixedToPixel);
                irts.add((InvertibleRealTransform) rt);
                irts.add(movingToPixel.inverse());

                transformSequence = irts;

            } else {
                RealTransformSequence rts = new RealTransformSequence();

                rts.add(fixedToPixel);
                rts.add(rt);
                rts.add(movingToPixel.inverse());

                transformSequence = rts;
            }

            String jsonMovingToFixed = ScijavaGsonHelper.getGson(scijavaCtx).toJson(transformSequence, RealTransform.class);

            QuPathEntryEntity movingEntity = QuPathBdvHelper.getQuPathEntityFromSource(moving_source);
            QuPathEntryEntity fixedEntity = QuPathBdvHelper.getQuPathEntityFromSource(fixed_source);

            int moving_series_index = movingEntity.getId();
            int fixed_series_index = fixedEntity.getId();

            String movingToFixedLandmarkName = "transform_"+moving_series_index+"_"+fixed_series_index+".json";

            File result = new File(moving_entry_folder.getAbsolutePath(), movingToFixedLandmarkName);
            FileUtils.writeStringToFile(result, jsonMovingToFixed, Charset.defaultCharset());

            IJ.log("Fixed: "+fixed_source.getSpimSource().getName()+" | Moving: "+moving_source.getSpimSource().getName());
            IJ.log("Transformation file successfully written to QuPath project: "+result);

        } catch (Exception e) {
            e.printStackTrace();
        }

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
                                    if (result.exists()) {
                                        message+="WARNING! A REGISTRATION FILE ALREADY EXISTS! You can:<br>";
                                        message+="<ul><li> Click <b>OK</b> to re-run the wizard and override it</li>";
                                        message+="<li> Run the command <b>Edit QuPath Registration</b> to edit this existing registration</li></ul>";
                                    }

                                    movingToFixedLandmarkName = "transform_"+fixed_series_index+"_"+moving_series_index+".json";

                                    result = new File(fixed_entry_folder.getAbsolutePath(), movingToFixedLandmarkName);
                                    if (result.exists()) {
                                        message+="WARNING! AN <b>INVERSE</b> REGISTRATION FILE ALREADY EXISTS! You can:<br>";
                                        message+="<ul><li> Switch your fixed and moving selected source</li>";
                                        message+="<li> Erase the transformation file and run the wizard.</li></ul>";
                                        message+="Keeping both the forward and inverse transformation <br>";
                                        message+="will cause some ambiguity in QuPath. <br>";
                                        message+="Inverse transformation file:<br>";
                                        message+= result.getAbsolutePath().replace("\\","\\\\")+"<br>";
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
