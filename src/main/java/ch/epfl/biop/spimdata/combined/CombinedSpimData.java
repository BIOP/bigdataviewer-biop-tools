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

import ch.epfl.biop.spimdata.reordered.ReorderedImageLoader;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Factory class for creating combined SpimData from multiple source datasets.
 * <p>
 * Primary use cases:
 * <ul>
 *   <li>{@link #fromTimepoints(List)}: Combine multiple single-timepoint files into a timelapse</li>
 *   <li>{@link #fromChannels(List)}: Combine multiple files as separate channels</li>
 * </ul>
 *
 * <h3>Example: Creating and saving a timelapse from multiple files</h3>
 * <pre>{@code
 * // Create combined dataset
 * List<String> files = Arrays.asList(
 *     "/path/to/timepoint_0.xml",
 *     "/path/to/timepoint_1.xml",
 *     "/path/to/timepoint_2.xml"
 * );
 * AbstractSpimData<?> timelapse = CombinedSpimData.fromTimepoints(files);
 *
 * // Save combined dataset
 * new XmlIoSpimData().save(timelapse, "/path/to/combined.xml");
 *
 * // Later, reload the combined dataset
 * AbstractSpimData<?> reloaded = new XmlIoSpimData().load("/path/to/combined.xml");
 * }</pre>
 *
 * <p><b>Note:</b> Serialization uses {@link ch.epfl.biop.spimdata.reordered.XmlIoReorderedImgLoader}
 * which is automatically registered via the {@link mpicbg.spim.data.generic.sequence.ImgLoaderIo} annotation.
 */
public class CombinedSpimData {

    private static final Logger logger = LoggerFactory.getLogger(CombinedSpimData.class);

    private static final int DEFAULT_NUM_FETCHER_THREADS = 4;
    private static final int DEFAULT_NUM_PRIORITIES = 2;

    /**
     * Creates a combined SpimData where each source file becomes a timepoint.
     * <p>
     * All source files must have identical structure (same ViewSetups).
     * Each source file is expected to have timepoint 0.
     * The resulting dataset will have timepoints [0, 1, 2, ...] corresponding to the file order.
     *
     * @param xmlPaths Absolute paths to source XML files, in temporal order
     * @return Combined SpimData with concatenated timepoints
     */
    public static AbstractSpimData<?> fromTimepoints(List<String> xmlPaths) {
        return fromTimepoints(xmlPaths, DEFAULT_NUM_FETCHER_THREADS, DEFAULT_NUM_PRIORITIES);
    }

    /**
     * Creates a combined SpimData where each source file becomes a timepoint.
     *
     * @param xmlPaths          Absolute paths to source XML files, in temporal order
     * @param numFetcherThreads Number of fetcher threads for the image loader
     * @param numPriorities     Number of priority levels for the cache
     * @return Combined SpimData with concatenated timepoints
     */
    public static AbstractSpimData<?> fromTimepoints(List<String> xmlPaths, int numFetcherThreads, int numPriorities) {
        if (xmlPaths == null || xmlPaths.isEmpty()) {
            throw new IllegalArgumentException("At least one source path is required");
        }

        logger.info("Creating combined SpimData from {} timepoint files", xmlPaths.size());

        // Load first source to get structure
        AbstractSpimData<?> firstSource = loadSpimData(xmlPaths.get(0));

        // Create CombinedOrder
        CombinedOrder order = CombinedOrder.forTimepoints(xmlPaths);
        order.initialize();

        // Build unified TimePoints
        List<TimePoint> timePoints = new ArrayList<>();
        IntStream.range(0, xmlPaths.size()).forEach(tp -> timePoints.add(new TimePoint(tp)));

        // Copy ViewSetups from first source
        List<ViewSetup> viewSetups = copyViewSetups(firstSource);

        // Build ViewRegistrations from all sources
        List<ViewRegistration> registrations = buildRegistrationsForTimepoints(order.getLoadedSources(), viewSetups);

        // Create SequenceDescription
        SequenceDescription sd = new SequenceDescription(
                new TimePoints(timePoints),
                viewSetups,
                null,
                new MissingViews(new ArrayList<>())
        );

        // Create and set the ReorderedImageLoader
        ReorderedImageLoader<?> imgLoader = new ReorderedImageLoader<>(order, sd, numFetcherThreads, numPriorities);
        sd.setImgLoader(imgLoader);

        // Create and return SpimData
        SpimData spimData = new SpimData((File) null, sd, new ViewRegistrations(registrations));

        logger.info("Created combined SpimData with {} timepoints and {} setups",
                timePoints.size(), viewSetups.size());

        return spimData;
    }

    /**
     * Creates a combined SpimData where each source file's setups are added as additional channels.
     * <p>
     * All source files should have the same timepoints.
     * SetupIds are renumbered sequentially, and Channel entities are renumbered based on source order.
     *
     * @param xmlPaths Absolute paths to source XML files, in order
     * @return Combined SpimData with concatenated channels/setups
     */
    public static AbstractSpimData<?> fromChannels(List<String> xmlPaths) {
        return fromChannels(xmlPaths, DEFAULT_NUM_FETCHER_THREADS, DEFAULT_NUM_PRIORITIES);
    }

    /**
     * Creates a combined SpimData where each source file's setups are added as additional channels.
     *
     * @param xmlPaths          Absolute paths to source XML files, in order
     * @param numFetcherThreads Number of fetcher threads for the image loader
     * @param numPriorities     Number of priority levels for the cache
     * @return Combined SpimData with concatenated channels/setups
     */
    public static AbstractSpimData<?> fromChannels(List<String> xmlPaths, int numFetcherThreads, int numPriorities) {
        if (xmlPaths == null || xmlPaths.isEmpty()) {
            throw new IllegalArgumentException("At least one source path is required");
        }

        logger.info("Creating combined SpimData from {} channel files", xmlPaths.size());

        // Load all sources to get structure
        List<AbstractSpimData<?>> sources = new ArrayList<>();
        List<Integer> setupsPerSource = new ArrayList<>();

        for (String path : xmlPaths) {
            AbstractSpimData<?> source = loadSpimData(path);
            sources.add(source);
            setupsPerSource.add(source.getSequenceDescription().getViewSetupsOrdered().size());
        }

        // Create CombinedOrder
        CombinedOrder order = CombinedOrder.forChannels(xmlPaths, setupsPerSource);
        order.initialize();

        // Get timepoints from first source (assume all same)
        List<TimePoint> timePoints = copyTimePoints(sources.get(0));

        // Build unified ViewSetups with renumbered channels
        List<ViewSetup> viewSetups = buildViewSetupsForChannels(sources);

        // Build ViewRegistrations from all sources
        List<ViewRegistration> registrations = buildRegistrationsForChannels(sources, timePoints.size());

        // Create SequenceDescription
        SequenceDescription sd = new SequenceDescription(
                new TimePoints(timePoints),
                viewSetups,
                null,
                new MissingViews(new ArrayList<>())
        );

        // Create and set the ReorderedImageLoader
        ReorderedImageLoader<?> imgLoader = new ReorderedImageLoader<>(order, sd, numFetcherThreads, numPriorities);
        sd.setImgLoader(imgLoader);

        // Create and return SpimData
        SpimData spimData = new SpimData((File) null, sd, new ViewRegistrations(registrations));

        logger.info("Created combined SpimData with {} timepoints and {} setups",
                timePoints.size(), viewSetups.size());

        return spimData;
    }

    // --- Helper methods ---

    private static AbstractSpimData<?> loadSpimData(String path) {
        try {
            logger.debug("Loading SpimData from: {}", path);
            return new XmlIoSpimData().load(path);
        } catch (SpimDataException e) {
            throw new RuntimeException("Failed to load SpimData from: " + path, e);
        }
    }

    private static List<ViewSetup> copyViewSetups(AbstractSpimData<?> source) {
        List<ViewSetup> viewSetups = new ArrayList<>();

        for (BasicViewSetup bvs : source.getSequenceDescription().getViewSetupsOrdered()) {
            ViewSetup newViewSetup = new ViewSetup(
                    bvs.getId(),
                    bvs.getName(),
                    bvs.getSize(),
                    bvs.getVoxelSize(),
                    bvs.getAttribute(Channel.class),
                    bvs.getAttribute(Angle.class),
                    bvs.getAttribute(Illumination.class)
            );

            // Copy all attributes
            bvs.getAttributes().forEach((name, entity) -> newViewSetup.setAttribute(entity));

            viewSetups.add(newViewSetup);
        }

        return viewSetups;
    }

    private static List<TimePoint> copyTimePoints(AbstractSpimData<?> source) {
        List<TimePoint> timePoints = new ArrayList<>();
        for (TimePoint tp : source.getSequenceDescription().getTimePoints().getTimePointsOrdered()) {
            timePoints.add(new TimePoint(tp.getId()));
        }
        return timePoints;
    }

    private static List<ViewSetup> buildViewSetupsForChannels(List<AbstractSpimData<?>> sources) {
        List<ViewSetup> viewSetups = new ArrayList<>();
        int setupIdOffset = 0;
        int channelOffset = 0;

        // Track max channel ID from each source for proper renumbering
        Map<Integer, Channel> channelMap = new HashMap<>();

        for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
            AbstractSpimData<?> source = sources.get(sourceIndex);
            List<? extends BasicViewSetup> sourceSetups = source.getSequenceDescription().getViewSetupsOrdered();

            // Find max channel ID in this source for next offset calculation
            int maxChannelIdInSource = 0;

            for (BasicViewSetup bvs : sourceSetups) {
                int newSetupId = setupIdOffset + bvs.getId();

                // Renumber channel
                Channel originalChannel = bvs.getAttribute(Channel.class);
                int newChannelId = channelOffset + (originalChannel != null ? originalChannel.getId() : 0);

                if (originalChannel != null) {
                    maxChannelIdInSource = Math.max(maxChannelIdInSource, originalChannel.getId());
                }

                Channel newChannel = channelMap.computeIfAbsent(newChannelId,
                        id -> new Channel(id, originalChannel != null ? originalChannel.getName() : "channel " + id));

                ViewSetup newViewSetup = new ViewSetup(
                        newSetupId,
                        bvs.getName(),
                        bvs.getSize(),
                        bvs.getVoxelSize(),
                        newChannel,
                        bvs.getAttribute(Angle.class),
                        bvs.getAttribute(Illumination.class)
                );

                // Copy other attributes (except Channel which we replaced)
                bvs.getAttributes().forEach((name, entity) -> {
                    if (!(entity instanceof Channel)) {
                        newViewSetup.setAttribute(entity);
                    }
                });

                viewSetups.add(newViewSetup);
            }

            setupIdOffset += sourceSetups.size();
            channelOffset += maxChannelIdInSource + 1;
        }

        return viewSetups;
    }

    private static List<ViewRegistration> buildRegistrationsForTimepoints(
            List<AbstractSpimData<?>> sources,
            List<ViewSetup> viewSetups) {

        List<ViewRegistration> registrations = new ArrayList<>();

        for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
            AbstractSpimData<?> source = sources.get(sourceIndex);
            int newTimepoint = sourceIndex;

            // For each setup, copy the registration from the source (at timepoint 0)
            for (ViewSetup vs : viewSetups) {
                int setupId = vs.getId();
                ViewId sourceViewId = new ViewId(0, setupId);

                ViewRegistration sourceReg = source.getViewRegistrations().getViewRegistration(sourceViewId);
                if (sourceReg != null) {
                    registrations.add(new ViewRegistration(
                            newTimepoint,
                            setupId,
                            sourceReg.getModel()
                    ));
                }
            }
        }

        return registrations;
    }

    private static List<ViewRegistration> buildRegistrationsForChannels(
            List<AbstractSpimData<?>> sources,
            int numTimepoints) {

        List<ViewRegistration> registrations = new ArrayList<>();
        int setupIdOffset = 0;

        for (AbstractSpimData<?> source : sources) {
            List<? extends BasicViewSetup> sourceSetups = source.getSequenceDescription().getViewSetupsOrdered();

            for (int tp = 0; tp < numTimepoints; tp++) {
                for (BasicViewSetup bvs : sourceSetups) {
                    int sourceSetupId = bvs.getId();
                    int newSetupId = setupIdOffset + sourceSetupId;

                    ViewId sourceViewId = new ViewId(tp, sourceSetupId);
                    ViewRegistration sourceReg = source.getViewRegistrations().getViewRegistration(sourceViewId);

                    if (sourceReg != null) {
                        registrations.add(new ViewRegistration(
                                tp,
                                newSetupId,
                                sourceReg.getModel()
                        ));
                    }
                }
            }

            setupIdOffset += sourceSetups.size();
        }

        return registrations;
    }
}