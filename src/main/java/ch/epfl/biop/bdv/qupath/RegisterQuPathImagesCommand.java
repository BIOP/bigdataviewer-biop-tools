package ch.epfl.biop.bdv.qupath;

import bdv.util.QuPathBdvHelper;
import bdv.util.RealTransformHelper;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.command.register.RegisterWholeSlideScans2DCommand;
import ch.epfl.biop.bdv.command.register.Wizard2DWholeScanRegisterCommand;
import ch.epfl.biop.spimdata.qupath.QuPathEntryEntity;
import ij.gui.WaitForUserDialog;
import net.imglib2.realtransform.*;
import org.apache.commons.io.FileUtils;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.persist.ScijavaGsonHelper;

import java.io.File;
import java.nio.charset.Charset;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Wizard Align Slides For QuPath")
public class RegisterQuPathImagesCommand implements Command {

    private static Logger logger = LoggerFactory.getLogger(RegisterQuPathImagesCommand.class);

    @Parameter(label = "Fixed source from a QuPath generated dataset (see Open QuPath Project)")
    SourceAndConverter fixed_source;

    @Parameter(label = "Moving source from a QuPath generated dataset (see Open QuPath Project)")
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
                return;
            }
            if (!QuPathBdvHelper.isSourceDirectlyLinkedToQuPath(moving_source)) {
                logger.error("Error : the moving source is not associated to a QuPath project");
                return;
            }

            // - Is it the same project ?
            String qupathProjectMoving = QuPathBdvHelper.getQuPathProjectFile(moving_source).getAbsolutePath();
            String qupathProjectFixed = QuPathBdvHelper.getQuPathProjectFile(fixed_source).getAbsolutePath();

            if (!qupathProjectMoving.equals(qupathProjectFixed)) {
                logger.error("Error : the moving source and the fixed source are not from the same qupath project");
                return;
            }

            // - Are they different entries ?
            File moving_entry_folder = QuPathBdvHelper.getDataEntryFolder(moving_source);
            File fixed_entry_folder = QuPathBdvHelper.getDataEntryFolder(fixed_source);

            if (moving_entry_folder.getAbsolutePath().equals(fixed_entry_folder.getAbsolutePath())) {
                logger.error("Error : the moving source and the fixed source should be in a different entry ( do not select two channels of the same image)");
                return;
            }

            // Ok.
            RealTransform rt = (RealTransform) cs.run(Wizard2DWholeScanRegisterCommand.class, true,
                    "fixed", fixed_source,
                    "moving", moving_source,
                    "verbose", verbose,
                    "background_offset_value_moving", 0,
                    "background_offset_value_fixed", 0,
                    "sourcesToTransform", new SourceAndConverter[]{moving_source}
                    ).get().getOutput("transformation");

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

            FileUtils.writeStringToFile(new File(moving_entry_folder.getAbsolutePath(), movingToFixedLandmarkName), jsonMovingToFixed, Charset.defaultCharset());

            new WaitForUserDialog("Registration finished", "Transformation file successfully written to QuPath project.").show();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
