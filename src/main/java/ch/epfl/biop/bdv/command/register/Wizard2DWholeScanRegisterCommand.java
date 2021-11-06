package ch.epfl.biop.bdv.command.register;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.command.userdefinedregion.GetUserPointsCommand;
import ch.epfl.biop.bdv.command.userdefinedregion.GetUserRectangleCommand;
import ch.epfl.biop.bdv.command.userdefinedregion.PointsSelectorBehaviour;
import ch.epfl.biop.bdv.gui.card.CardHelper;
import ch.epfl.biop.bdv.gui.graphicalhandle.GraphicalHandle;
import ch.epfl.biop.bdv.gui.graphicalhandle.XYRectangleGraphicalHandle;
import ij.IJ;
import ij.gui.WaitForUserDialog;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.bdv.ManualRegistrationStarter;
import sc.fiji.bdvpg.bdv.ManualRegistrationStopper;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.command.source.BrightnessAdjusterCommand;
import sc.fiji.bdvpg.scijava.command.source.ManualTransformCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.sourceandconverter.register.BigWarpLauncher;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

import javax.swing.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static bdv.ui.BdvDefaultCards.*;
import static bdv.util.RealTransformHelper.BigWarpFileFromRealTransform;
import static ch.epfl.biop.bdv.command.userdefinedregion.RectangleSelectorBehaviour.box;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Wizard Align Slides (2D)",
        headless = false // User interface required
        )
public class Wizard2DWholeScanRegisterCommand implements BdvPlaygroundActionCommand{

    private static Logger logger = LoggerFactory.getLogger(Wizard2DWholeScanRegisterCommand.class);

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String message = "<html><h2>Automated WSI registration wizard</h2><br/>"+
            "Images should be opened with bigdataviewer-playground<br/>"+
            "Automated registrations requires elastix.<br/>"+
            "</html>";

    @Parameter
    CommandService cs;

    @Parameter(label = "Fixed reference source")
    SourceAndConverter fixed;

    @Parameter(label = "Background offset value for moving image")
    double background_offset_value_moving = 0;

    @Parameter(label = "Background offset value for fixed image")
    double background_offset_value_fixed = 0;

    @Parameter(label = "Moving source used for registration to the reference")
    SourceAndConverter moving;

    @Parameter(label = "Sources to transform, including the moving source if needed")
    SourceAndConverter[] sourcesToTransform;

    //@Parameter
    BdvHandle bdvh;

    @Parameter(label = "Remove images z-offsets")
    boolean removeZOffset = true;

    @Parameter(label = "Center moving image with fixed image")
    boolean centerMovingImage = true;

    @Parameter(label = "0 - Manual rigid registration")
    boolean manualRigidRegistration = true;

    @Parameter(label = "1 - Auto affine registration")
    boolean automatedAffineRegistration = true;

    @Parameter(label = "2 - Semi auto spline registration")
    boolean automatedSplineRegistration = true;

    @Parameter(label = "3 - Manual spline registration (BigWarp)")
    boolean manualSplineRegistration = true;

    @Parameter(label = "Pixel size for coarse registration in mm (default 0.01)", style = "format:0.000", persist = false)
    double coarsePixelSize_mm = 0.01;

    @Parameter(label = "Patch size for registration in mm (default 0.5)", style = "format:0.000", persist = false)
    double patchSize_mm = 0.5;

    @Parameter(label = "Pixel size for precise patch registration in mm (default 0.001)", style = "format:0.000", persist = false)
    double precisePixelSize_mm = 0.001;

    @Parameter(label = "Number of iterations for each scale (default 100)")
    int maxIterationNumberPerScale = 100;

    @Parameter(label = "Show results of automated registrations (breaks parallelization)")
    boolean showDetails = false;

    @Parameter
    boolean verbose;

    @Parameter
    SourceAndConverterBdvDisplayService sacbds;

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter[] transformedSources;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform transformation;

