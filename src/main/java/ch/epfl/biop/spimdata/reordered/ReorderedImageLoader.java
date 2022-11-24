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
import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.cache.SharedQueue;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.NumericType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The idea of this loader is to allow for maximum flexibility in order to link Sources from
 * multiple spimdata objects into a new spimdata object. Provided there is enough compatibility
 * (same pixel type, probably same image size) we can re-use source and redefine them in terms
 * of timepoints and channels and tiles, etc.
 *
 * A bdv.viewer.Source can occupy :
 * - a certain space interval in 3D (RAI) and a certain space interval in time (tBegin - tEnd)
 * In fact, it can be more complicated than that because of missing views, but that's not supported ( could be )
 *
 * These sources can originate from potentially many AbstractSpimData object.
 *
 * An image loader should provide a RAI (3D), as well as other properties ( dimension, mipmap ),
 * based on the following "coordinates":
 * - a setupId
 * - a timepoint
 *
 * This is in fact a {@link ViewId}
 *
 * So this image loader and this associated setuploader ( ShuffledSourceSetupLoader ) maps from:
 *
 * (setupId, timepoint) to (spimdata, setupid, timepoint)
 *
 * Requirements : origin setup loader should be multiresolution loader supported volatile access (AbstractViewerSetupImgLoader)
 *
 * The first timepoint of each view setup is used to gather mipmap information etc.
 *
 */

public class  ReorderedImageLoader<T extends AbstractViewerSetupImgLoader & MultiResolutionSetupImgLoader> implements ViewerImgLoader,MultiResolutionImgLoader {

    final AbstractSequenceDescription<?, ?, ?> sequenceDescription;

    protected static Logger logger = LoggerFactory.getLogger(ReorderedImageLoader.class);

    Map<Integer, ReorderedSetupLoader> imgLoaders = new ConcurrentHashMap<>();

    Map<Integer, T> originImgLoaders = new ConcurrentHashMap<>();

    Map<AbstractSpimData, MultiResolutionImgLoader> spimdataImageLoaders = new ConcurrentHashMap<>();

    protected VolatileGlobalCellCache cache;

    protected SharedQueue sq;

    public final int numFetcherThreads;
    public final int numPriorities;

    final ISetupOrder order;

    public ReorderedImageLoader(ISetupOrder shuffler, final AbstractSequenceDescription<?, ?, ?> sequenceDescription, int numFetcherThreads, int numPriorities) {
        this.sequenceDescription = sequenceDescription;
        this.numFetcherThreads=numFetcherThreads;
        this.numPriorities=numPriorities;
        sq = new SharedQueue(numFetcherThreads,numPriorities);

        this.order = shuffler;


        // NOT CORRECTLY IMPLEMENTED YET
        //final BlockingFetchQueues<Callable<?>> queue = new BlockingFetchQueues<>(1,1);
        cache = new VolatileGlobalCellCache(sq);
    }

    public ISetupOrder getOrder() {
        return order;
    }

    public ReorderedSetupLoader getSetupImgLoader(int setupId) {
        if (imgLoaders.containsKey(setupId)) {
            return imgLoaders.get(setupId);
        } else {
            logger.debug("loading setupId = "+setupId);

            ViewId currentViewId = new ViewId(0,setupId);

            SpimDataViewId svi = order.getOriginalLocation(currentViewId);

            AbstractSpimData originSpimData = svi.asd;

            ViewId originViewId = svi.viewId;

            BasicImgLoader originImageLoader = getOrFetchImageLoader(originSpimData);

            BasicSetupImgLoader setupImgLoader = originImageLoader.getSetupImgLoader(originViewId.getViewSetupId());

            if (!(setupImgLoader instanceof AbstractViewerSetupImgLoader)) {
                logger.error("Origin setup loader is not of instance AbstractViewerSetupImgLoader, reordering is not supported");
                throw new UnsupportedOperationException("Unsupported setup loader class "+setupImgLoader.getClass());
            }

            if (!(setupImgLoader instanceof MultiResolutionSetupImgLoader)) {
                logger.error("Origin setup loader is not of instance MultiResolutionSetupImgLoader, reordering is not supported");
                throw new UnsupportedOperationException("Unsupported setup loader class "+setupImgLoader.getClass());
            }

            T originSetupLoader = (T) setupImgLoader;

            originImgLoaders.put(setupId, originSetupLoader);

            ReorderedSetupLoader setupLoader =
                    new ReorderedSetupLoader(this, setupId,
                            (NumericType) originSetupLoader.getImageType(),
                            originSetupLoader.getVolatileImageType());

            imgLoaders.put(setupId,setupLoader);
            return setupLoader;
        }
    }

