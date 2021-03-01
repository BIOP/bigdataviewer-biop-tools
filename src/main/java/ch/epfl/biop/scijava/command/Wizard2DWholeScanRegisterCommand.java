package ch.epfl.biop.scijava.command;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.userdefinedregion.GetUserPointsCommand;
import ch.epfl.biop.bdv.userdefinedregion.GetUserRectangleCommand;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.RealTransform;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;

import java.util.Arrays;
import java.util.List;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Wizard Align Slides (2D)")
public class Wizard2DWholeScanRegisterCommand implements BdvPlaygroundActionCommand{

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String message = "TODO : explain what this wizard does";

    @Parameter
    CommandService cs;

    @Parameter(label = "Reference Source (fixed)")
    SourceAndConverter fixed;

    @Parameter(label = "Moving source used for registration to the reference")
    SourceAndConverter moving;

    @Parameter(label = "Sources to transform, including the moving source if needed")
    SourceAndConverter[] sourcesToTransform;

    @Parameter
    BdvHandle bdvh;

    /*@Parameter(label = "Pixel size used for registration (physical unit)")
    double pixelPhysicalSizeUsedForRegistration;

    @Parameter(label = "Field of view size for registration (physical unit)")
    double fovSize;*/

    @Parameter
    SourceAndConverterBdvDisplayService sacbds;

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter[] transformedSources;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform transformation;

    @Override
    public void run() {
        // Make source the relevant sources are displayed
        if (!bdvh.getViewerPanel().state().containsSource(fixed)) {
            sacbds.show(bdvh,fixed);
        }
        if (!bdvh.getViewerPanel().state().containsSource(moving)) {
            sacbds.show(bdvh,moving);
        }

        try {
            // Ask the user to select the region that should be aligned ( a rectangle )
            List<RealPoint> corners = (List<RealPoint>) cs
                    .run(GetUserRectangleCommand.class, true,
                    "messageForUser", "Select a rectangular region for the region you'd like to register.",
                            "timeOutInMs",-1)
                    .get().getOutput("pts");

            // Ask the user to select the points where the fine tuning should be performed
            List<RealPoint> ptsForRegistration = (List<RealPoint>) cs
                    .run(GetUserPointsCommand.class, true,
                            "messageForUser", "Select the position of the landmarks that will be used for the registration.",
                            "timeOutInMs",-1)
                    .get().getOutput("pts");


            String ptCoords = "";

            if (ptsForRegistration.size()<4) {
                System.err.println("At least 4 points should be selected");
                return;
            }

            for (RealPoint pt : ptsForRegistration) {
                ptCoords+=pt.getDoublePosition(0)+","+pt.getDoublePosition(1)+",";
            }

            transformation = (RealTransform) cs.run(RegisterWholeSlideScans2DCommand.class, true,
                        "globalRefSource", fixed,
                               "currentRefSource", moving,
                               "ptListCoordinates", ptCoords,
                               "topLeftX", Math.min(corners.get(0).getDoublePosition(0),corners.get(1).getDoublePosition(0) ),
                               "topLeftY", Math.min(corners.get(0).getDoublePosition(1),corners.get(1).getDoublePosition(1) ),
                               "bottomRightX", Math.max(corners.get(0).getDoublePosition(0),corners.get(1).getDoublePosition(0) ),
                               "bottomRightY", Math.max(corners.get(0).getDoublePosition(1),corners.get(1).getDoublePosition(1) ),
                               "showDetails", true
                    ).get().getOutput("rts");

            // Now transforms all the sources required to be transformed

            SourceRealTransformer srt = new SourceRealTransformer(null,transformation);

            transformedSources = Arrays.stream(sourcesToTransform)
                    .map(srt)
                    .toArray(SourceAndConverter[]::new);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
