package ch.epfl.biop.scijava.command.source.register;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.*;
import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import bigwarp.BigWarp;
import ch.epfl.biop.scijava.command.bdv.userdefinedregion.GetUserPointsCommand;
import ch.epfl.biop.scijava.command.bdv.userdefinedregion.GetUserRectangleCommand;
import ch.epfl.biop.scijava.command.bdv.userdefinedregion.PointsSelectorBehaviour;
import ch.epfl.biop.bdv.gui.card.CardHelper;
import ch.epfl.biop.bdv.gui.graphicalhandle.GraphicalHandle;
import ch.epfl.biop.bdv.gui.graphicalhandle.XYRectangleGraphicalHandle;
import ch.epfl.biop.scijava.command.bdv.userdefinedregion.PointsSelectorOverlay;
import ij.IJ;
import jitk.spline.ThinPlateR2LogRSplineKernelTransform;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.realtransform.*;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import net.imglib2.type.numeric.integer.ByteType;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.bdv.ManualRegistrationStarter;
import sc.fiji.bdvpg.bdv.ManualRegistrationStopper;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.bdv.supplier.BdvSupplierHelper;
import sc.fiji.bdvpg.bdv.supplier.IBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.biop.BiopSerializableBdvOptions;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
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
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static bdv.ui.BdvDefaultCards.*;
import static bdv.util.RealTransformHelper.BigWarpFileFromRealTransform;
import static ch.epfl.biop.scijava.command.bdv.userdefinedregion.RectangleSelectorBehaviour.box;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Wizard Align Slides (2D)",
        headless = false // User interface required
        )
public class Wizard2DWholeScanRegisterCommand implements BdvPlaygroundActionCommand{

    private static Logger logger = LoggerFactory.getLogger(Wizard2DWholeScanRegisterCommand.class);

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String message = "<html><h2>Automated WSI registration wizard</h2><br/>"+
            "Automated registrations requires elastix.<br/>"+
            "</html>";

    @Parameter
    CommandService cs;

    @Parameter(label = "Fixed reference source")
    SourceAndConverter<?> fixed;

    @Parameter(label = "Background offset value for moving image")
    double background_offset_value_moving = 0;

    @Parameter(label = "Background offset value for fixed image")
    double background_offset_value_fixed = 0;

    @Parameter(label = "Moving source used for registration to the reference")
    SourceAndConverter<?> moving;

    @Parameter(label = "Sources to transform, including the moving source if needed")
    SourceAndConverter<?>[] sourcesToTransform;

    //@Parameter
    BdvHandle bdvh;

    @Parameter(label = "Remove images z-offsets")
    boolean removeZOffset = true;

    @Parameter(label = "0 - Center moving image with fixed image")
    boolean centerMovingImage = true;

    @Parameter(label = "1 - Manual rigid registration")
    boolean manualRigidRegistration = true;

    @Parameter(label = "2 - Auto affine registration")
    boolean automatedAffineRegistration = true;

    @Parameter(label = "3 - Semi auto spline registration")
    boolean automatedSplineRegistration = true;

    @Parameter(label = "4 - Manual spline registration (BigWarp)")
    boolean manualSplineRegistration = true;

    @Parameter(label = "Pixel size for coarse registration in microns (default 10)", style = "format:0.00", persist = false)
    double coarsePixelSize_um = 10;
    double coarsePixelSize_mm;

    @Parameter(label = "Patch size for registration in microns (default 500)", style = "format:0.0", persist = false)
    double patchSize_um = 500;
    double patchSize_mm;

    @Parameter(label = "Pixel size for precise patch registration in microns (default 1)", style = "format:0.00", persist = false)
    double precisePixelSize_um = 1;
    double precisePixelSize_mm;

    @Parameter(label = "Number of iterations for each scale (default 100)")
    int maxIterationNumberPerScale = 100;

    @Parameter(label = "Show results of automated registrations (breaks parallelization)")
    boolean showDetails = false;

    @Parameter
    boolean verbose;

    @Parameter
    SourceAndConverterBdvDisplayService sacbds;

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter<?>[] transformedSources;

