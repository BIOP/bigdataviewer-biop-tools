package ch.epfl.biop.bdv.bioformats;

import java.io.File;

import bdv.util.BdvHandle;
import loci.common.DebugTools;
import loci.formats.ImageReader;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import net.imagej.ImageJ;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

// Have a look at:
//
// https://github.com/qupath/qupath-bioformats-extension/blob/master/src/main/java/qupath/lib/images/servers/BioFormatsServerOptions.java
// https://github.com/qupath/qupath-bioformats-extension/blob/master/src/main/java/qupath/lib/images/servers/BioFormatsImageServer.java
// https://github.com/scifio/scifio/blob/master/src/main/java/io/scif/img/ImgOpener.java
// https://github.com/scifio/scifio-hdf5/tree/master/src/main/java/io/scif/formats/imaris
// https://github.com/openmicroscopy/bioformats/issues/3343
// https://github.com/openmicroscopy/bioformats/blob/96bd37e3f6d8a9fb3a74231f8775e08497fea886/components/formats-gpl/src/loci/formats/in/CellSensReader.java
// https://github.com/openmicroscopy/bioformats/blob/96bd37e3f6d8a9fb3a74231f8775e08497fea886/components/formats-gpl/src/loci/formats/in/CellSensReader.java
// https://github.com/ome/bio-formats-imagej
// https://github.com/ome/bio-formats-imagej/blob/master/src/main/java/loci/plugins/in/ImportProcess.java
// TODO : add lookuptable

@Plugin(type = Command.class,menuPath = "Plugins>BigDataViewer>SciJava>Open VSI (experimental) (SciJava)")
public class BioFormatsOpenPlugInSciJava implements Command
{

    private static final Logger LOGGER = Logger.getLogger( BioFormatsOpenPlugInSciJava.class.getName() );

    @Parameter(label = "VSI Image File")
    public File inputFile;

    @Parameter(label = "Open in new BigDataViewer window")
    public boolean createNewWindow;

    // ItemIO.BOTH required because it can be modified in case of appending new data to BDV (-> requires INPUT), or created (-> requires OUTPUT)
    @Parameter(label = "BigDataViewer Frame", type = ItemIO.BOTH, required = false)
    public BdvHandle bdv_h;

    @Parameter(label="Full New")
    public String sourceIndexStringNewFull = "0";

    @Parameter(label="Display type ()", choices = {"Volatile","Standard", "Volatile + Standard"})
    public String appendMode = "Volatile";

    @Parameter
    public boolean autoscale = true;

    @Parameter
    public boolean switchZandC = false;

    @Parameter
    CommandService cs;

