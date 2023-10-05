package ch.epfl.biop.scijava.command.spimdata;

import bdv.util.BdvHandle;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import ch.epfl.biop.bdv.img.OpenersToSpimData;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsHelper;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ch.epfl.biop.bdv.img.legacy.bioformats.command.OpenFilesWithBigdataviewerBioformatsBridgeCommand;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import ch.epfl.biop.operetta.OperettaManager;
import ij.IJ;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import ome.xml.model.Well;
import org.apache.commons.io.FileUtils;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.scijava.services.ui.SourceAndConverterServiceUI;
import sc.fiji.bdvpg.scijava.services.ui.SourceFilterNode;
import sc.fiji.bdvpg.scijava.services.ui.SpimDataFilterNode;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAdjuster;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Warning : a qupath project may have its source reordered and or removed :
 * - not all entries will be present in the qupath project
 * Limitations : only images
 */

@Plugin(type = Command.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"BDVDataset>Create BDV Dataset [Operetta]"
)
public class OpenOperettaDatasetCommand implements Command {

    // Useful to display the label of the folder parameter
    @Parameter(
            required = false,
            label = "Physical unit",
            choices = {"MILLIMETER", "MICROMETER", "NANOMETER"}
    )
    public String unit = "MILLIMETER";
    String message = "BIOP Operetta BigDataViewer";

    @Parameter(label = "Select the 'Images' folder of your Operetta dataset", style = "directory")
    File folder;

    @Parameter
    Context ctx;

    @Parameter
    SourceAndConverterService sourceService;

    @Parameter
    SourceAndConverterBdvDisplayService sourceDisplayService;

    @Parameter
    double min_display_value = 0;

    @Parameter
    double max_display_value = 20000;

    @Parameter
    boolean show = true;

    @Parameter(type = ItemIO.OUTPUT)
    String dataset_name;

