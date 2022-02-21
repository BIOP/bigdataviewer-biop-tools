package ch.epfl.biop.sourceandconverter.exporter;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.operetta.utils.HyperRange;
import ij.ImagePlus;
import net.imglib2.realtransform.AffineTransform3D;
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

    public static ImagePlus get(String imageName,
                                List<SourceAndConverter> sourceList,
                                SourceAndConverter model,
                                boolean automipmap,
                                int level,
                                CZTRange range,
                                String unit,
                                boolean interpolate,
                                boolean virtual,
                                boolean cache,
                                boolean verbose,
                                boolean parallelC, boolean parallelZ, boolean parallelT) throws UnsupportedOperationException {

        if (sourceList.size() == 0) {
            throw  new UnsupportedOperationException("No input sources");
        }

        if (sourceList.stream().map(sac -> sac.getSpimSource().getType().getClass()).distinct().count()>1) {
            throw new UnsupportedOperationException("Cannot make composite because all sources are not of the same type");
        }

        // The core of it : resampling each source with the model
        List<SourceAndConverter> resampledSourceList = sourceList
                .stream()
                .map(sac -> new SourceResampler(sac,model,sac.getSpimSource().getName()+"_SampledLike_"+model.getSpimSource().getName(), automipmap, cache, interpolate, level).get())
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
            compositeImage = ImagePlusGetter.getImagePlus(imageName, resampledSourceList, 0, range, verbose, parallelC, parallelZ, parallelT);
        } else {
            compositeImage = ImagePlusGetter.getVirtualImagePlus(imageName, resampledSourceList, 0, range, cache, verbose);
        }

        compositeImage.setTitle(imageName);
        AffineTransform3D at3D = new AffineTransform3D();
        model.getSpimSource().getSourceTransform(timepointbegin, 0, at3D);
        ImagePlusHelper.storeExtendedCalibrationToImagePlus(compositeImage, at3D, unit, timepointbegin);
        compositeImage.show();

        return compositeImage;
    }

    public static class Builder {

        private String imageName = "Image";
        private SourceAndConverter[] sacs = new SourceAndConverter[0];
        private SourceAndConverter<?> model;
        private boolean interpolate;
        private boolean cache = true;
        private boolean verbose = false;
        private boolean virtual = false;
        private int level = -1;
        private String unit;
        private CZTRange.Builder rangeBuilder = new CZTRange.Builder();
        private Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultGeneric(sacslist);

        private boolean parallelC = false;
        private boolean parallelZ = false;
        private boolean parallelT = false;

        public Builder sources(SourceAndConverter[] sacs) {
            this.sacs = sacs;
            return this;
        }

        public Builder sort(Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter) {
            this.sorter = sorter;
            return this;
        }

        public Builder setModel(SourceAndConverter<?> model) {
            this.model = model;
            return this;
        }

        public Builder rangeC(String rangeC) {
            this.rangeBuilder.setC(rangeC);
            return this;
        }

        public Builder rangeT( String rangeT) {
            this.rangeBuilder.setT(rangeT);
            return this;
        }

        public Builder rangeZ( String rangeZ) {
            this.rangeBuilder.setZ(rangeZ);
            return this;
        }

        public Builder monitor(boolean flag) {
            this.verbose = flag;
            return this;
        }

        public Builder virtual(boolean flag) {
            this.virtual = flag;
            return this;
        }

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public Builder interpolate(boolean flag) {
            this.interpolate = flag;
            return this;
        }

        public Builder cache(boolean flag) {
            this.cache = flag;
            return this;
        }

        public Builder unit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder title(String title) {
            this.imageName = title;
            return this;
        }

        public Builder parallelC() {
            this.parallelC = true;
            return this;
        }

        public Builder parallelZ() {
            this.parallelZ = true;
            return this;
        }

        public Builder parallelT() {
            this.parallelT = true;
            return this;
        }

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
                    interpolate,virtual, cache, verbose, parallelC, parallelZ, parallelT);
        }
    }
}
