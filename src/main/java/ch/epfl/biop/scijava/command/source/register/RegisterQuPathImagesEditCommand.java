package ch.epfl.biop.scijava.command.source.register;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.util.QuPathBdvHelper;
import bdv.viewer.SourceAndConverter;
import bigwarp.BigWarp;
import ch.epfl.biop.spimdata.qupath.QuPathEntryEntity;
import com.google.gson.stream.JsonReader;
import ij.IJ;
import net.imglib2.realtransform.*;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import org.apache.commons.io.FileUtils;
import org.scijava.Context;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.register.BigWarpLauncher;
import sc.fiji.persist.ScijavaGsonHelper;

import javax.swing.*;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static bdv.ui.BdvDefaultCards.DEFAULT_SOURCEGROUPS_CARD;
import static bdv.ui.BdvDefaultCards.DEFAULT_VIEWERMODES_CARD;
import static bdv.util.RealTransformHelper.BigWarpFileFromRealTransform;
import static ch.epfl.biop.scijava.command.bdv.userdefinedregion.RectangleSelectorBehaviour.box;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>QuPath - Edit Warpy Registration")
public class RegisterQuPathImagesEditCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false, style = "message")
    String message = "<html><h1>QuPath registration wizard</h1>Please select a moving and a fixed source<br></html>";

    @Parameter(label = "Fixed source", callback = "updateMessage")
    SourceAndConverter fixed_source;

    @Parameter(label = "Moving source", callback = "updateMessage")
    SourceAndConverter moving_source;

    @Parameter
    Context scijavaCtx;

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

                FileUtils.writeStringToFile(result, jsonMovingToFixed, Charset.defaultCharset());

                IJ.log("Transformation file successfully written to QuPath project: "+result);
            }

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

        // Launch BigWarp
        List<SourceAndConverter> movingSacs = Arrays.stream(new SourceAndConverter[]{moving_source}).collect(Collectors.toList());

        List<SourceAndConverter> fixedSacs = Arrays.stream(new SourceAndConverter[]{fixed_source}).collect(Collectors.toList());

        List<ConverterSetup> converterSetups = Arrays.stream(new SourceAndConverter[]{moving_source}).map(src -> SourceAndConverterServices.getBdvDisplayService().getConverterSetup(src)).collect(Collectors.toList());

        converterSetups.addAll(Arrays.stream(new SourceAndConverter[]{fixed_source}).map(src -> SourceAndConverterServices.getBdvDisplayService().getConverterSetup(src)).collect(Collectors.toList()));


        BigWarpLauncher bwl = new BigWarpLauncher(movingSacs, fixedSacs, "Edit QuPath Registration", converterSetups);
        bwl.set2d();
        bwl.run();

        // Output bdvh handles -> will be put in the object service
        BdvHandle bdvhQ = bwl.getBdvHandleQ();
        BdvHandle bdvhP = bwl.getBdvHandleP();

        SourceAndConverterServices.getBdvDisplayService().pairClosing(bdvhQ,bdvhP);

        bdvhP.getViewerPanel().requestRepaint();
        bdvhQ.getViewerPanel().requestRepaint();

        bwl.getBigWarp().getLandmarkFrame().repaint();

        bwl.getBigWarp().loadLandmarks(BigWarpFileFromRealTransform(tst));

        bwl.getBigWarp().setIsMovingDisplayTransformed(true);

        AffineTransform3D at3D = new AffineTransform3D();
        bdvhP.getViewerPanel().state().getViewerTransform(at3D);

        waitForBigWarp(bwl.getBigWarp());
        bwl.getBigWarp().closeAll();

        return bwl.getBigWarp().getBwTransform().getTransformation().copy();
    }

    private boolean isBigWarpFinished = false;

    private void waitForBigWarp(BigWarp bw) throws InterruptedException {

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
        bw.getViewerFrameP().getCardPanel().addCard("BigWarp Registration", cardpanelP, true);

        JButton confirmQ = new JButton("Click to finish");
        confirmQ.addActionListener((e) -> {
            isBigWarpFinished = true;
        });

        JPanel cardpanelQ = box(false,
                new JLabel("BigWarp registration"),
                confirmQ
        );

        bw.getViewerFrameQ().getSplitPanel().setCollapsed(false);
        bw.getViewerFrameQ().getCardPanel().setCardExpanded(DEFAULT_SOURCEGROUPS_CARD, false);
        bw.getViewerFrameQ().getCardPanel().setCardExpanded(DEFAULT_VIEWERMODES_CARD, false);
        //bw.getViewerFrameQ().getCardPanel().setCardExpanded(DEFAULT_SOURCES_CARD, true);
        bw.getViewerFrameQ().getCardPanel().addCard("BigWarp Registration", cardpanelQ, true);

        while (!isBigWarpFinished) {
            Thread.sleep(100); // Wait for user.. dirty but ok.
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
                                    if (!result.exists()) {
                                        message+="WARNING! REGISTRATION FILE NOT FOUND!<br>";
                                    }

                                    movingToFixedLandmarkName = "transform_"+fixed_series_index+"_"+moving_series_index+".json";

                                    result = new File(fixed_entry_folder.getAbsolutePath(), movingToFixedLandmarkName);
                                    if (result.exists()) {
                                        message+="WARNING! AN <b>INVERSE</b> REGISTRATION FILE ALREADY EXISTS! <br>";
                                        message+="Switch your fixed and moving selected source to edit it!<br>";
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