    @Override
    public void run()
    {
        DebugTools.enableIJLogging(false);
        DebugTools.enableLogging("INFO");
        try {

            final ImageReader reader = new ImageReader();
            reader.setFlattenedResolutions(false);
            final IMetadata omeMetaOmeXml = MetadataTools.createOMEXMLMetadata();
            reader.setMetadataStore(omeMetaOmeXml);
            reader.setId(inputFile.getAbsolutePath());

            LOGGER.info("reader.getSeriesCount()="+reader.getSeriesCount());

            ArrayList<Pair<Integer, ArrayList<Integer>>>
                listOfStuff =
                    commaSeparatedListToArrayOfArray(
                        sourceIndexStringNewFull,
                        idxSeries ->(idxSeries>=0)?idxSeries:reader.getSeriesCount()+idxSeries-1, // apparently -1 is necessary
                        (idxSeries, idxChannel) ->
                                (idxChannel>=0)?idxChannel:omeMetaOmeXml.getChannelCount(idxSeries)+idxChannel
                    );

            listOfStuff.stream().forEach(p -> {
                p.getRight().stream().forEach(idCh -> {
                    try {

                        LOGGER.info("omeMetaOmeXml.getChannelCount("+p.getLeft()+")="+omeMetaOmeXml.getChannelCount(p.getLeft()));
                        CommandModule cm;
                        if (!appendMode.equals("Volatile + Standard")) {
                            Future<CommandModule> module = cs.run(BioFormatsOpenPlugInSingleSourceSciJava.class, false,
                                    "sourceIndex", p.getLeft(),
                                    "channelIndex", idCh,
                                    "bdv_h", bdv_h,
                                    "createNewWindow", createNewWindow,
                                    "inputFile", inputFile,
                                    "switchZandC", switchZandC,
                                    "autoscale", autoscale,
                                    "appendMode", appendMode);
                            cm = module.get();
                        } else {
                            Future<CommandModule> module = cs.run(BioFormatsOpenPlugInSingleSourceSciJava.class, false,
                                    "sourceIndex", p.getLeft(),
                                    "channelIndex", idCh,
                                    "bdv_h", bdv_h,
                                    "createNewWindow", createNewWindow,
                                    "inputFile", inputFile,
                                    "switchZandC", switchZandC,
                                    "autoscale", autoscale,
                                    "appendMode", "Volatile");
                            module.get();
                            module = cs.run(BioFormatsOpenPlugInSingleSourceSciJava.class, false,
                                    "sourceIndex", p.getLeft(),
                                    "channelIndex", idCh,
                                    "bdv_h", bdv_h,
                                    "createNewWindow", createNewWindow,
                                    "inputFile", inputFile,
                                    "switchZandC", switchZandC,
                                    "autoscale", autoscale,
                                    "appendMode", "Standard");
                            cm = module.get();

                        }
                        bdv_h = (BdvHandle) cm.getOutput("bdv_h");

                        createNewWindow = false;

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * BiFunction necessary to be able to find index of negative values
     */
    static public ArrayList<Pair<Integer, ArrayList<Integer>>> commaSeparatedListToArrayOfArray(String expression, Function<Integer, Integer> fbounds, BiFunction<Integer, Integer, Integer> f) {
        String[] splitIndexes = expression.split(";");

        ArrayList<Pair<Integer, ArrayList<Integer>>> arrayOfArrayOfIndexes = new ArrayList<>();

        for (String str : splitIndexes) {
            str.trim();
            String seriesIdentifier = str;
            String channelIdentifier = "*";
            if (str.contains(".")) {
                String[] boundIndex = str.split("\\.");
                if (boundIndex.length==2) {
                    seriesIdentifier = boundIndex[0];
                    channelIdentifier = boundIndex[1];
                } else {
                    LOGGER.warning("Number format problem with expression:"+str+" - Expression ignored");
                    break;
                }
            }
            // TODO Need to split by comma
                // No sub array specifier -> equivalent to * in subchannel
                try {
                    if (seriesIdentifier.trim().equals("*")) {
                        int maxIndex = fbounds.apply(-1);
                        System.out.println("maxIndex="+maxIndex);
                        for (int index = 0; index <=maxIndex; index++) {
                            MutablePair<Integer, ArrayList<Integer>> current = new MutablePair<>();
                            final int idxCp = index;
                            current.setLeft(idxCp);
                            current.setRight(
                                    expressionToArray(channelIdentifier, i -> f.apply(idxCp,i))
                            );
                            arrayOfArrayOfIndexes.add(current);
                        }
                    } else {
                        int indexMin, indexMax;

                        System.out.println("ON est la");
                        if (seriesIdentifier.trim().contains(":")) {
                            String[] boundIndex = seriesIdentifier.split(":");
                            assert boundIndex.length==2;
                            indexMin = fbounds.apply(Integer.valueOf(boundIndex[0].trim()));
                            indexMax = fbounds.apply(Integer.valueOf(boundIndex[1].trim()));
                        } else {
                            indexMin = fbounds.apply(Integer.valueOf(seriesIdentifier.trim()));
                            indexMax = indexMin;
                        }
                        if (indexMax>=indexMin) {
                            for (int index=indexMin;index<=indexMax;index++) {
                                MutablePair<Integer, ArrayList<Integer>> current = new MutablePair<>();
                                final int idxCp = index;
                                current.setLeft(index);
                                current.setRight(
                                        expressionToArray(channelIdentifier, i -> f.apply(idxCp,i))
                                );
                                arrayOfArrayOfIndexes.add(current);
                            }
                        } else {
                            for (int index=indexMax;index>=indexMin;index--) {
                                MutablePair<Integer, ArrayList<Integer>> current = new MutablePair<>();
                                final int idxCp = index;
                                current.setLeft(index);
                                current.setRight(
                                        expressionToArray(channelIdentifier, i -> f.apply(idxCp,i))
                                );
                                arrayOfArrayOfIndexes.add(current);
                            }
                        }

                    }
                } catch (NumberFormatException e) {
                    LOGGER.warning("Number format problem with expression:"+str+" - Expression ignored");
                }

        }
        return arrayOfArrayOfIndexes;
    }

    /**
     * Convert a comma separated list of indexes into an arraylist of integer
     *
     * For instance 1,2,5:7,10:12,14 returns an ArrayList containing
     * [1,2,5,6,7,10,11,12,14]
     *
     * Invalid format are ignored and an error message is displayed
     *
     * @param expression
     * @return list of indexes in ArrayList
     */

    static public ArrayList<Integer> expressionToArray(String expression, Function<Integer, Integer> fbounds) {
        String[] splitIndexes = expression.split(",");
        ArrayList<java.lang.Integer> arrayOfIndexes = new ArrayList<>();
        for (String str : splitIndexes) {
            str.trim();
            if (str.contains(":")) {
                // Array of source, like 2:5 = 2,3,4,5
                String[] boundIndex = str.split(":");
                if (boundIndex.length==2) {
                    try {
                        int b1 = fbounds.apply(Integer.valueOf(boundIndex[0].trim()));
                        int b2 = fbounds.apply(Integer.valueOf(boundIndex[1].trim()));
                        if (b1<b2) {
                            for (int index = b1; index <= b2; index++) {
                                arrayOfIndexes.add(index);
                            }
                        }  else {
                            for (int index = b2; index >= b1; index--) {
                                arrayOfIndexes.add(index);
                            }
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Number format problem with expression:"+str+" - Expression ignored");
                    }
                } else {
                    LOGGER.warning("Cannot parse expression "+str+" to pattern 'begin-end' (2-5) for instance, omitted");
                }
            } else {
                // Single source
                try {
                    if (str.trim().equals("*")) {
                        int maxIndex = fbounds.apply(-1);
                        for (int index = 0; index <=maxIndex; index++) {
                            arrayOfIndexes.add(index);
                        }
                    } else {
                        int index = fbounds.apply(Integer.valueOf(str.trim()));
                        arrayOfIndexes.add(index);
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warning("Number format problem with expression:"+str+" - Expression ignored");
                }
            }
        }
        return arrayOfIndexes;
    }

    public static void main(String... args) {
        // Arrange
        //  create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        ij.command().run(BioFormatsOpenPlugInSciJava.class, true);

    }
}
