package ch.epfl.biop;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.bioformats.command.BioformatsBigdataviewerBridgeDatasetCommand;
import ch.epfl.biop.bdv.bioformats.command.OpenFilesWithBigdataviewerBioformatsBridgeCommand;
import ch.epfl.biop.bdv.bioformats.export.IntRangeParser;
import ch.epfl.biop.bdv.bioformats.imageloader.SeriesNumber;
import ch.epfl.biop.scijava.command.source.ExportToMultipleImagePlusCommand;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.Scaler;
import ij.plugin.ZProjector;
import ij.process.ImageConverter;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.scijava.services.ui.SourceAndConverterServiceUI;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

public class OMETiffMultiSeriesProcessorExporter {

    private static Map<String, String> export(Builder builder) throws Exception {
        TaskService taskService = builder.ctx.getService(TaskService.class);
        File image_file = new File(builder.image_file_path);
        Task task = taskService.createTask("OME TIFF conversion "+image_file.getName());

        task.setStatusMessage("Parsing file metadata...");

        SourceAndConverterService sac_service = builder.ctx.getService(SourceAndConverterService.class);

        CommandService command = builder.ctx.getService(CommandService.class);

        String datasetName = builder.image_file_path;

        Map<String,Object> options = BioformatsBigdataviewerBridgeDatasetCommand.getDefaultParameters();
        options.put("datasetname", datasetName);
        options.put("unit","MICROMETER");
        options.put("files", new File[]{image_file});
        options.put("splitrgbchannels", false);
        options.put("numberofblockskeptinmemory", 1);

        if ((builder.cacheSizeX>0)&&(builder.cacheSizeY>0)) {
            options.put("usebioformatscacheblocksize", false);
            options.put("cachesizex", builder.cacheSizeX);
            options.put("cachesizey", builder.cacheSizeY);
            options.put("cachesizez", 1);
        }

        AbstractSpimData spimdata = (AbstractSpimData) command.run(OpenFilesWithBigdataviewerBioformatsBridgeCommand.class,true,
                    options
                ).get().getOutput("spimdata");

        List<SourceAndConverter<?>> allSources = sac_service.getSourceAndConverterFromSpimdata(spimdata);

        if (builder.removeZOffset) {
            for (SourceAndConverter source: allSources) {
                RealPoint center = SourceAndConverterHelper.getSourceAndConverterCenterPoint(source);
                AffineTransform3D zOffset = new AffineTransform3D();
                zOffset.translate(0,0,center.getDoublePosition(2));
                int maxTimePoint = SourceAndConverterHelper.getMaxTimepoint(source.getSpimSource());
                SourceTransformHelper.append(zOffset.inverse(), new SourceAndConverterAndTimeRange(source,0, maxTimePoint));
            }
        }

        SourceAndConverterServiceUI.Node seriesNode =
                sac_service.getUI()
                        .getRoot()
                        .child(datasetName)
                        .child(SeriesNumber.class.getSimpleName());

        System.out.println("nSeries = "+seriesNode.children().size());

        List<Integer> rangeSeries = new IntRangeParser(builder.rangeS).get(seriesNode.children().size());

        int number_of_series = rangeSeries.size();

        task.setProgressMaximum(number_of_series);

        AtomicInteger iImage = new AtomicInteger();

        Instant start = Instant.now();

        task.setStatusMessage("Conversion in progress");

        final Map<String, String> outputMap = new ConcurrentHashMap<>();

        ForkJoinPool pool = new ForkJoinPool(builder.n_threads);

        Callable c = () -> {

        rangeSeries.parallelStream().forEach(index -> {
                SourceAndConverterServiceUI.Node currentSeriesNode = seriesNode.child(index);
                try {
                    List<ImagePlus> ij1_images = (List<ImagePlus>) command.run(ExportToMultipleImagePlusCommand.class, false,
                            "sacs", currentSeriesNode.sources(),
                            "level", 0,
                            "range_frames", builder.rangeT,
                            "range_channels", builder.rangeC,
                            "range_slices", builder.rangeZ,
                            "export_mode", "Virtual no-cache", // Because we only read once!
                            "parallel", Boolean.TRUE,
                            "verbose", Boolean.TRUE
                    ).get().getOutput("imps_out");

                    if (ij1_images.size() != 1) IJ.log("ERROR : ONE IMAGE EXPECTED, MULTIPLE ONES FOUND");
                    ImagePlus image = ij1_images.get(0);
                    image.setTitle(currentSeriesNode.name()); // Fix issue with file name
                    IJ.log("Processing " + image.getTitle());
                    // Z Project
                    if (builder.z_project) {
                        String iniTitle = image.getTitle();
                        Calibration cal = image.getCalibration().copy();
                        int initialBitDepth = image.getBitDepth();
                        if ((image.getNFrames()>1)&&(image.getNSlices()==1)) {
                            image = ZProjector.run(image,"max");
                        } else {
                            ZProjector zp = new ZProjector();
                            zp.setImage(image);
                            zp.setMethod(Arrays.asList(ZProjector.METHODS).indexOf(builder.z_project_method));
                            zp.setStopSlice(image.getNSlices());
                            if (image.getNSlices() > 1 || image.getNFrames() > 1) {
                                zp.doHyperStackProjection(true);
                            }
                            image = zp.getProjection();
                        }
                        image.setTitle(iniTitle + "_ZProj_" + builder.z_project_method);
                        cal.zOrigin = 0; // remove z offset when projecting
                        image.setCalibration(cal);
                        // Restores bit depth
                        if (image.getBitDepth() != initialBitDepth) {
                            ImageConverter ic = new ImageConverter(image);
                            ic.setDoScaling(false);
                            switch (initialBitDepth) {
                                case 8:
                                    ic.convertToGray8();
                                    break;
                                case 16:
                                    ic.convertToGray16();
                                    break;
                                default:
                                    IJ.log("Conversion from " + image.getBitDepth() + " to " + initialBitDepth + " unsupported");
                            }
                        }
                    }
                    // Resizes the image if set by the user
                    if (builder.resize_xy != 1) {
                        String iniTitle = image.getTitle();
                        Calibration cal = image.getCalibration().copy();
                        cal.pixelWidth = cal.pixelWidth * builder.resize_xy;
                        cal.pixelHeight = cal.pixelHeight * builder.resize_xy;
                        cal.xOrigin = cal.xOrigin / builder.resize_xy;
                        cal.yOrigin = cal.yOrigin / builder.resize_xy;
                                image = Scaler.resize(
                                image, image.getWidth() / builder.resize_xy,
                        image.getHeight() / builder.resize_xy,
                                image.getNSlices(), "bilinear");
                        image.setTitle(iniTitle + "_RescaledXY_" + builder.resize_xy);
                        image.setCalibration(cal);
                    }
                    String prefix = "";
                    if (builder.appendFileName) prefix = image_file.getName()+"-";
                    String totalPath = builder.output_directory + File.separator + prefix + image.getTitle() + ".ome.tiff";
                    if (builder.overrideCalibration.containsKey(index)) {
                        image.setCalibration(builder.overrideCalibration.get(index));
                    }
                    ImagePlus finalImage = image;
                    new Thread(() -> {
                        try {
                            ImagePlusToOMETiff.writeToOMETiff(finalImage, new File(totalPath), builder.nResolutionLevels, builder.downscaleFactor, builder.compression);
                            outputMap.put(finalImage.getTitle(), totalPath);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        synchronized (OMETiffMultiSeriesProcessorExporter.class) {
                            printTimingMessage(start, ((double) (iImage.incrementAndGet()) / (double) (number_of_series)) * 100);
                            long currentProgress = iImage.get();
                            task.setProgressValue(currentProgress);
                            if (currentProgress == number_of_series) {
                                task.run(() -> {}); // finished task
                            }
                            synchronized (iImage) {
                                iImage.notify();
                            }
                        }
                    }).start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });  return  null;};

        pool.submit(c).get();

        while (iImage.get()!=number_of_series) {
            synchronized (iImage) {
                iImage.wait();
            }
        }

        // Cleanup
        sac_service.remove(allSources.toArray(new SourceAndConverter[0])); // Maybe an issue if no projection TODO : check that all files are written
        return outputMap;
    }

    public static void printTimingMessage(Instant start, double percentageCompleteness) {
        long s = Duration.between(start, Instant.now()).getSeconds();
        String elapsedTime = String.format("%d:%02d:%02d", (int) (s / 3600), (int) ((s % 3600) / 60), (int) (s % 60));
        double sPerPC = s / percentageCompleteness;
        long sRemaining = (long) ((100 - percentageCompleteness) * sPerPC);
        String remainingTime = String.format("%d:%02d:%02d", (int) (sRemaining / 3600), (int) ((sRemaining % 3600) / 60), (int) (sRemaining % 60));
        LocalDateTime estimateDoneJob = LocalDateTime.now().plus(Duration.ofSeconds(sRemaining));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        long nDays = sRemaining / (3600 * 24);
        String daysMessage = "";
        if (nDays == 1) {
            daysMessage += " tomorrow.";
        }
        if (nDays == 1) {
            daysMessage += " in " + nDays + " days.";
        }
        String formatDateTime = estimateDoneJob.format(formatter);
        if ((int) (percentageCompleteness)== 100) {
            String message = " -  Task completed. Elapsed time:" + elapsedTime + ".";
            IJ.log(message);
        } else {
            String message = " -  Task " + (int) (percentageCompleteness) + " % completed. Elapsed time:" + elapsedTime + ". Estimated remaining time: " + remainingTime + ". Job done at around " + formatDateTime + daysMessage;
            IJ.log(message);
        }
    }

    public static Builder builder(Context ctx) {
        Builder builder = new Builder();
        builder.ctx = ctx;
        return builder;
    }

    public static class Builder {
        String image_file_path; // Done
        Boolean z_project = false; // Done
        String z_project_method = ""; // Done
        Integer resize_xy = 1; // Done
        String output_directory; // Done
        Integer n_threads = 0; // Done
        Integer nResolutionLevels = 1; //
        Integer downscaleFactor = 1; //
        String compression = "Uncompressed"; // Done
        Boolean removeZOffset = false; // Done
        Context ctx; // Done
        int cacheSizeX = -1;
        int cacheSizeY = -1;

        String rangeS = ""; // Done
        String rangeC = ""; // Done
        String rangeZ = ""; // Done
        String rangeT = ""; // Done
        Map<Integer, Calibration> overrideCalibration = new HashMap<>();

        boolean appendFileName = false;

        /**
         *
         * @param f file to export
         * @return the builder
         */
        public Builder file(File f) {
            image_file_path = f.getAbsolutePath();
            if (output_directory==null) {
                output_directory = f.getParent();
            }
            return this;
        }

        public Builder appendFileName() {
            this.appendFileName = true;
            return this;
        }

        /**
         * Where to output the converted files
         * @param path folder path
         * @return the builder
         */
        public Builder outputFolder(String path) {
            output_directory = path;
            return this;
        }

        /**
         * String that is parsed by {@link IntRangeParser} in order to select and or reorder a subset of series in a multi series file
         * @param rangeS string to parse
         * @return the builder
         */
        public Builder rangeS(String rangeS) {
            this.rangeS = rangeS;
            return this;
        }

        /**
         * Can be used to override the calibration of a an exported series
         * @param series series index
         * @param calibration calibration image object
         * @return the builder
         */
        public Builder setCalibration(int series, Calibration calibration) {
            this.overrideCalibration.put(series, calibration);
            return this;
        }

        /**
         * String that is parsed by {@link IntRangeParser} in order to select and or reorder a subset of channels
         * @param rangeC string to parse
         * @return the builder
         */
        public Builder rangeC(String rangeC) {
            this.rangeC = rangeC;
            return this;
        }

        /**
         * Can be use to override the default cache bioformats cache size when opening the file
         * @param cacheSizeX cache size x in pixel
         * @param cacheSizeY cache size y in pixel
         * @return the builder
         */
        public Builder cacheSize(int cacheSizeX, int cacheSizeY) {
            this.cacheSizeX = cacheSizeX;
            this.cacheSizeY = cacheSizeY;
            return this;
        }

        /**
         * String that is parsed by {@link IntRangeParser} in order to select and or reorder a subset of z slices
         * @param rangeZ string to parse
         * @return the builder
         */
        public Builder rangeZ(String rangeZ) {
            this.rangeZ = rangeZ;
            return this;
        }

        /**
         * String that is parsed by {@link IntRangeParser} in order to select and or reorder a subset of frames
         * @param rangeT string to parse
         * @return the builder
         */
        public Builder rangeT(String rangeT) {
            this.rangeT = rangeT;
            return this;
        }

        /**
         * @param nThreads number of threads used for the conversion
         * @return the builder
         */
        public Builder nThreads(int nThreads) {
            this.n_threads = nThreads;
            return this;
        }

        /**
         * Number of resolution levels to be used in the conversion
         * @param nResolutionLevels number of resolution levels
         * @return the builder
         */
        public Builder nResolutionLevels(int nResolutionLevels) {
            this.nResolutionLevels = nResolutionLevels;
            return this;
        }

        /**
         * @param downscaleFactor Scaling factor between each resolution level
         * @return the builder
         */
        public Builder downscaleFactorLevels(int downscaleFactor) {
            this.downscaleFactor = downscaleFactor;
            return this;
        }

        /**
         * Performs a maximum intensity projection over each selected slices
         * @return the builder
         */
        public Builder projectMax() {
            this.z_project = true;
            this.z_project_method = "Max Intensity";
            this.removeZOffset = true;
            return this;
        }

        /**
         * Do not keep potential z offsets
         * @return the builder
         */
        public Builder removeZOffsets() {
            this.removeZOffset = true;
            return this;
        }

        /**
         * Min projection
         * @return the builder
         */
        public Builder projectMin() {
            this.z_project = true;
            this.z_project_method = "Min Intensity";
            this.removeZOffset = true;
            return this;
        }
        /**
         * Sum projection
         * @return the builder
         */
        public Builder projectSum() {
            this.z_project = true;
            this.z_project_method = "Sum Slices";
            this.removeZOffset = true;
            return this;
        }
        /**
         * Stdev projection
         * @return the builder
         */
        public Builder projectStDev() {
            this.z_project = true;
            this.z_project_method = "Standard Deviation";
            this.removeZOffset = true;
            return this;
        }

        /**
         * Median projection
         * @return the builder
         */
        public Builder projectMedian() {
            this.z_project = true;
            this.z_project_method = "Median";
            this.removeZOffset = true;
            return this;
        }

        /**
         * Average projection
         * @return the builder
         */
        public Builder projectAverage() {
            this.z_project = true;
            this.z_project_method = "Average Intensity";
            this.removeZOffset = true;
            return this;
        }

        /**
         * Use lzw compression in the output
         * @return the builder
         */
        public Builder lzw() {
            this.compression = "LZW";
            return this;
        }

        public Map<String, String> export() {
            try {
                return OMETiffMultiSeriesProcessorExporter.export(this);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

    }
}