    BiConsumer<String,String> waitForUser = (windowtitle, message) -> {
        IJ.log("Registration Wizard - wait: "+message);
        WaitForUserDialog dialog = new WaitForUserDialog(windowtitle,message);
        dialog.show();
    };

    // Rectangle properties
    List<RealPoint> corners;
    double topLeftX, topLeftY, bottomRightX, bottomRightY, cx, cy;

    List<RealPoint> landmarks = new ArrayList<>();

    private boolean manualRegistrationStopped = false;

    @Override
    public void run() {

        bdvh = SourceAndConverterServices.getBdvDisplayService().getNewBdv();

        //waitForUser.accept("Prepare your bigdataviewer window","Fit the image onto the bdv window.");
        if ((!automatedAffineRegistration)&&(!automatedSplineRegistration)&&(!manualSplineRegistration)) {
            System.err.println("You need to select at least one sort of registration!");
            return;
        }

        // These transforms are removed at the end in the wizard
        AffineTransform3D preTransformFixed = new AffineTransform3D();
        AffineTransform3D preTransfromedMoving = new AffineTransform3D();

        if (removeZOffset) {
            AffineTransform3D at3D = new AffineTransform3D();
            moving.getSpimSource().getSourceTransform(0,0,at3D);
            preTransformFixed.translate(0,0,-at3D.get(2,3)); // Removes z offset
            fixed.getSpimSource().getSourceTransform(0,0,at3D);
            preTransfromedMoving.translate(0,0,-at3D.get(2,3)); // Removes z offset
        }

        if (centerMovingImage) {
            RealPoint centerMoving = SourceAndConverterHelper.getSourceAndConverterCenterPoint(moving);
            RealPoint centerFixed = SourceAndConverterHelper.getSourceAndConverterCenterPoint(fixed);
            preTransfromedMoving.translate(
                    centerFixed.getDoublePosition(0)-centerMoving.getDoublePosition(0),
                    centerFixed.getDoublePosition(1)-centerMoving.getDoublePosition(1),
                    centerFixed.getDoublePosition(2)-centerMoving.getDoublePosition(2)
                    );
        }

        SourceAndConverter newFixed = SourceTransformHelper.createNewTransformedSourceAndConverter(preTransformFixed, new SourceAndConverterAndTimeRange(fixed,0));
        SourceAndConverter newMoving = SourceTransformHelper.createNewTransformedSourceAndConverter(preTransfromedMoving, new SourceAndConverterAndTimeRange(moving,0));

        SourceAndConverterServices.getBdvDisplayService().remove(bdvh, new SourceAndConverter[]{fixed, moving});
        moving = newMoving;
        fixed = newFixed;
        SourceAndConverterServices.getBdvDisplayService().show(bdvh, new SourceAndConverter[]{fixed, moving});

        // Make sure the relevant sources are displayed
        showImagesIfNecessary();

        if (manualRigidRegistration) {
            addCardPanelRigidRegistration();
            while (manualRegistrationStopped == false) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            removeCardPanelRigidRegistration();
        }

        try {

            // Ask the user to select the region that should be aligned ( a rectangle ) - in any case
            getUserRectangle();

            if (automatedSplineRegistration) {
                // Ask the user to select the points where the fine tuning should be performed
                getUserLandmarks();
                if (landmarks.size()<4) {
                    logger.error("At least 4 points should be selected");
                    return;
                }
            } else {
                // Let's keep corners for editing registration
                for (RealPoint pt : corners) {
                    landmarks.add(new RealPoint(pt)); // points are being copied
                }
            }

            // Conversion of RealPoints to String representation for next command launching
            String ptCoords = "";
            if (automatedSplineRegistration) {
                for (RealPoint pt : landmarks) {
                    ptCoords += pt.getDoublePosition(0) + "," + pt.getDoublePosition(1) + ",";
                }
            }
            SourceAndConverterServices.getBdvDisplayService().closeBdv(bdvh);
            bdvh.close();

            IJ.log("Registration started...");

            // Coarse and spline registration - if selected by the user
            transformation = (RealTransform) cs.run(RegisterWholeSlideScans2DCommand.class, true,
                        "globalRefSource", fixed,
                               "currentRefSource", moving,
                               "ptListCoordinates", ptCoords,
                               "topLeftX", topLeftX,
                               "topLeftY", topLeftY,
                               "bottomRightX", bottomRightX,
                               "bottomRightY", bottomRightY,
                               "showDetails", showDetails,
                               "verbose", verbose,
                               "performFirstCoarseAffineRegistration",  automatedAffineRegistration,
                               "performSecondSplineRegistration", automatedSplineRegistration,
                               "maxIterationNumberPerScale", maxIterationNumberPerScale,
                               "background_offset_value_moving", background_offset_value_moving,
                               "background_offset_value_fixed", background_offset_value_fixed,
                               "precisePixelSize_mm", precisePixelSize_mm,
                               "patchSize_mm", patchSize_mm,
                               "coarsePixelSize_mm", coarsePixelSize_mm
                    ).get().getOutput("tst");


            IJ.log("Registration DONE.");

            if (manualSplineRegistration) {
                // The user wants big warp to correct landmark points
                List<SourceAndConverter> movingSacs = Arrays.stream(new SourceAndConverter[]{moving}).collect(Collectors.toList());

                List<SourceAndConverter> fixedSacs = Arrays.stream(new SourceAndConverter[]{fixed}).collect(Collectors.toList());

                List<ConverterSetup> converterSetups = Arrays.stream(new SourceAndConverter[]{moving}).map(src -> SourceAndConverterServices.getBdvDisplayService().getConverterSetup(src)).collect(Collectors.toList());

                converterSetups.addAll(Arrays.stream(new SourceAndConverter[]{fixed}).map(src -> SourceAndConverterServices.getBdvDisplayService().getConverterSetup(src)).collect(Collectors.toList()));

                // Launch BigWarp
                BigWarpLauncher bwl = new BigWarpLauncher(movingSacs, fixedSacs, "Big Warp", converterSetups);
                bwl.set2d();
                bwl.run();

                // Output bdvh handles -> will be put in the object service
                BdvHandle bdvhQ = bwl.getBdvHandleQ();
                BdvHandle bdvhP = bwl.getBdvHandleP();

                SourceAndConverterServices.getBdvDisplayService().pairClosing(bdvhQ,bdvhP);

                bdvhP.getViewerPanel().requestRepaint();
                bdvhQ.getViewerPanel().requestRepaint();

                bwl.getBigWarp().getLandmarkFrame().repaint();

                bwl.getBigWarp().loadLandmarks(BigWarpFileFromRealTransform(transformation));

                bwl.getBigWarp().setIsMovingDisplayTransformed(true);

                // Adjusting BigWarp View on user ROI
                double sizeX = Math.abs((topLeftX-bottomRightX)*1.25f); // 25% margin

                AffineTransform3D at3D = new AffineTransform3D();
                bdvhP.getViewerPanel().state().getViewerTransform(at3D);

                if (sizeX!=0) {
                    at3D.scale(bdvhP.getViewerPanel().getWidth() / (sizeX*at3D.get(0,0))  );
                    bdvhP.getViewerPanel().state().setViewerTransform(at3D);
                    bdvhQ.getViewerPanel().state().setViewerTransform(at3D);
                }

                // Centers bdv on ROI center
                fitBdvOnUserROI(bdvhQ);
                fitBdvOnUserROI(bdvhP);
                
                waitForUser.accept("Manual spline registration", "Please perform carefully your registration then press ok.");

                transformation = bwl.getBigWarp().getBwTransform().getTransformation().copy();

                bwl.getBigWarp().closeAll();
            }

            // Now transforms all the sources required to be transformed

            SourceRealTransformer srt = new SourceRealTransformer(null,transformation);

            transformedSources = Arrays.stream(sourcesToTransform)
                    .map(srt)
                    .toArray(SourceAndConverter[]::new);

            // Cleaning temporary transformed working sources
            SourceAndConverterServices
                    .getSourceAndConverterService()
                    .remove(moving, fixed);

            // Potentially releasing resources
            manualRegistrationStarter = null;
            manualRegistrationStopper = null;

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    CardHelper.CardState iniCardState;
    boolean rigidRegistrationStarted = false;
    ManualRegistrationStarter manualRegistrationStarter;
    ManualRegistrationStopper manualRegistrationStopper;

    private void addCardPanelRigidRegistration() {

        manualRegistrationStarter = new ManualRegistrationStarter(bdvh, moving);
        manualRegistrationStopper = new ManualRegistrationStopper(manualRegistrationStarter, SourceTransformHelper::mutate);

        iniCardState = CardHelper.getCardState(bdvh);

        AffineTransform3D initialView = bdvh.getViewerPanel().state().getViewerTransform();

        JButton restoreView = new JButton("Start manual rigid registration");

        restoreView.addActionListener((e)-> {
            if (rigidRegistrationStarted) {
                bdvh.getViewerPanel().state().setViewerTransform(initialView);
                (new ManualRegistrationStopper(manualRegistrationStarter, SourceTransformHelper::cancel)).run();
                manualRegistrationStarter = new ManualRegistrationStarter(bdvh, moving);
                manualRegistrationStopper = new ManualRegistrationStopper(manualRegistrationStarter, SourceTransformHelper::mutate);
                rigidRegistrationStarted = false;
                restoreView.setText("Start manual rigid registration");
            } else {
                manualRegistrationStarter.run();
                rigidRegistrationStarted = true;
                restoreView.setText("Restore original state");
            }
        });

        JButton confirmationButton = new JButton("Confirm transformation");
        confirmationButton.addActionListener((e) -> {
            if (rigidRegistrationStarted) {
                manualRegistrationStopper.run();
            }
            manualRegistrationStopped = true;
        });

        JButton autoScaleMoving = new JButton("Autoscale B&C (moving)");
        autoScaleMoving.addActionListener((e) -> {
            bdvh.getViewerPanel().showMessage("Autoscaling moving image");
            new BrightnessAutoAdjuster(moving,0).run();
        });

        JButton autoScaleFixed = new JButton("Autoscale B&C (fixed)");
        autoScaleFixed.addActionListener((e) -> {
            bdvh.getViewerPanel().showMessage("Autoscaling fixed image");
            new BrightnessAutoAdjuster(fixed,0).run();
        });

        JPanel cardpanel = box(false,
                new JLabel("Perform manual rigid registration"),
                box(true, autoScaleMoving, autoScaleFixed),
                restoreView,
                confirmationButton);

        bdvh.getSplitPanel().setCollapsed(false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCEGROUPS_CARD, false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_VIEWERMODES_CARD, false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCES_CARD, false);
        bdvh.getCardPanel().addCard("Manual rigid registration", cardpanel, true);
    }

    private void removeCardPanelRigidRegistration() {
        CardHelper.restoreCardState(bdvh, iniCardState);
        bdvh.getCardPanel().removeCard("Manual rigid registration");
    }

    private void getUserLandmarks() throws Exception {
        landmarks = (List<RealPoint>) cs
                .run(GetUserPointsCommand.class, true,
                        "messageForUser", "Select the position of the landmarks that will be used for the registration (at least 4).",
                        "timeOutInMs", -1,
                        "graphicalHandleSupplier",
                        (Function<RealPoint, GraphicalHandle>) realPoint ->
                                new XYRectangleGraphicalHandle(
                                        bdvh.getViewerPanel().state(),
                                        () -> realPoint,
                                        () -> patchSize_mm, // To improve, clearly!
                                        () -> patchSize_mm, // To improve, clearly!
                                        () -> PointsSelectorBehaviour.defaultLandmarkColor )
                        )
                .get().getOutput("pts");
    }

    private void getUserRectangle() throws Exception {
        // Gets estimated corners to initialize the rectangle
        RealInterval box = getBoundingBox();

        CommandModule module = cs.run(GetUserRectangleCommand.class, true,
                        "messageForUser", "Select a rectangular region for the region you'd like to register.",
                        "timeOutInMs", -1,
                        "p1", box.minAsRealPoint(),
                        "p2", box.maxAsRealPoint()).get();
        corners = new ArrayList<>();
        corners.add((RealPoint) module.getOutput("p1"));
        corners.add((RealPoint) module.getOutput("p2"));

        topLeftX = Math.min(corners.get(0).getDoublePosition(0),corners.get(1).getDoublePosition(0) );
        topLeftY = Math.min(corners.get(0).getDoublePosition(1),corners.get(1).getDoublePosition(1) );
        bottomRightX = Math.max(corners.get(0).getDoublePosition(0),corners.get(1).getDoublePosition(0) );
        bottomRightY = Math.max(corners.get(0).getDoublePosition(1),corners.get(1).getDoublePosition(1) );

        cx = (topLeftX+bottomRightX)/2.0;
        cy = (topLeftY+bottomRightY)/2.0;
    }

    RealInterval getBoundingBox() {
        SourceAndConverter[] sources = new SourceAndConverter[]{moving, fixed};
        List<RealInterval> intervalList = (List)Arrays.asList(sources).stream().filter((sourceAndConverter) -> {
            return sourceAndConverter.getSpimSource() != null;
        }).filter((sourceAndConverter) -> {
            return sourceAndConverter.getSpimSource().isPresent(0);
        }).map((sourceAndConverter) -> {
            Interval interval = sourceAndConverter.getSpimSource().getSource(0, 0);
            AffineTransform3D sourceTransform = new AffineTransform3D();
            sourceAndConverter.getSpimSource().getSourceTransform(0, 0, sourceTransform);
            RealPoint corner0 = new RealPoint(new float[]{(float)interval.min(0), (float)interval.min(1), (float)interval.min(2)});
            RealPoint corner1 = new RealPoint(new float[]{(float)interval.max(0), (float)interval.max(1), (float)interval.max(2)});
            sourceTransform.apply(corner0, corner0);
            sourceTransform.apply(corner1, corner1);
            return new FinalRealInterval(new double[]{Math.min(corner0.getDoublePosition(0), corner1.getDoublePosition(0)), Math.min(corner0.getDoublePosition(1), corner1.getDoublePosition(1)), Math.min(corner0.getDoublePosition(2), corner1.getDoublePosition(2))}, new double[]{Math.max(corner0.getDoublePosition(0), corner1.getDoublePosition(0)), Math.max(corner0.getDoublePosition(1), corner1.getDoublePosition(1)), Math.max(corner0.getDoublePosition(2), corner1.getDoublePosition(2))});
        }).filter((object) -> {
            return object != null;
        }).collect(Collectors.toList());
        RealInterval maxInterval = (RealInterval)intervalList.stream().reduce((i1, i2) -> {
            return new FinalRealInterval(new double[]{Math.min(i1.realMin(0), i2.realMin(0)), Math.min(i1.realMin(1), i2.realMin(1)), Math.min(i1.realMin(2), i2.realMin(2))}, new double[]{Math.max(i1.realMax(0), i2.realMax(0)), Math.max(i1.realMax(1), i2.realMax(1)), Math.max(i1.realMax(2), i2.realMax(2))});
        }).get();
        return maxInterval;
    }


    private void showImagesIfNecessary() {
        if (!bdvh.getViewerPanel().state().containsSource(fixed)) {
            sacbds.show(bdvh,fixed);
        }

        if (!bdvh.getViewerPanel().state().containsSource(moving)) {
            sacbds.show(bdvh,moving);
        }

        new ViewerTransformAdjuster(bdvh, new SourceAndConverter[]{fixed, moving}).run();
    }

    void fitBdvOnUserROI(BdvHandle bdvh) {
        AffineTransform3D newCenter = BdvHandleHelper.getViewerTransformWithNewCenter(bdvh, new double[]{cx,cy,0});
        bdvh.getViewerPanel().state().setViewerTransform(newCenter);
    }
}
