/*-
 * #%L
 * Commands and function for opening, conversion and easy use of bioformats format into BigDataViewer
 * %%
 * Copyright (C) 2019 - 2025 Nicolas Chiaruttini, BIOP, EPFL
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
package ch.epfl.biop.spimdata.combined;

import ch.epfl.biop.spimdata.reordered.ISetupOrder;
import ch.epfl.biop.spimdata.reordered.ReorderedImageLoader;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.sequence.ViewId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link ISetupOrder} that combines multiple SpimData sources
 * into a unified coordinate space.
 * <p>
 * Supports two main modes:
 * <ul>
 *   <li>{@link MappingMode#CONCATENATE_TIMEPOINTS}: Each source file becomes a timepoint</li>
 *   <li>{@link MappingMode#CONCATENATE_CHANNELS}: Each source file's setups are added as additional channels</li>
 * </ul>
 */
public class CombinedOrder implements ISetupOrder {

    private static final Logger logger = LoggerFactory.getLogger(CombinedOrder.class);

    /**
     * Defines how multiple sources are combined.
     */
    public enum MappingMode {
        /**
         * Each source becomes a separate timepoint.
         * Source index maps to target timepoint.
         * SetupIds remain unchanged.
         */
        CONCATENATE_TIMEPOINTS,

        /**
         * Each source's setups are concatenated.
         * SetupIds are renumbered sequentially.
         * Timepoints remain unchanged.
         */
        CONCATENATE_CHANNELS
    }

    // Persisted state (for future serialization)
    private final List<String> sourcePaths;
    private final MappingMode mode;

    // For CONCATENATE_CHANNELS mode: number of setups per source
    private final List<Integer> setupsPerSource;

    // Transient state (rebuilt on initialize)
    private transient List<AbstractSpimData<?>> loadedSources;
    private transient boolean initialized = false;

    /**
     * Creates a CombinedOrder for concatenating timepoints.
     *
     * @param sourcePaths Absolute paths to source XML files, in order
     * @return CombinedOrder configured for timepoint concatenation
     */
    public static CombinedOrder forTimepoints(List<String> sourcePaths) {
        return new CombinedOrder(sourcePaths, MappingMode.CONCATENATE_TIMEPOINTS, null);
    }

    /**
     * Creates a CombinedOrder for concatenating channels.
     *
     * @param sourcePaths     Absolute paths to source XML files, in order
     * @param setupsPerSource Number of setups in each source (needed for mapping)
     * @return CombinedOrder configured for channel concatenation
     */
    public static CombinedOrder forChannels(List<String> sourcePaths, List<Integer> setupsPerSource) {
        return new CombinedOrder(sourcePaths, MappingMode.CONCATENATE_CHANNELS, setupsPerSource);
    }

    private CombinedOrder(List<String> sourcePaths, MappingMode mode, List<Integer> setupsPerSource) {
        this.sourcePaths = new ArrayList<>(sourcePaths);
        this.mode = mode;
        this.setupsPerSource = setupsPerSource != null ? new ArrayList<>(setupsPerSource) : null;
    }

    @Override
    public void initialize() {
        if (initialized) {
            return;
        }

        loadedSources = new ArrayList<>();
        XmlIoSpimData xmlIo = new XmlIoSpimData();

        for (String path : sourcePaths) {
            try {
                logger.debug("Loading source SpimData from: {}", path);
                AbstractSpimData<?> spimData = xmlIo.load(path);
                loadedSources.add(spimData);
            } catch (SpimDataException e) {
                throw new RuntimeException("Failed to load SpimData from: " + path, e);
            }
        }

        initialized = true;
        logger.info("CombinedOrder initialized with {} sources in {} mode", loadedSources.size(), mode);
    }