    @Override
    public CacheControl getCacheControl() {
        return cache;
    }

    public SharedQueue getQueue() {
        return sq;
    }

    public void close() {
        synchronized (this) {
            cache.clearCache();
            sq.shutdown();
        }
    }

    public static class SpimDataViewId {
        public AbstractSpimData asd;
        public ViewId viewId;
    }

    // SETUP LOADER FUNCTIONS

    public RandomAccessibleInterval getVolatileImage(int setupId, int timepointId, int level, ImgLoaderHint... hints) {
        SpimDataViewId svi = order.getOriginalLocation(new ViewId(timepointId, setupId));

        AbstractSpimData asd = svi.asd;
        int setupIdRedirect = svi.viewId.getViewSetupId();
        int timepointRedirect = svi.viewId.getTimePointId();

        return ((ViewerSetupImgLoader)getOrFetchImageLoader(asd)
                .getSetupImgLoader(setupIdRedirect))
                .getVolatileImage( timepointRedirect, level, hints);
    }

    public Dimensions getImageSize(int setupId, int timepointId, int level) {
        SpimDataViewId svi = order.getOriginalLocation(new ViewId(timepointId, setupId));

        AbstractSpimData asd = svi.asd;
        int setupIdRedirect = svi.viewId.getViewSetupId();
        int timepointRedirect = svi.viewId.getTimePointId();

        return getOrFetchImageLoader(asd)
                .getSetupImgLoader(setupIdRedirect)
                .getImageSize( timepointRedirect, level);
    }

    public RandomAccessibleInterval getImage(int setupId, int timepointId, int level, ImgLoaderHint... hints) {
        SpimDataViewId sdvi = order.getOriginalLocation(new ViewId(timepointId, setupId));

        AbstractSpimData asd = sdvi.asd;
        int setupIdRedirect = sdvi.viewId.getViewSetupId();
        int timepointRedirect = sdvi.viewId.getTimePointId();

        return getOrFetchImageLoader(asd)
                .getSetupImgLoader(setupIdRedirect)
                .getImage( timepointRedirect, level, hints);
    }

    public double[][] getMipmapResolutions(int setupId) {
        return originImgLoaders.get(setupId).getMipmapResolutions();
    }

    public AffineTransform3D[] getMipmapTransforms(int setupId) {
        return originImgLoaders.get(setupId).getMipmapTransforms();
    }

    public int numMipmapLevels(int setupId) {
        return originImgLoaders.get(setupId).numMipmapLevels();
    }

    public Dimensions getImageSize(int setupId, int timepointId) {
        SpimDataViewId svi = order.getOriginalLocation(new ViewId(timepointId, setupId));

        AbstractSpimData asd = svi.asd;
        int setupIdRedirect = svi.viewId.getViewSetupId();
        int timepointRedirect = svi.viewId.getTimePointId();

        return getOrFetchImageLoader(asd)
                .getSetupImgLoader(setupIdRedirect)
                .getImageSize(timepointRedirect);
    }

    public VoxelDimensions getVoxelSize(int setupId, int timepointId) {
        SpimDataViewId svi = order.getOriginalLocation(new ViewId(timepointId, setupId));

        AbstractSpimData asd = svi.asd;
        int setupIdRedirect = svi.viewId.getViewSetupId();
        int timepointRedirect = svi.viewId.getTimePointId();

        return getOrFetchImageLoader(asd)
                .getSetupImgLoader(setupIdRedirect)
                .getVoxelSize(timepointRedirect);
    }

    MultiResolutionImgLoader getOrFetchImageLoader(AbstractSpimData asd) {
        if (!spimdataImageLoaders.containsKey(asd)) {
            BasicImgLoader imgLoader = asd.getSequenceDescription().getImgLoader();

            if (!(imgLoader instanceof MultiResolutionImgLoader)) {
                logger.error("Origin image loader is not of instance MultiResolutionImgLoader, reoredering is not supported");
                throw new UnsupportedOperationException("Unsupported image loader class "+imgLoader.getClass());
            }

            if (!(imgLoader instanceof ViewerImgLoader)) {
                logger.error("Origin image loader is not of instance ViewerImgLoader, reoredering is not supported");
                throw new UnsupportedOperationException("Unsupported image loader class "+imgLoader.getClass());
            }

            spimdataImageLoaders.put(asd, (MultiResolutionImgLoader) imgLoader);
        }

        return spimdataImageLoaders.get(asd);
    }

}