    @Override
    public void run() {
        // A few checks and warning for big files
        XMLFILE file = null;
        for (XMLFILE version : XMLFILE.values()) {
            File candidate = new File(folder, version.getIndexFileName());
            if (candidate.exists()) {
                file = version;
                break;
            }
        }
        // A few checks and warning for big files
        if (file == null) {
            IJ.log("Error, no matching Index files found in " + folder.getAbsolutePath());
            IJ.log("Implemented valid Index files:");
            for (XMLFILE version : XMLFILE.values()) {
                IJ.log("\t" + version.getIndexFileName() + " (" + version.getDescription() + ")");
            }
            return;
        }

        File f = new File(folder, file.getIndexFileName());

        int sizeInMb = (int) ((double) FileUtils.sizeOf(f)/(double)(1024*1024));
        IJ.log("- Opening Operetta dataset "+f.getAbsolutePath()+" (" + sizeInMb + " Mb)");

        File fmemo = new File(folder, ".Index.idx.xml.bfmemo");
        int estimatedOpeningTimeInMin;
        if (!fmemo.exists()) {
            estimatedOpeningTimeInMin = sizeInMb / 30; // 30 Mb per minute
            IJ.log("- No memo file, the first opening will take longer.");
        } else {
            estimatedOpeningTimeInMin = sizeInMb / 600; // 60 Mb per minute
            IJ.log("- Memo file detected.");
        }

        if (estimatedOpeningTimeInMin==0) {
            IJ.log("- Estimated opening time below 1 minute.");
        } else {
            IJ.log("- Estimated opening time = " + estimatedOpeningTimeInMin + " min.");
        }

        final IFormatReader[] reader = new IFormatReader[1];
        Thread t = new Thread(() -> {
            try {
                reader[0] = OperettaManager.createReader(f.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            } catch (FormatException e) {
                e.printStackTrace();
            }
        });

        t.start();
        int countSeconds = 0;
        while (t.isAlive()) {
            try {
                Thread.sleep(1000);
                countSeconds++;
            } catch (InterruptedException e) {
                IJ.log("Operetta dataset opening interrupted!");
                return;
            }
            if ((countSeconds % 20)==0) {
                IJ.log("- t = " + countSeconds + " s");
            }
        }

        if (reader[0]==null) {
            IJ.log("Error during reader creation, please retry or post your issue in forum.image.sc.");
            return;
        }

        OperettaManager.Builder opmBuilder =  new OperettaManager.Builder()
                .reader(reader[0]);

        OperettaManager opm = opmBuilder.build();

        IJ.log("Dataset "+opm.getPlateName()+" : "+opm.getRange());

        int stack_width = reader[0].getSizeX();
        int stack_height = reader[0].getSizeY();

        opm.getAvailableWellsString().forEach(System.out::println);
        opm.getAvailableWells().forEach(System.out::println);
        opm.getAvailableFieldIds().forEach(System.out::println);
        opm.getAvailableFieldsString().forEach(System.out::println);


        try {
            // Block size : reading one full plane at a time

            List<OpenerSettings> openerSettings = new ArrayList<>();

            int nSeries = BioFormatsHelper.getNSeries(f);
            for (int i = 0; i < nSeries; i++) {
                openerSettings.add(
                        OpenerSettings.BioFormats()
                                .location(f)
                                .setSerie(i)
                                .unit(unit)
                                .splitRGBChannels(false)
                                .cornerPositionConvention()
                                .cornerPositionConvention()
                                .cacheBlockSize(stack_width, stack_height, 1)
                                .context(ctx));
            }

            AbstractSpimData asd = OpenersToSpimData.getSpimData(openerSettings);
            sourceService.register(asd);

            IJ.log("Done! Dataset opened.");

            dataset_name = opm.getPlateName();
            if (dataset_name==null) {
                IJ.log("Error, could not find plate name!");
                dataset_name = folder.getParentFile().getName();
            }
            sourceService.setSpimDataName(asd, dataset_name);

            Map<Well, SourceFilterNode> wellFilters = new LinkedHashMap<>();
            Map<Integer, SourceFilterNode> fieldsFilters = new LinkedHashMap<>();

            DefaultTreeModel model = sourceService.getUI().getTreeModel();

            opm.getAvailableWells().forEach(w -> {
                int row = w.getRow().getValue() + 1;
                int col = w.getColumn().getValue() + 1;
                String name = getWellName(row, col);// "R" + row + "-C" + col;
                //int idx = opm.getAvailableWells().indexOf(w)+1;
                SourceFilterNode sfn = new SourceFilterNode(model,name,
                        (source) -> source.getSpimSource().getName().startsWith("Well "+name+","),false);
                wellFilters.put(w, sfn);
            });

            opm.getAvailableFieldIds().forEach(id -> {
                fieldsFilters.put(id,
                        new SourceFilterNode(model,"Field "+id,
                        (source) -> source.getSpimSource().getName().contains(" Field "+id+"-"),false)
               );
            });

            TreePath tp = sourceService.getUI().getTreePathFromString(opm.getPlateName());
            SpimDataFilterNode datasetNode = (SpimDataFilterNode) tp.getLastPathComponent();

            SourceFilterNode wellsNode = new SourceFilterNode(model, "Wells", (source)-> true, false);
            SourceFilterNode fieldsNode = new SourceFilterNode(model, "Fields", (source)-> true, false);
            sourceService.getUI().addNode(datasetNode,wellsNode);
            sourceService.getUI().addNode(datasetNode,fieldsNode);

            tp = sourceService.getUI().getTreePathFromString(opm.getPlateName()+">SeriesIndex");
            sourceService.getUI().removeNode((SourceFilterNode) tp.getLastPathComponent());

            tp = sourceService.getUI().getTreePathFromString(opm.getPlateName()+">Illumination");
            sourceService.getUI().removeNode((SourceFilterNode) tp.getLastPathComponent());

            tp = sourceService.getUI().getTreePathFromString(opm.getPlateName()+">FileName");
            sourceService.getUI().removeNode((SourceFilterNode) tp.getLastPathComponent());

            tp = sourceService.getUI().getTreePathFromString(opm.getPlateName()+">Displaysettings");
            sourceService.getUI().removeNode((SourceFilterNode) tp.getLastPathComponent());

            tp = sourceService.getUI().getTreePathFromString(opm.getPlateName()+">Angle");
            sourceService.getUI().removeNode((SourceFilterNode) tp.getLastPathComponent());

            // Don't forget to clone the nodes...
            wellFilters.values().forEach(wf -> {
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        SourceFilterNode node = (SourceFilterNode) wf.clone();
                        sourceService.getUI().addNode(wellsNode,node);
                        fieldsFilters.values().forEach(ff -> {
                            SourceFilterNode ffc =(SourceFilterNode) ff.clone();
                            sourceService.getUI().addNode(node,ffc);
                            sourceService.getUI().addNode(ffc, new SourceFilterNode(model, "Sources",(source) -> true, true));
                        });
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            });

            // Don't forget to clone the nodes...
            fieldsFilters.values().forEach(ff -> {
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        SourceFilterNode node = (SourceFilterNode) ff.clone();
                        sourceService.getUI().addNode(fieldsNode,node);
                        wellFilters.values().forEach(wf -> {
                            SourceFilterNode wfc =(SourceFilterNode)wf.clone();
                            sourceService.getUI().addNode(node,wfc);
                            sourceService.getUI().addNode(wfc, new SourceFilterNode(model, "Sources",(source) -> true, true));
                        });
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            });

            IJ.log("Setting location in space...");

            // Get the occupation of a well, including all fields
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            double maxZ = -Double.MAX_VALUE;


            AffineTransform3D at3d = new AffineTransform3D();
            RealPoint topLeft = new RealPoint(3);
            RealPoint bottomRight = new RealPoint(3);

            int nSlices = opm.getRange().getRangeZ().size();

            Well w0 = opm.getAvailableWells().get(0);
            int row0 = w0.getRow().getValue() + 1;
            int col0 = w0.getColumn().getValue() + 1;
            String wellName0 =  getWellName(row0, col0); // "R" + row0 + "-C" + col0;
            TreePath pathFirstWell = sourceService.getUI().getTreePathFromString(opm.getPlateName()+">Wells>"+wellName0);

            for (SourceAndConverter source : sourceService.getUI().getSourceAndConvertersFromTreePath(pathFirstWell)) {
                source.getSpimSource().getSourceTransform(0,0,at3d);
                topLeft.setPosition(new double[]{0,0,0});
                at3d.apply(topLeft,topLeft);
                if (topLeft.getDoublePosition(0)<minX) {
                    minX = topLeft.getDoublePosition(0);
                }
                if (topLeft.getDoublePosition(1)<minY) {
                    minY = topLeft.getDoublePosition(1);
                }
                if (topLeft.getDoublePosition(2)<minZ) {
                    minZ = topLeft.getDoublePosition(2);
                }
                bottomRight.setPosition(new double[]{stack_width*2,stack_height*2,nSlices}); // *2 to put some space between wells
                at3d.apply(bottomRight,bottomRight);
                if (bottomRight.getDoublePosition(0)>maxX) {
                    maxX = bottomRight.getDoublePosition(0);
                }
                if (bottomRight.getDoublePosition(1)>maxY) {
                    maxY = bottomRight.getDoublePosition(1);
                }
                if (bottomRight.getDoublePosition(2)>maxZ) {
                    maxZ = bottomRight.getDoublePosition(2);
                }

            }

            double originX = minX;
            double originY = minY;
            double originZ = minZ;

            double sizeX = maxX-minX;
            double sizeY = maxY-minY;
            double sizeZ = maxZ-minZ;

            double startX = Double.MAX_VALUE;
            double startY = Double.MAX_VALUE;
            // Moving in space
            for (Well w : opm.getAvailableWells()) {
                int row = w.getRow().getValue() + 1;
                int col = w.getColumn().getValue() + 1;
                String wellName =  getWellName(row, col);//"R" + row + "-C" + col;

                TreePath p = sourceService.getUI().getTreePathFromString(opm.getPlateName()+">Wells>"+wellName);

                List<SourceAndConverter<?>> sources = sourceService.getUI().getSourceAndConvertersFromTreePath(p);
                sources.forEach(source -> new BrightnessAdjuster(source, min_display_value, max_display_value).run());
                List<SourceAndConverterAndTimeRange> sourceAndTime = sources.stream().map(source ->
                    new SourceAndConverterAndTimeRange(source,0,opm.getRange().getRangeT().size())
                ).collect(Collectors.toList());
                AffineTransform3D transform = new AffineTransform3D();
                if ((col*sizeX-originX)<startX) {
                    startX = col*sizeX-originX;
                }
                if (row*sizeY-originY<startY) {
                    startY = row*sizeY-originY;
                }
                //System.out.println("col = "+col+" row = "+row);
                //System.out.println("startX = "+startX+" startY = "+startY);
                transform.translate(col*sizeX-originX,row*sizeY-originY,0.5*sizeZ-originZ);
                sourceAndTime.forEach(sat -> SourceTransformHelper.appendNewSpimdataTransformation(transform, sat));
            }

            IJ.log("Done.");

            if (show) {
                BdvHandle bdvh = sourceDisplayService.getNewBdv();
                SourceAndConverter[] sources = sourceService.getSourceAndConverterFromSpimdata(asd).toArray(new SourceAndConverter[0]);
                sourceDisplayService.show(bdvh, sources);

                new ViewerTransformAdjuster(bdvh, sources).run();
                bdvh.getViewerPanel().state().getGroups();
                List<SourceGroup> groups = bdvh.getViewerPanel().state().getGroups();

                int maxChannels = Math.min(10, opm.getRange().getRangeC().size());

                for (int iCh = 0; iCh<maxChannels; iCh++) {
                    SourceGroup group = groups.get(iCh);
                    SourceAndConverterServiceUI.Node chNode = sourceService.getUI().getRoot().child(opm.getPlateName()).child("Channel").child(iCh);
                    List<SourceAndConverter<?>> sourcesInChannel =
                            Arrays.asList(chNode.sources());
                    List<SourceAndConverter<?>> sourcesCast = sourcesInChannel.stream().map(sac -> (SourceAndConverter<?>) sac).collect(Collectors.toList());
                    bdvh.getViewerPanel().state().addSourcesToGroup(sourcesCast, group);
                    bdvh.getViewerPanel().state().setGroupName(group, chNode.name());
                }

                bdvh.getViewerPanel().setNumTimepoints(opm.getRange().getRangeT().size());

            }

            reader[0].close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //String input = "abc".toLowerCase();
    final static String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    final static DecimalFormat df = new DecimalFormat("00");

    public static String getWellName(int row, int col) {
        //System.out.println("R"+row+" - C"+col);
        //System.out.println("rendered to "+alphabet.substring(row-1, row)+df.format(col));
        return alphabet.substring(row-1, row)+df.format(col);
    }

    private enum XMLFILE {
        V5("Index.idx.xml", "PerkinElmer Harmony V5"),
        V5FLEX("Index.flex.xml", "PerkinElmer Harmony V5 Flatfield data"),
        V6("Index.xml", "PerkinElmer Harmony V6");

        private final String description;
        private final String indexFileName;

        XMLFILE(String indexFileName, String description) {
            this.indexFileName = indexFileName;
            this.description = description;
        }

        private String getIndexFileName() {
            return this.indexFileName;
        }

        public String getDescription() {
            return this.description;
        }

    }
}