    @Parameter(type = ItemIO.OUTPUT)
    RealTransform transformation;

    // Rectangle properties
    List<RealPoint> corners;
    double topLeftX, topLeftY, bottomRightX, bottomRightY, cx, cy;

    List<RealPoint> landmarks = new ArrayList<>();

    private boolean manualRegistrationStopped = false;

    AffineTransform3D centeringTransform = new AffineTransform3D();
    AffineTransform3D manualTransform = new AffineTransform3D();

    public void setUserMessage(String message) {
        labelLogger.setText(message);
        BdvHandleHelper.setWindowTitle(bdvh, "Warpy Registration Wizard - "+message);
    }


    @Override
    public void run() {
        coarsePixelSize_mm = coarsePixelSize_um / 1000.00;
        patchSize_mm = patchSize_um / 1000.00;
        precisePixelSize_mm = precisePixelSize_um / 1000.00;

        bdvh = new WizardBdvSupplier().get(); //
        SourceAndConverterServices.getBdvDisplayService().registerBdvHandle(bdvh);

        if ((!manualRigidRegistration)&&(!automatedAffineRegistration)&&(!automatedSplineRegistration)&&(!manualSplineRegistration)) {
            IJ.error("You need to select at least one sort of registration!");
            return;
        }

        // These transforms are removed at the end in the wizard
        AffineTransform3D preTransformFixed = new AffineTransform3D();

        if (removeZOffset) {
            AffineTransform3D at3D = new AffineTransform3D();
            moving.getSpimSource().getSourceTransform(0,0,at3D);
            preTransformFixed.translate(0,0,-at3D.get(2,3)); // Removes z offset
            fixed.getSpimSource().getSourceTransform(0,0,at3D);
            centeringTransform.translate(0,0,-at3D.get(2,3)); // Removes z offset
        }

        if (centerMovingImage) {
            RealPoint centerMoving = SourceAndConverterHelper.getSourceAndConverterCenterPoint(moving);
            RealPoint centerFixed = SourceAndConverterHelper.getSourceAndConverterCenterPoint(fixed);
            centeringTransform.translate(
                    centerFixed.getDoublePosition(0)-centerMoving.getDoublePosition(0),
                    centerFixed.getDoublePosition(1)-centerMoving.getDoublePosition(1),
                    centerFixed.getDoublePosition(2)-centerMoving.getDoublePosition(2)
                    );
        }

        SourceAndConverter newFixed = SourceTransformHelper.createNewTransformedSourceAndConverter(preTransformFixed, new SourceAndConverterAndTimeRange(fixed,0));
        SourceAndConverter newMoving = SourceTransformHelper.createNewTransformedSourceAndConverter(centeringTransform, new SourceAndConverterAndTimeRange(moving,0));

        SourceAndConverterServices.getBdvDisplayService().remove(bdvh, new SourceAndConverter[]{fixed, moving});
        moving = newMoving;
        fixed = newFixed;
        SourceAndConverterServices.getBdvDisplayService().show(bdvh, new SourceAndConverter[]{fixed, moving});

        // Make sure the relevant sources are displayed
        showImages();
        iniView = bdvh.getViewerPanel().state().getViewerTransform();

        SwingUtilities.invokeLater(() -> addCardPanelCommons());

        if (manualRigidRegistration) {
            setUserMessage(" 0 - Manual Rigid Registration ");
            SwingUtilities.invokeLater(() -> addManualRigidRegistration());
            while (manualRegistrationStopped == false) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            SwingUtilities.invokeLater(() -> removeCardPanelRigidRegistration());
        }

        try {

            // Ask the user to select the region that should be aligned ( a rectangle ) - in any case
            if (automatedAffineRegistration) {
                setUserMessage(" 1 - Automated Affine Registration ");
                bdvh.getViewerPanel().state().setViewerTransform(iniView);
                getUserRectangle();
            } else {
                // Let's take the bounding box
                RealInterval box = getBoundingBox();
                corners = new ArrayList<>();
                corners.add(box.minAsRealPoint());
                corners.add(box.maxAsRealPoint());
            }

            topLeftX = Math.min(corners.get(0).getDoublePosition(0),corners.get(1).getDoublePosition(0) );
            topLeftY = Math.min(corners.get(0).getDoublePosition(1),corners.get(1).getDoublePosition(1) );
            bottomRightX = Math.max(corners.get(0).getDoublePosition(0),corners.get(1).getDoublePosition(0) );
            bottomRightY = Math.max(corners.get(0).getDoublePosition(1),corners.get(1).getDoublePosition(1) );

            cx = (topLeftX+bottomRightX)/2.0;
            cy = (topLeftY+bottomRightY)/2.0;

            if (automatedSplineRegistration) {

                setUserMessage(" 2 - Automated Spline Registration ");
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

            if (manualSplineRegistration) {

                IJ.log("BigWarp registration...");

                // The user wants big warp to correct landmark points
                List<SourceAndConverter<?>> movingSacs = Arrays.stream(new SourceAndConverter<?>[]{moving}).collect(Collectors.toList());

                List<SourceAndConverter<?>> fixedSacs = Arrays.stream(new SourceAndConverter<?>[]{fixed}).collect(Collectors.toList());

                List<ConverterSetup> converterSetups = Arrays.stream(new SourceAndConverter<?>[]{moving}).map(src -> SourceAndConverterServices.getSourceAndConverterService().getConverterSetup(src)).collect(Collectors.toList());

                converterSetups.addAll(Arrays.stream(new SourceAndConverter<?>[]{fixed}).map(src -> SourceAndConverterServices.getSourceAndConverterService().getConverterSetup(src)).collect(Collectors.toList()));

                // Launch BigWarp
                BigWarpLauncher bwl = new BigWarpLauncher(movingSacs, fixedSacs, "Big Warp", converterSetups);
                bwl.set2d();
                bwl.run();

                // Output bdvh handles -> will be put in the object service
                BdvHandle bdvhQ = bwl.getBdvHandleQ();
                BdvHandle bdvhP = bwl.getBdvHandleP();

                BdvHandleHelper.setWindowTitle(bdvhP, "Warpy Registration - 3 - BigWarp Registration");
                BdvHandleHelper.setWindowTitle(bdvhQ, "Warpy Registration - 3 - BigWarp Registration");

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
                waitForBigWarp(bwl.getBigWarp());
                transformation = bwl.getBigWarp().getBwTransform().getTransformation().copy();
                bwl.getBigWarp().closeAll();
            }

            // Adds the pretransformation!
            preTransformLandmarks();
            IJ.log("Registration DONE.");

            transformedSources = Arrays.stream(sourcesToTransform)
                    .map(source -> new SourceRealTransformer(transformation).apply(source))
                    .toArray(SourceAndConverter<?>[]::new);

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

    private void preTransformLandmarks() {
        // I know, it's complicated
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
            for (int d = 0; d<kernel.getNumDims();d++) {
                // num dims should be 2!
                moving.setPosition(pts_tgt[d][i], d);
                fixed.setPosition(pts_src[d][i], d);
            }
            manualTransform.inverse().apply(moving, moving);
            centeringTransform.inverse().apply(moving, moving);
            movingPts.add(moving);
            fixedPts.add(fixed);
        }
        transformation = new Wrapped2DTransformAs3D(
                new WrappedIterativeInvertibleRealTransform<>(BigWarpHelper.getTransform(movingPts, fixedPts, true))
        );
    }

    boolean isBigWarpFinished = false;

    private void waitForBigWarp(BigWarp bw) throws InterruptedException {
        SwingUtilities.invokeLater(() -> {
            bw.getViewerFrameP().addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                    isBigWarpFinished = true;
                }
            });
            bw.getViewerFrameQ().addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                    isBigWarpFinished = true;
                }
            });

            JButton confirmP = new JButton("Click to finish");
            confirmP.addActionListener((e) -> isBigWarpFinished = true);

            JPanel cardpanelP = box(false,
                    new JLabel("BigWarp registration"),
                    confirmP
            );

            bw.getViewerFrameP().getSplitPanel().setCollapsed(false);
            bw.getViewerFrameP().getCardPanel().setCardExpanded(DEFAULT_SOURCEGROUPS_CARD, false);
            bw.getViewerFrameP().getCardPanel().setCardExpanded(DEFAULT_VIEWERMODES_CARD, false);
            //bw.getViewerFrameP().getCardPanel().setCardExpanded(DEFAULT_SOURCES_CARD, true);
            SwingUtilities.invokeLater(() -> bw.getViewerFrameP().getCardPanel().addCard("BigWarp Registration", cardpanelP, true));

            JButton confirmQ = new JButton("Click to finish");
            confirmQ.addActionListener((e) -> isBigWarpFinished = true);

            JPanel cardpanelQ = box(false,
                    new JLabel("BigWarp registration"),
                    confirmQ
            );

            bw.getViewerFrameQ().getSplitPanel().setCollapsed(false);
            bw.getViewerFrameQ().getCardPanel().setCardExpanded(DEFAULT_SOURCEGROUPS_CARD, false);
            bw.getViewerFrameQ().getCardPanel().setCardExpanded(DEFAULT_VIEWERMODES_CARD, false);
            //bw.getViewerFrameQ().getCardPanel().setCardExpanded(DEFAULT_SOURCES_CARD, true);
            SwingUtilities.invokeLater(() -> bw.getViewerFrameQ().getCardPanel().addCard("BigWarp Registration", cardpanelQ, true));

        });

        while (!isBigWarpFinished) {
            Thread.sleep(100); // Wait for user.. dirty but working.
        }

    }

    CardHelper.CardState iniCardState;
    boolean rigidRegistrationStarted = false;
    ManualRegistrationStarter manualRegistrationStarter;
    ManualRegistrationStopper manualRegistrationStopper;

    AffineTransform3D iniView;

    final JLabel labelLogger = new JLabel();

    private void addCardPanelCommons() {
        iniView = new AffineTransform3D();

        bdvh.getViewerPanel().state().getViewerTransform(iniView);

        JButton restoreView = new JButton("Restore initial view");
        restoreView.addActionListener((e) -> bdvh.getViewerPanel().state().setViewerTransform(iniView));

                //getNavigationPad(bdvh, initialView)
        JButton toggleMoving = new JButton("Hide moving");
        toggleMoving.addActionListener((e) -> {
            if (bdvh.getViewerPanel().state().isSourceActive(moving)) {
                bdvh.getViewerPanel().state().setSourceActive(moving, false);
                toggleMoving.setText("Show moving");
            } else {
                bdvh.getViewerPanel().state().setSourceActive(moving, true);
                toggleMoving.setText("Hide moving");
            }
        });

        JButton toggleFixed = new JButton("Hide fixed");
        toggleFixed.addActionListener((e) -> {
            if (bdvh.getViewerPanel().state().isSourceActive(fixed)) {
                bdvh.getViewerPanel().state().setSourceActive(fixed, false);
                toggleFixed.setText("Show fixed");
            } else {
                bdvh.getViewerPanel().state().setSourceActive(fixed, true);
                toggleFixed.setText("Hide fixed");
            }
        });

        JButton autoScaleMoving = new JButton("Autoscale moving");
        autoScaleMoving.addActionListener((e) -> {
            bdvh.getViewerPanel().showMessage("Autoscaling moving image");
            new BrightnessAutoAdjuster<>(moving,0).run();
        });

        JButton autoScaleFixed = new JButton("Autoscale fixed");
        autoScaleFixed.addActionListener((e) -> {
            bdvh.getViewerPanel().showMessage("Autoscaling fixed image");
            new BrightnessAutoAdjuster<>(fixed,0).run();
        });

        JPanel panel = box(false,
                labelLogger,
                new JLabel("BDV Navigation"),
                new JLabel("- Left click drag > Rotate"),
                new JLabel("- Right click drag > Pan"),
                new JLabel("- UP/mouse wheel > Zoom"),
                new JLabel("- DOWN/mouse wheel > Unzoom"),
                new JLabel("- Shift+Z > Ortho"),
                restoreView,
                box(true, toggleMoving, toggleFixed),
                box(true, autoScaleFixed, autoScaleMoving));

        bdvh.getCardPanel().addCard("WSI Registration Wizard", panel, true); // No swing invoke later because it's already the case
    }

    private void addManualRigidRegistration() {

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
                restoreView.setText("Cancel manual registration");
            }
        });

        JButton confirmationButton = new JButton("Confirm transformation");
        confirmationButton.addActionListener((e) -> {
            if (rigidRegistrationStarted) {
                manualTransform.concatenate(manualRegistrationStarter.getCurrentTransform().copy());
                manualRegistrationStopper.run();
            }
            manualRegistrationStopped = true;
        });

        JPanel cardpanel = box(false,
                restoreView,
                confirmationButton);

        bdvh.getSplitPanel().setCollapsed(false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCEGROUPS_CARD, false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_VIEWERMODES_CARD, false);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCES_CARD, true);
        bdvh.getCardPanel().addCard("Manual rigid registration", cardpanel, true); // No swing utilities invoke later
    }

    private void removeCardPanelRigidRegistration() {
        CardHelper.restoreCardState(bdvh, iniCardState);
        bdvh.getCardPanel().removeCard("Manual rigid registration");
    }

    private void getUserLandmarks() throws Exception {

        JLabel addGridLabel = new JLabel("Add landmark grid");
        JLabel gridSpacingLabel = new JLabel("Spacing (um)");
        JTextField gridSpacing = new JTextField();
        gridSpacing.setEditable(true);
        gridSpacing.setText(Double.toString(patchSize_um));
        JButton addPoints = new JButton("Add landmark grid");
        addPoints.addActionListener((e) -> {
            try {
                System.out.println("IN action listener");
                double spacing_mm = Double.parseDouble(gridSpacing.getText())/1000.0;
                if (spacing_mm>(patchSize_mm / 10.0)) {

                    double xStart = cx;
                    while (xStart>topLeftX) xStart-=spacing_mm;

                    double yStart = cy;
                    while (yStart>topLeftY) yStart-=spacing_mm;

                    for (double xPos = xStart+spacing_mm;xPos<bottomRightX;xPos+=spacing_mm) {
                        for (double yPos = yStart+spacing_mm;yPos<bottomRightY;yPos+=spacing_mm) {
                            System.out.println("Add point "+xPos+":"+yPos);
                            ((PointsSelectorOverlay.AddGlobalPointBehaviour)(bdvh.getTriggerbindings()
                                    .getConcatenatedBehaviourMap()
                                    .get("add_point_global_hack")))
                                    .addGlobalPoint(xPos, yPos, 0.0);
                        }
                    }
                } else {
                    IJ.log("spacing too low");
                }

            } catch (Exception parseException) {
                IJ.log(parseException.getMessage());
            }
        });

        JPanel addGridCard = box(false, addGridLabel, box(true, gridSpacingLabel, gridSpacing), addPoints);
        SwingUtilities.invokeLater(() -> bdvh.getCardPanel().addCard("Place landmark grid", addGridCard, true));

        landmarks = (List<RealPoint>) cs
                .run(GetUserPointsCommand.class, true,
                        "messageForUser", "Select the position of the landmarks that will be used for the registration (at least 4).",
                        "timeOutInMs", -1,
                        "graphicalHandleSupplier",
                        (Function<RealPoint, GraphicalHandle>) realPoint ->
                                new XYRectangleGraphicalHandle(
                                        new Behaviours(new InputTriggerConfig()),
                                        bdvh.getTriggerbindings(),
                                        UUID.randomUUID().toString(),
                                        bdvh.getViewerPanel().state(),
                                        () -> realPoint,
                                        () -> patchSize_mm, // To improve, clearly!
                                        () -> patchSize_mm, // To improve, clearly!
                                        () -> PointsSelectorBehaviour.defaultLandmarkColor )
                        )
                .get().getOutput("pts");


        bdvh.getCardPanel().removeCard("Place landmark grid");
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
    }

    RealInterval getBoundingBox() {
        SourceAndConverter[] sources = new SourceAndConverter[]{moving, fixed};
        List<RealInterval> intervalList = Arrays.asList(sources).stream().map((sourceAndConverter) -> {
            Interval interval = sourceAndConverter.getSpimSource().getSource(0, 0);
            AffineTransform3D sourceTransform = new AffineTransform3D();
            sourceAndConverter.getSpimSource().getSourceTransform(0, 0, sourceTransform);
            RealPoint corner0 = new RealPoint(new float[]{(float)interval.min(0), (float)interval.min(1), (float)interval.min(2)});
            RealPoint corner1 = new RealPoint(new float[]{(float)interval.max(0), (float)interval.max(1), (float)interval.max(2)});
            sourceTransform.apply(corner0, corner0);
            sourceTransform.apply(corner1, corner1);
            return new FinalRealInterval(new double[]{Math.min(corner0.getDoublePosition(0), corner1.getDoublePosition(0)), Math.min(corner0.getDoublePosition(1), corner1.getDoublePosition(1)), Math.min(corner0.getDoublePosition(2), corner1.getDoublePosition(2))}, new double[]{Math.max(corner0.getDoublePosition(0), corner1.getDoublePosition(0)), Math.max(corner0.getDoublePosition(1), corner1.getDoublePosition(1)), Math.max(corner0.getDoublePosition(2), corner1.getDoublePosition(2))});
        }).collect(Collectors.toList());
        RealInterval maxInterval = intervalList.stream().reduce((i1, i2) -> {
            return new FinalRealInterval(new double[]{Math.min(i1.realMin(0), i2.realMin(0)), Math.min(i1.realMin(1), i2.realMin(1)), Math.min(i1.realMin(2), i2.realMin(2))}, new double[]{Math.max(i1.realMax(0), i2.realMax(0)), Math.max(i1.realMax(1), i2.realMax(1)), Math.max(i1.realMax(2), i2.realMax(2))});
        }).get();
        return maxInterval;
    }

    private void showImages() {
        sacbds.show(bdvh,fixed, moving);
        new ViewerTransformAdjuster(bdvh, new SourceAndConverter[]{fixed, moving}).run();
    }

    void fitBdvOnUserROI(BdvHandle bdvh) {
        AffineTransform3D newCenter = BdvHandleHelper.getViewerTransformWithNewCenter(bdvh, new double[]{cx,cy,0});
        bdvh.getViewerPanel().state().setViewerTransform(newCenter);
    }

    public static class WizardBdvSupplier implements IBdvSupplier {

        @Override
        public BdvHandle get() {
            BiopSerializableBdvOptions sOptions = new BiopSerializableBdvOptions();
            sOptions.is2D = true;
            sOptions.width = 1200;
            sOptions.height = 800;
            sOptions.interpolate = false;
            sOptions.frameTitle = "Warpy Registration Wizard";
            sOptions.numTimePoints = 1;

            BdvOptions options = sOptions.getBdvOptions();
            ArrayImg<ByteType, ByteArray> dummyImg = ArrayImgs.bytes(new long[]{2L, 2L, 2L});
            options = options.sourceTransform(new AffineTransform3D());
            BdvStackSource<ByteType> bss = BdvFunctions.show(dummyImg, "dummy", options);
            BdvHandle bdvh = bss.getBdvHandle();
            if (sOptions.interpolate) {
                bdvh.getViewerPanel().setInterpolation(Interpolation.NLINEAR);
            }

            bdvh.getViewerPanel().state().removeSource(bdvh.getViewerPanel().state().getCurrentSource());
            bdvh.getViewerPanel().setNumTimepoints(sOptions.numTimePoints);
            BdvSupplierHelper.addSourcesDragAndDrop(bdvh);
            bdvh.getSplitPanel().setCollapsed(false);
            bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCES_CARD, true);
            bdvh.getCardPanel().removeCard(DEFAULT_SOURCEGROUPS_CARD);
            bdvh.getCardPanel().removeCard(DEFAULT_VIEWERMODES_CARD);
            return bdvh;
        }
    }

}
