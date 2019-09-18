package ch.epfl.biop.bdv.uberdataset;

import bdv.AbstractViewerSetupImgLoader;
import bdv.util.volatiles.VolatileViews;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.real.FloatType;

public class SetupLoaderConvert {

    static public <T extends NumericType<T>> MultiResolutionSetupImgLoader<T> MultiResolutionFromBasicSetupImageLoader(BasicSetupImgLoader<T> loader) {
        // MultiResolutionSetupImgLoader< T >
        //      extends BasicMultiResolutionSetupImgLoader< T >, SetupImgLoader< T >
        //          public RandomAccessibleInterval< FloatType > getFloatImage( final int timepointId, final int level, boolean normalize, ImgLoaderHint... hints );
        //          public Dimensions getImageSize( final int timepointId, final int level );
        //  BasicMultiResolutionSetupImgLoader< T >
        //      extends BasicSetupImgLoader< T >
        //          public RandomAccessibleInterval< T > getImage( final int timepointId, final int level, ImgLoaderHint... hints );
        //          public double[][] getMipmapResolutions();
        //          public AffineTransform3D[] getMipmapTransforms();
        //          public int numMipmapLevels();
        // BasicSetupImgLoader< T >
        //          public RandomAccessibleInterval< T > getImage( final int timepointId, ImgLoaderHint... hints );
        //          public T getImageType();
        // SetupImgLoader< T > extends BasicSetupImgLoader< T >
        //          public RandomAccessibleInterval< FloatType > getFloatImage( final int timepointId, boolean normalize, ImgLoaderHint... hints );
        //          public Dimensions getImageSize( final int timepointId );
        //          public VoxelDimensions getVoxelSize( final int timepointId );

        MultiResolutionSetupImgLoader<T> mrsil = new MultiResolutionSetupImgLoader<T>() {
            @Override
            public RandomAccessibleInterval<FloatType> getFloatImage(int timepointId, int level, boolean normalize, ImgLoaderHint... hints) {
                return getFloatImage(timepointId, normalize, hints);
            }

            @Override
            public Dimensions getImageSize(int timepointId, int level) {
                return getImageSize(timepointId);
            }

            @Override
            public RandomAccessibleInterval<T> getImage(int timepointId, int level, ImgLoaderHint... hints) {
                return getImage(timepointId,hints);
            }

            @Override
            public double[][] getMipmapResolutions() {
                double [][] mmResolutions = new double[1][3];
                mmResolutions[0][0]=1;
                mmResolutions[0][1]=1;
                mmResolutions[0][2]=1;
                return mmResolutions;
            }

            @Override
            public AffineTransform3D[] getMipmapTransforms() {
                // One level, identity transform
                AffineTransform3D[] ats = new AffineTransform3D[1];
                ats[0] = new AffineTransform3D();
                ats[0].identity();
                return ats;
            }

            @Override
            public int numMipmapLevels() {
                return 0;
            }

            @Override
            public RandomAccessibleInterval<FloatType> getFloatImage(int timepointId, boolean normalize, ImgLoaderHint... hints) {
                if (loader instanceof SetupImgLoader) {
                    return ((SetupImgLoader<T>)loader).getFloatImage(timepointId, normalize, hints);
                } else {
                    // COULD BE DONE : make float image with converter...
                    return null;
                }
            }

            @Override
            public Dimensions getImageSize(int timepointId) {
                if (loader instanceof SetupImgLoader) {
                    return ((SetupImgLoader<T>) loader).getImageSize(timepointId);
                } else {
                    // Weird
                    return new Dimensions() {
                        @Override
                        public void dimensions(long[] dimensions) {
                            loader.getImage(timepointId).dimensions(dimensions);
                        }

                        @Override
                        public long dimension(int d) {
                            return loader.getImage(timepointId).dimension(d);
                        }

                        @Override
                        public int numDimensions() {
                            return loader.getImage(timepointId).numDimensions();
                        }
                    };
                }
            }

            @Override
            public VoxelDimensions getVoxelSize(int timepointId) {
                if (loader instanceof SetupImgLoader) {
                    return ((SetupImgLoader<T>) loader).getVoxelSize(timepointId);
                } else {
                    return new VoxelDimensions() {
                        @Override
                        public String unit() {
                            return "Undefined";
                        }

                        @Override
                        public void dimensions(double[] dimensions) {
                            dimensions[0]=1;
                            dimensions[1]=1;
                            dimensions[2]=1;
                        }

                        @Override
                        public double dimension(int d) {
                            return 1;
                        }

                        @Override
                        public int numDimensions() {
                            return 3;
                        }
                    };
                }
            }

            @Override
            public RandomAccessibleInterval<T> getImage(int timepointId, ImgLoaderHint... hints) {
                return loader.getImage(timepointId, hints);
            }

            @Override
            public T getImageType() {
                return loader.getImageType();
            }
        };
        return mrsil;
    }

    static public <T extends NumericType<T>,V extends Volatile<T> & NumericType<V>> AbstractViewerSetupImgLoader<T,V>
    ViewerFromBasicSetupImageLoader (T t, V v, BasicSetupImgLoader<T> loader, MultiResolutionSetupImgLoader<T> mrsil) {
        AbstractViewerSetupImgLoader<T,V> avsil = new AbstractViewerSetupImgLoader<T, V>(t,v) {
            @Override
            public RandomAccessibleInterval<V> getVolatileImage(int timepointId, int level, ImgLoaderHint... hints) {
                return VolatileViews.wrapAsVolatile(mrsil.getImage(timepointId, level, hints));
            }

            @Override
            public RandomAccessibleInterval<T> getImage(int timepointId, int level, ImgLoaderHint... hints) {
                return mrsil.getImage(timepointId,level,hints);
            }

            @Override
            public double[][] getMipmapResolutions() {
                return mrsil.getMipmapResolutions();
            }

            @Override
            public AffineTransform3D[] getMipmapTransforms() {
                return mrsil.getMipmapTransforms();
            }

            @Override
            public int numMipmapLevels() {
                return mrsil.numMipmapLevels();
            }
        };
        return avsil;
    }

}
