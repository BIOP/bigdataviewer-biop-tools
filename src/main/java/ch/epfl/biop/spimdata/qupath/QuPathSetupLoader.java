package ch.epfl.biop.spimdata.qupath;

import bdv.AbstractViewerSetupImgLoader;
import bdv.viewer.Source;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.imageloader.BioFormatsSetupLoader;
import ch.epfl.biop.omero.imageloader.OmeroSetupLoader;
import ch.epfl.biop.omero.omerosource.OmeroSourceOpener;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * See documentation in {@link QuPathImageLoader}
 *
 * This class builds the setupLoader corresponding to the opener type (Bioformat or Omero) and get all the necessary
 * info from this loader.
 *
 * @author Rémy Dornier, EPFL, BIOP, 2022
 */

public class QuPathSetupLoader<T extends NumericType<T>,V extends Volatile<T> & NumericType<V>> extends AbstractViewerSetupImgLoader<T, V> implements MultiResolutionSetupImgLoader< T > {

    QuPathImageOpener opener;
    Object imageSetupLoader;
    public Source<T> concreteSource;
    public Source<V> volatileSource;
    final public int iSerie,iChannel;

    public QuPathSetupLoader(QuPathImageOpener qpOpener, int serieIndex, int channelIndex, T type, V volatileType) {
        super(type, volatileType);

        this.opener = qpOpener;
        this.iChannel = channelIndex;
        this.iSerie = serieIndex;

        // get the setup loader corresponding to BioFormat opener
        if(qpOpener.getOpener() instanceof BioFormatsBdvOpener){
            BioFormatsSetupLoader<?,?> bfSetupLoader = new BioFormatsSetupLoader(
                    (BioFormatsBdvOpener) this.opener.getOpener(),
                    serieIndex,
                    channelIndex,
                    type,
                    volatileType);
            this.concreteSource = (Source<T>)bfSetupLoader.concreteSource;
            this.volatileSource = (Source<V>)bfSetupLoader.volatileSource;
            this.imageSetupLoader = bfSetupLoader;

        }else{
            // get the setup loader corresponding to Omero opener
            if(qpOpener.getOpener() instanceof OmeroSourceOpener){
                try {
                    OmeroSetupLoader<?,?> omeSetupLoader = new OmeroSetupLoader(
                            (OmeroSourceOpener) this.opener.getOpener(),
                            channelIndex,
                            type,
                            volatileType);
                    this.concreteSource = (Source<T>)omeSetupLoader.concreteSource;
                    this.volatileSource = (Source<V>)omeSetupLoader.volatileSource;
                    this.imageSetupLoader = omeSetupLoader;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    @Override
    public RandomAccessibleInterval<V> getVolatileImage(int timepointId, int level, ImgLoaderHint... hints) {
        if(this.imageSetupLoader instanceof BioFormatsSetupLoader)
            return (RandomAccessibleInterval<V>)(((BioFormatsSetupLoader<?, ?>)this.imageSetupLoader).getVolatileImage(timepointId,level,hints));
        else{
            if(this.imageSetupLoader instanceof OmeroSetupLoader)
                return ((RandomAccessibleInterval<V>)((OmeroSetupLoader<?,?>)this.imageSetupLoader).getVolatileImage(timepointId,level,hints));
        }
        System.out.println("QuPathSetuploader/GetVolatileImage => the current loader is not recognized : "+this.imageSetupLoader.getClass().getName());
        return null;
    }

    @Override
    public RandomAccessibleInterval<FloatType> getFloatImage(int timepointId, int level, boolean normalize, ImgLoaderHint... hints) {
        if(this.imageSetupLoader instanceof BioFormatsSetupLoader)
            return ((BioFormatsSetupLoader<?, ?>)this.imageSetupLoader).getFloatImage(timepointId, level, normalize, hints);
        else{
            if(this.imageSetupLoader instanceof OmeroSetupLoader)
                return ((OmeroSetupLoader<?,?>)this.imageSetupLoader).getFloatImage(timepointId, level, normalize, hints);
        }
        System.out.println("QuPathSetuploader/getFloatImage => the current loader is not recognized : "+this.imageSetupLoader.getClass().getName());
        return null;
    }

    @Override
    public Dimensions getImageSize(int timepointId, int level) {
        if(this.imageSetupLoader instanceof BioFormatsSetupLoader)
            return ((BioFormatsSetupLoader<?, ?>)this.imageSetupLoader).getImageSize(timepointId,level);
        else{
            if(this.imageSetupLoader instanceof OmeroSetupLoader)
                return ((OmeroSetupLoader<?,?>)this.imageSetupLoader).getImageSize(timepointId,level);
        }
        System.out.println("QuPathSetuploader/getImageSize => the current loader is not recognized : "+this.imageSetupLoader.getClass().getName());
        return null;
    }


    @Override
    public RandomAccessibleInterval<T> getImage(int timepointId, int level, ImgLoaderHint... hints) {
        if(this.imageSetupLoader instanceof BioFormatsSetupLoader)
            return (RandomAccessibleInterval<T>)(((BioFormatsSetupLoader<?, ?>)this.imageSetupLoader).getImage(timepointId, level, hints));
        else{
            if(this.imageSetupLoader instanceof OmeroSetupLoader)
                return (RandomAccessibleInterval<T>)(((OmeroSetupLoader<?,?>)this.imageSetupLoader).getImage(timepointId, level, hints));
        }
        System.out.println("QuPathSetuploader/getImage => the current loader is not recognized : "+this.imageSetupLoader.getClass().getName());
        return null;
    }

    @Override
    public double[][] getMipmapResolutions() {
        if(this.imageSetupLoader instanceof BioFormatsSetupLoader)
            return ((BioFormatsSetupLoader<?, ?>)this.imageSetupLoader).getMipmapResolutions();
        else{
            if(this.imageSetupLoader instanceof OmeroSetupLoader)
                return ((OmeroSetupLoader<?,?>)this.imageSetupLoader).getMipmapResolutions();
        }
        System.out.println("QuPathSetuploader/getMipmapResolutions => the current loader is not recognized : "+this.imageSetupLoader.getClass().getName());
        return null;
    }

    @Override
    public AffineTransform3D[] getMipmapTransforms() {
        if(this.imageSetupLoader instanceof BioFormatsSetupLoader)
            return ((BioFormatsSetupLoader<?, ?>)this.imageSetupLoader).getMipmapTransforms();
        else{
            if(this.imageSetupLoader instanceof OmeroSetupLoader)
                return ((OmeroSetupLoader<?,?>)this.imageSetupLoader).getMipmapTransforms();
        }
        System.out.println("QuPathSetuploader/getMipmapTransforms => the current loader is not recognized : "+this.imageSetupLoader.getClass().getName());
        return null;
    }

    @Override
    public int numMipmapLevels() {
        if(this.imageSetupLoader instanceof BioFormatsSetupLoader)
            return ((BioFormatsSetupLoader<?, ?>)this.imageSetupLoader).numMipmapLevels();
        else{
            if(this.imageSetupLoader instanceof OmeroSetupLoader)
                return ((OmeroSetupLoader<?,?>)this.imageSetupLoader).numMipmapLevels();
        }
        System.out.println("QuPathSetuploader/numMipmapLevels => the current loader is not recognized : "+this.imageSetupLoader.getClass().getName());
        return 0;
    }

    @Override
    public RandomAccessibleInterval<FloatType> getFloatImage(int timepointId, boolean normalize, ImgLoaderHint... hints) {
        if(this.imageSetupLoader instanceof BioFormatsSetupLoader)
            return ((BioFormatsSetupLoader<?, ?>)this.imageSetupLoader).getFloatImage(timepointId,normalize,hints);
        else{
            if(this.imageSetupLoader instanceof OmeroSetupLoader)
                return ((OmeroSetupLoader<?,?>)this.imageSetupLoader).getFloatImage(timepointId,normalize,hints);
        }
        System.out.println("QuPathSetuploader/getFloatImage => the current loader is not recognized : "+this.imageSetupLoader.getClass().getName());
        return null;
    }

    @Override
    public Dimensions getImageSize(int timepointId) {
        if(this.imageSetupLoader instanceof BioFormatsSetupLoader)
            return ((BioFormatsSetupLoader<?, ?>)this.imageSetupLoader).getImageSize(timepointId);
        else{
            if(this.imageSetupLoader instanceof OmeroSetupLoader)
                return ((OmeroSetupLoader<?,?>)this.imageSetupLoader).getImageSize(timepointId);
        }
        System.out.println("QuPathSetuploader/getImageSize => the current loader is not recognized : "+this.imageSetupLoader.getClass().getName());
        return null;
    }


    @Override
    public VoxelDimensions getVoxelSize(int timepointId) {
        if(this.imageSetupLoader instanceof BioFormatsSetupLoader)
            return ((BioFormatsSetupLoader<?, ?>)this.imageSetupLoader).getVoxelSize(timepointId);
        else{
            if(this.imageSetupLoader instanceof OmeroSetupLoader)
                return ((OmeroSetupLoader<?,?>)this.imageSetupLoader).getVoxelSize(timepointId);
        }
        System.out.println("QuPathSetuploader/getVoxelSize => the current loader is not recognized : "+this.imageSetupLoader.getClass().getName());
        return null;
    }
}
