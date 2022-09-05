package ch.epfl.biop.sourceandconverter.exporter;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.operetta.utils.HyperRange;
import ij.ImagePlus;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import org.scijava.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;
import spimdata.imageplus.ImagePlusHelper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ImagePlusSampler {

    private static Logger logger = LoggerFactory.getLogger(ImagePlusSampler.class);

    public static Builder Builder() {
        return new Builder();
    }

    private static <T extends NumericType<T> & NativeType<T>> ImagePlus get(String imageName,
                                List<SourceAndConverter<T>> sourceList,
                                SourceAndConverter model,
                                boolean automipmap,
                                int level,
                                CZTRange range,
                                String unit,
                                boolean interpolate,
                                boolean virtual,
                                boolean cache,
                                Task task,
                                boolean parallelC, boolean parallelZ, boolean parallelT) throws UnsupportedOperationException {

        if (sourceList.size() == 0) {
            throw  new UnsupportedOperationException("No input sources");
        }

        if (sourceList.stream().map(sac -> sac.getSpimSource().getType().getClass()).distinct().count()>1) {
            throw new UnsupportedOperationException("Cannot make composite because all sources are not of the same type");
        }

        // The core of it : resampling each source with the model
        List<SourceAndConverter<T>> resampledSourceList = sourceList
                .stream()
                .map(sac -> new SourceResampler<>(sac,model,sac.getSpimSource().getName()+"_SampledLike_"+model.getSpimSource().getName(), automipmap, cache, interpolate, level).get())
                .collect(Collectors.toList());

        SourceAndConverterServices.getSourceAndConverterService().register(model);

        /*int resolutionLevel = 0;
        Map<SourceAndConverter, Integer> mapMipmap = new HashMap<>();
        //sourceList.forEach(src -> {
        for (SourceAndConverter src:sourceList) {
            int mipmapLevel = SourceAndConverterHelper.bestLevel(src, 0, builder.sizePixelY);
            logger.debug("Mipmap level chosen for source ["+src.getSpimSource().getName()+"] : "+mipmapLevel);
            mapMipmap.put(resampledSourceList.get(sourceList.indexOf(src)), mipmapLevel);
            resolutionLevel = mipmapLevel;
        }*/

        int timepointbegin = 0;//range.getRangeT().get(0)-1;

        ImagePlus compositeImage;
        if (!virtual) {
            compositeImage = ImagePlusGetter.getImagePlus(imageName, resampledSourceList, 0, range, parallelC, parallelZ, parallelT, task);
        } else {
            compositeImage = ImagePlusGetter.getVirtualImagePlus(imageName, resampledSourceList, 0, range, cache, task);
        }

        compositeImage.setTitle(imageName);
        AffineTransform3D at3D = new AffineTransform3D();
        model.getSpimSource().getSourceTransform(timepointbegin, 0, at3D);
        ImagePlusHelper.storeExtendedCalibrationToImagePlus(compositeImage, at3D, unit, timepointbegin);
        compositeImage.show();

        return compositeImage;
    }

    /**
     * a builder of an image plus sampler
     */
    public static class Builder {

        private String imageName = "Image";
        private SourceAndConverter[] sacs = new SourceAndConverter[0];
        private SourceAndConverter<?> model;
        private boolean interpolate;
        private boolean cache = true;
        //private boolean verbose = false;
        private boolean virtual = false;
        private int level = -1;
        private String unit;
        private CZTRange.Builder rangeBuilder = new CZTRange.Builder();
        private Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultGeneric(sacslist);

        private boolean parallelC = false;
        private boolean parallelZ = false;
        private boolean parallelT = false;
        transient Task task = null;

        /**
         * @param sacs sources used for the sampling
         * @return the builder
         */
        public Builder sources(SourceAndConverter[] sacs) {
            this.sacs = sacs;
            return this;
        }

        /**
         * @param sorter which sorts the sources before sampling them
         * @return the builder
         */
        public Builder sort(Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter) {
            this.sorter = sorter;
            return this;
        }

        /**
         * @param model the model ( a source ) used for resampling the other sources
         * @return the builder
         */
        public Builder setModel(SourceAndConverter<?> model) {
            this.model = model;
            return this;
        }

        /**
         * @param rangeC to select and or reorder the channels for the resampling
         * @return the builder
         */
        public Builder rangeC(String rangeC) {
            this.rangeBuilder.setC(rangeC);
            return this;
        }

        /**
         * @param rangeT to select and or reorder the frames for the resampling
         * @return the builder
         */
        public Builder rangeT( String rangeT) {
            this.rangeBuilder.setT(rangeT);
            return this;
        }

        /**
         * @param rangeZ to select and or reorder the slies for the resampling
         * @return the builder
         */
        public Builder rangeZ( String rangeZ) {
            this.rangeBuilder.setZ(rangeZ);
            return this;
        }

        /**
         *
         * @param task object which be used to cancel and monitor how the resampling process is happening
         * @return the builder
         */
        public Builder monitor(Task task) {
            this.task = task;
            return this;
        }

        /**
         * Whether the resampled image should be virtual or not
         * @param flag virtual imageplus output
         * @return the builder
         */
        public Builder virtual(boolean flag) {
            this.virtual = flag;
            return this;
        }

        /**
         * The resolution level of teh MODEL used for resampling
         * @param level resolution level of the model
         * @return the builder
         */
        public Builder level(int level) {
            this.level = level;
            return this;
        }

        /**
         * @param flag if true, the sources are resampled from their linear interpolation sources
         * @return the builder
         */
        public Builder interpolate(boolean flag) {
            this.interpolate = flag;
            return this;
        }

        /**
         * @param flag if true, the virtual stack will be cached
         * @return the builder
         */
        public Builder cache(boolean flag) {
            this.cache = flag;
            return this;
        }

        /**
         * physical unit of the world coordinates system
         * @param unit physical unit
         * @return the builder
         */
        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        /**
         * @param title title of the output resampled image
         * @return the builder
         */
        public Builder title(String title) {
            this.imageName = title;
            return this;
        }

        /**
         * if not virtual, the acquisition of all channels will be performed in parallel
         * @return the builder
         */
        public Builder parallelC() {
            this.parallelC = true;
            return this;
        }

        /**
         * if not virtual, the acquisition of all channels can be performed in parallel
         * @param flag flag
         * @return the builder
         */
        public Builder parallelC(boolean flag) {
            this.parallelC = flag;
            return this;
        }

        /**
         * if not virtual, the acquisition of all z slices will be performed in parallel
         * @return the builder
         */
        public Builder parallelZ() {
            this.parallelZ = true;
            return this;
        }

        /**
         * if not virtual, the acquisition of all z slices can be performed in parallel
         * @param flag flag
         * @return the builder
         */
        public Builder parallelZ(boolean flag) {
            this.parallelZ = flag;
            return this;
        }

        /**
         * if not virtual, the acquisition of all frames will be performed in parallel
         * @return the builder
         */
        public Builder parallelT() {
            this.parallelT = true;
            return this;
        }

        /**
         * if not virtual, the acquisition of all frames can be performed in parallel
         * @param flag flag
         * @return the builder
         */
        public Builder parallelT(boolean flag) {
            this.parallelT = flag;
            return this;
        }

        /**
         * @return the resampled ImagePlus object
         * @throws Exception if something goes wrong
         */
        public ImagePlus get() throws Exception{

            int maxTimeFrames = SourceAndConverterHelper.getMaxTimepoint(sacs);

            //int maxZSlices = (int) sacs[0].getSpimSource().getSource(0,level).dimension(2);
            int maxZSlices = (int) model.getSpimSource().getSource(0,level).dimension(2);

            CZTRange range = rangeBuilder.get(sacs.length, maxZSlices, maxTimeFrames);

            return ImagePlusSampler.get(imageName,
                    Arrays.asList(sacs),
                    model,
                    level==-1,
                    level,
                    range,
                    unit,
                    interpolate,virtual, cache, task, parallelC, parallelZ, parallelT);
        }
    }
}
