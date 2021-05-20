/*-
 * #%L
 * Commands and function for opening, conversion and easy use of bioformats format into BigDataViewer
 * %%
 * Copyright (C) 2019 - 2021 Nicolas Chiaruttini, BIOP, EPFL
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the BIOP nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package ch.epfl.biop.spimdata.reordered;

import bdv.AbstractViewerSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.AbstractIntegerType;
import net.imglib2.type.numeric.real.FloatType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class ReorderedSetupLoader<T extends NumericType<T>,V extends Volatile<T> & NumericType<V>> extends AbstractViewerSetupImgLoader<T, V> implements MultiResolutionSetupImgLoader< T > {

    protected static Logger logger = LoggerFactory.getLogger(ReorderedSetupLoader.class);

    Function<RandomAccessibleInterval<T>, RandomAccessibleInterval<FloatType>> cvtRaiToFloatRai;

    final Converter<T,FloatType> cvt;

    final int setupId;

    final ReorderedImageLoader imgLoader;

    public ReorderedSetupLoader(ReorderedImageLoader imgLoader,
                                int setupId,
                                T t,
                                V v) {
        super(t, v);

        this.setupId = setupId;
        this.imgLoader = imgLoader;

        if (t instanceof FloatType) {
            cvt = null;
            cvtRaiToFloatRai = rai -> (RandomAccessibleInterval<FloatType>) rai; // Nothing to be done
        }else if (t instanceof ARGBType) {
            // Average of RGB value
            cvt = (input, output) -> {
                int val = ((ARGBType) input).get();
                int r = ARGBType.red(val);
                int g = ARGBType.green(val);
                int b = ARGBType.blue(val);
                output.set(r+g+b);
            };
            cvtRaiToFloatRai = rai -> Converters.convert( rai, cvt, new FloatType());
        }else if (t instanceof AbstractIntegerType) {
            cvt = (input, output) -> output.set(((AbstractIntegerType) input).getRealFloat());
            cvtRaiToFloatRai = rai -> Converters.convert( rai, cvt, new FloatType());
        }else {
            cvt = null;
            cvtRaiToFloatRai = e -> {
                logger.error("Conversion of "+t.getClass()+" to FloatType unsupported.");
                return null;
            };
        }
    }

    @Override
    public RandomAccessibleInterval<V> getVolatileImage(int timepointId, int level, ImgLoaderHint... hints) {
        return imgLoader.getVolatileImage(setupId, timepointId, level, hints);//volatileSource.getSource(timepointId,level);
    }

    @Override
    public RandomAccessibleInterval<FloatType> getFloatImage(int timepointId, int level, boolean normalize, ImgLoaderHint... hints) {
        return cvtRaiToFloatRai.apply(getImage(timepointId,level));
    }

    @Override
    public Dimensions getImageSize(int timepointId, int level) {
        return imgLoader.getImageSize(setupId, timepointId, level);
    }

    @Override
    public RandomAccessibleInterval<T> getImage(int timepointId, int level, ImgLoaderHint... hints) {
        return imgLoader.getImage(setupId, timepointId, level, hints);//concreteSource.getSource(timepointId,level);
    }

    @Override
    public double[][] getMipmapResolutions() {
        return imgLoader.getMipmapResolutions(setupId);
    }

    @Override
    public AffineTransform3D[] getMipmapTransforms() {
        return imgLoader.getMipmapTransforms(setupId);
    }

    @Override
    public int numMipmapLevels() {
        return imgLoader.numMipmapLevels(setupId);
    }

    @Override
    public RandomAccessibleInterval<FloatType> getFloatImage(int timepointId, boolean normalize, ImgLoaderHint... hints) {
        return cvtRaiToFloatRai.apply(getImage(timepointId,0));
    }

    @Override
    public Dimensions getImageSize(int timepointId) {
        return imgLoader.getImageSize(setupId, timepointId);
    }

    @Override
    public VoxelDimensions getVoxelSize(int timepointId) {
        return imgLoader.getVoxelSize(setupId, timepointId);
    }

}
