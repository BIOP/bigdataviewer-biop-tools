package ch.epfl.biop.bdv.uberdataset;

import bdv.AbstractViewerSetupImgLoader;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.real.FloatType;

public class UberSetupLoader<T extends NumericType<T>,V extends Volatile<T> & NumericType<V>> extends AbstractViewerSetupImgLoader<T, V> implements MultiResolutionSetupImgLoader< T > {

    AbstractViewerSetupImgLoader<T,V> innerAVSIL;

    MultiResolutionSetupImgLoader<T> innerMRSIL;

    public UberSetupLoader(T numType, V volType, Object bsil) {
        super(numType,volType);

        if (bsil instanceof MultiResolutionSetupImgLoader) {
            innerMRSIL = (MultiResolutionSetupImgLoader<T>) bsil;
        } else {
            innerMRSIL = SetupLoaderConvert.MultiResolutionFromBasicSetupImageLoader((BasicSetupImgLoader<T>) bsil);
        }

        if (bsil instanceof AbstractViewerSetupImgLoader) {
            innerAVSIL = (AbstractViewerSetupImgLoader<T, V>) bsil;
        } else {
            innerAVSIL = SetupLoaderConvert.ViewerFromBasicSetupImageLoader(numType, volType,(BasicSetupImgLoader<T>) bsil, innerMRSIL);
        }
    }

    @Override
    public RandomAccessibleInterval<V> getVolatileImage(int timepointId, int level, ImgLoaderHint... hints) {
        return innerAVSIL.getVolatileImage(timepointId, level, hints);
    }

    @Override
    public RandomAccessibleInterval<FloatType> getFloatImage(int timepointId, int level, boolean normalize, ImgLoaderHint... hints) {
        return innerMRSIL.getFloatImage(timepointId, level, normalize, hints);
    }

    @Override
    public Dimensions getImageSize(int timepointId, int level) {
        return innerMRSIL.getImageSize(timepointId, level);
    }

    @Override
    public RandomAccessibleInterval<T> getImage(int timepointId, int level, ImgLoaderHint... hints) {
        return innerMRSIL.getImage(timepointId, level, hints);
    }

    @Override
    public double[][] getMipmapResolutions() {
        return innerMRSIL.getMipmapResolutions();
    }

    @Override
    public AffineTransform3D[] getMipmapTransforms() {
        return innerMRSIL.getMipmapTransforms();
    }

    @Override
    public int numMipmapLevels() {
        return innerMRSIL.numMipmapLevels();
    }

    @Override
    public RandomAccessibleInterval<FloatType> getFloatImage(int timepointId, boolean normalize, ImgLoaderHint... hints) {
        return innerMRSIL.getFloatImage(timepointId, normalize, hints);
    }

    @Override
    public Dimensions getImageSize(int timepointId) {
        return innerMRSIL.getImageSize(timepointId);
    }

    @Override
    public VoxelDimensions getVoxelSize(int timepointId) {
        return innerMRSIL.getVoxelSize(timepointId);
    }
}
