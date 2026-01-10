package ch.epfl.biop.labkit;
/*-
 * #%L
 * The Labkit image segmentation tool for Fiji.
 * %%
 * Copyright (C) 2017 - 2024 Matthias Arzt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.DefaultLinearAxis;
import net.imagej.axis.IdentityAxis;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.util.Cast;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Converts {@link AbstractSpimData} to {@link ImgPlus}.
 * <p>
 * Helper to {@link SourcesInputImage}.
 */
public class SourcesToImgPlus {

    public static <T> ImgPlus<T> wrap(SourceAndConverter<T>[] sources, String name, Integer resolutionLevel) {

        // TODO checkSetupsMatch(sources);
        Img<?> img = ImgView.wrap(Cast.unchecked(asRai(sources, resolutionLevel)),
                Cast.unchecked(new CellImgFactory()));
        CalibratedAxis[] axes = getAxes(sources, resolutionLevel);
        return new ImgPlus(img, name, axes);
    }

    /**
     * Wraps sources at a specific timepoint as an ImgPlus (no time dimension).
     *
     * @param sources the input sources
     * @param name the name for the ImgPlus
     * @param resolutionLevel the resolution level to use
     * @param timepoint the specific timepoint to use
     * @param <T> the pixel type
     * @return an ImgPlus for the given timepoint
     */
    public static <T> ImgPlus<T> wrap(SourceAndConverter<T>[] sources, String name, int resolutionLevel, int timepoint) {
        Img<?> img = ImgView.wrap(Cast.unchecked(asRaiSingleTimepoint(sources, resolutionLevel, timepoint)),
                Cast.unchecked(new CellImgFactory()));
        CalibratedAxis[] axes = getAxesSingleTimepoint(sources, resolutionLevel);
        return new ImgPlus(img, name, axes);
    }

    private static <T> RandomAccessibleInterval<?> asRaiSingleTimepoint(SourceAndConverter<T>[] sources, int level, int timepoint) {
        return dropThirdDimension(combineChannels(Arrays.asList(sources), new TimePoint(timepoint), level));
    }

    private static CalibratedAxis[] getAxesSingleTimepoint(SourceAndConverter[] sources, int resolutionLevel) {
        VoxelDimensions voxelSize = sources[0].getSpimSource().getVoxelDimensions();
        List<CalibratedAxis> list = new ArrayList<>();
        list.add(new DefaultLinearAxis(Axes.X, voxelSize.unit(), voxelSize.dimension(0)));
        list.add(new DefaultLinearAxis(Axes.Y, voxelSize.unit(), voxelSize.dimension(1)));
        if (is3d(sources, resolutionLevel))
            list.add(new DefaultLinearAxis(Axes.Z, voxelSize.unit(), voxelSize.dimension(2)));
        if (sources.length > 1)
            list.add(new IdentityAxis(Axes.CHANNEL));
        // No TIME axis for single timepoint
        return list.toArray(new CalibratedAxis[0]);
    }

    private static <T> RandomAccessibleInterval<?> asRai(SourceAndConverter[] sources,
                                                         Integer level)
    {
        int nTps = SourceAndConverterHelper.getMaxTimepoint(sources) + 1;
        List<TimePoint> timePoints = new ArrayList<>();
        for (int t = 0; t<nTps; t++) {
            timePoints.add(new TimePoint(t));
        }
        return dropThirdDimension(combineFrames(Arrays.asList(sources), timePoints, level));
    }

    private static RandomAccessibleInterval<?> dropThirdDimension(
            RandomAccessibleInterval<?> image)
    {
        return image.dimension(2) != 1 ? image : Views.hyperSlice(image, 2, 0);
    }

    private static <T> RandomAccessibleInterval<T> combineFrames(
            List<SourceAndConverter<T>> sources, List<TimePoint> timePoints, int level)
    {
        List<RandomAccessibleInterval<T>> frames = timePoints.stream()
                .map(t -> combineChannels(sources, t, level))
                .collect(Collectors.toList());
        return frames.size() > 1 ? Views.stack(frames) : frames.get(0);
    }

    private static <T> RandomAccessibleInterval<T> combineChannels(
            List<SourceAndConverter<T>> sources, TimePoint timePoint, int level)
    {
        List<RandomAccessibleInterval<T>> channels = sources.stream()
                .map(source -> (RandomAccessibleInterval<T>) source.getSpimSource().getSource(timePoint.getId(), level))
                .collect(Collectors.toList());
        return channels.size() > 1 ? Views.stack(channels) : channels.get(0);
    }

    private static CalibratedAxis[] getAxes(SourceAndConverter[] sources, int resolutionLevel) {
        VoxelDimensions voxelSize = sources[0].getSpimSource().getVoxelDimensions();
        List<CalibratedAxis> list = new ArrayList<>();
        list.add(new DefaultLinearAxis(Axes.X, voxelSize.unit(), voxelSize.dimension(0)));
        list.add(new DefaultLinearAxis(Axes.Y, voxelSize.unit(), voxelSize.dimension(1)));
        if (is3d(sources, resolutionLevel))
            list.add(new DefaultLinearAxis(Axes.Z, voxelSize.unit(), voxelSize.dimension(2)));
        if (sources.length> 1)
            list.add(new IdentityAxis(Axes.CHANNEL));
        if (SourceAndConverterHelper.getMaxTimepoint(sources) > 0)
            list.add(new DefaultLinearAxis(Axes.TIME));
        return list.toArray(new CalibratedAxis[0]);
    }

    private static boolean is3d(SourceAndConverter[] sources, int resolutionLevel) {
        return sources[0].getSpimSource().getSource(0, resolutionLevel).dimension(
                2) > 1;
    }

}