    @Override
    public ReorderedImageLoader.SpimDataViewId getOriginalLocation(ViewId viewId) {
        if (!initialized) {
            throw new IllegalStateException("CombinedOrder not initialized. Call initialize() first.");
        }

        ReorderedImageLoader.SpimDataViewId result = new ReorderedImageLoader.SpimDataViewId();

        switch (mode) {
            case CONCATENATE_TIMEPOINTS:
                result = mapForConcatenateTimepoints(viewId);
                break;
            case CONCATENATE_CHANNELS:
                result = mapForConcatenateChannels(viewId);
                break;
            default:
                throw new UnsupportedOperationException("Unknown mapping mode: " + mode);
        }

        logger.trace("Mapped [{}, t={}] -> source[{}], [{}, t={}]",
                viewId.getViewSetupId(), viewId.getTimePointId(),
                sourcePaths.indexOf(result.asd.toString()),
                result.viewId.getViewSetupId(), result.viewId.getTimePointId());

        return result;
    }

    /**
     * Maps for CONCATENATE_TIMEPOINTS mode.
     * Target timepoint = source index
     * Target setupId = source setupId (unchanged)
     */
    private ReorderedImageLoader.SpimDataViewId mapForConcatenateTimepoints(ViewId viewId) {
        int targetTimepoint = viewId.getTimePointId();
        int targetSetupId = viewId.getViewSetupId();

        // Target timepoint directly maps to source index
        int sourceIndex = targetTimepoint;

        if (sourceIndex < 0 || sourceIndex >= loadedSources.size()) {
            throw new IndexOutOfBoundsException(
                    "Timepoint " + targetTimepoint + " out of range. Only " + loadedSources.size() + " sources available.");
        }

        ReorderedImageLoader.SpimDataViewId result = new ReorderedImageLoader.SpimDataViewId();
        result.asd = loadedSources.get(sourceIndex);
        // All sources have timepoint 0, setupId unchanged
        result.viewId = new ViewId(0, targetSetupId);

        return result;
    }

    /**
     * Maps for CONCATENATE_CHANNELS mode.
     * Target setupId is renumbered: source 0 setups [0..n-1], source 1 setups [n..m-1], etc.
     * Target timepoint = source timepoint (unchanged)
     */
    private ReorderedImageLoader.SpimDataViewId mapForConcatenateChannels(ViewId viewId) {
        int targetTimepoint = viewId.getTimePointId();
        int targetSetupId = viewId.getViewSetupId();

        // Find which source this setupId belongs to
        int sourceIndex = 0;
        int setupOffset = 0;

        for (int i = 0; i < setupsPerSource.size(); i++) {
            int setupsInThisSource = setupsPerSource.get(i);
            if (targetSetupId < setupOffset + setupsInThisSource) {
                sourceIndex = i;
                break;
            }
            setupOffset += setupsInThisSource;
        }

        int sourceSetupId = targetSetupId - setupOffset;

        if (sourceIndex >= loadedSources.size()) {
            throw new IndexOutOfBoundsException(
                    "SetupId " + targetSetupId + " out of range for available sources.");
        }

        ReorderedImageLoader.SpimDataViewId result = new ReorderedImageLoader.SpimDataViewId();
        result.asd = loadedSources.get(sourceIndex);
        result.viewId = new ViewId(targetTimepoint, sourceSetupId);

        return result;
    }

    // Getters for serialization support

    public List<String> getSourcePaths() {
        return new ArrayList<>(sourcePaths);
    }

    public MappingMode getMode() {
        return mode;
    }

    public List<Integer> getSetupsPerSource() {
        return setupsPerSource != null ? new ArrayList<>(setupsPerSource) : null;
    }

    public int getNumSources() {
        return sourcePaths.size();
    }

    /**
     * Returns loaded source SpimData. Only valid after initialize() is called.
     */
    public List<AbstractSpimData<?>> getLoadedSources() {
        if (!initialized) {
            throw new IllegalStateException("CombinedOrder not initialized.");
        }
        return loadedSources;
    }
}