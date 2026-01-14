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
package ch.epfl.biop.scijava.command.spimdata;

import ch.epfl.biop.spimdata.combined.CombinedSpimData;
import ij.IJ;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command to combine multiple BDV XML datasets into a single dataset.
 * <p>
 * Supports two modes:
 * <ul>
 *   <li><b>Concatenate Timepoints</b>: Each input file becomes a separate timepoint.
 *       Useful for combining multiple single-timepoint acquisitions (e.g., CZI files)
 *       into a timelapse.</li>
 *   <li><b>Concatenate Channels</b>: Each input file's setups are added as additional
 *       channels. Useful for combining different acquisitions as separate channels.</li>
 * </ul>
 */
@Plugin(type = Command.class,
        menuPath = ScijavaBdvDefaults.RootMenu + "BDVDataset>Combine BDV Datasets",
        description = "Combines multiple BDV XML datasets into a single dataset by concatenating timepoints or channels")
public class CombineSpimDataCommand implements Command {

    /**
     * Mode for combining datasets.
     */
    public enum CombineMode {
        /**
         * Each input file becomes a separate timepoint.
         */
        CONCATENATE_TIMEPOINTS("Concatenate Timepoints"),

        /**
         * Each input file's setups are added as additional channels.
         */
        CONCATENATE_CHANNELS("Concatenate Channels");

        private final String displayName;

        CombineMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    @Parameter(label = "Input XML Files",
            description = "The BDV XML files to combine. Order matters: for timepoints, files are ordered temporally; for channels, files contribute channels in order.",
            style = "open, extensions:xml")
    File[] input_files;

    @Parameter(label = "Combine Mode",
            description = "How to combine the datasets: as separate timepoints or as separate channels")
    CombineMode combine_mode = CombineMode.CONCATENATE_TIMEPOINTS;

    @Parameter(label = "Dataset Name",
            description = "Name for the resulting BDV dataset.")
    public String datasetname = "dataset";

    @Parameter(type = ItemIO.OUTPUT, label = "Output BDV dataset object")
    AbstractSpimData<?> combinedSpimData;

    @Override
    public void run() {
        if (input_files == null || input_files.length == 0) {
            IJ.error("No input files selected");
            return;
        }

        if (input_files.length < 2) {
            IJ.error("At least 2 input files are required to combine");
            return;
        }

        // Convert File[] to List<String> of absolute paths
        List<String> xmlPaths = Arrays.stream(input_files)
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());

        IJ.log("Combining " + xmlPaths.size() + " datasets in mode: " + combine_mode);

        try {

            switch (combine_mode) {
                case CONCATENATE_TIMEPOINTS:
                    combinedSpimData = CombinedSpimData.fromTimepoints(xmlPaths);
                    break;
                case CONCATENATE_CHANNELS:
                    combinedSpimData = CombinedSpimData.fromChannels(xmlPaths);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown combine mode: " + combine_mode);
            }

            IJ.log("Successfully created combined dataset: " + datasetname);
            IJ.log("  - Input files: " + xmlPaths.size());
            IJ.log("  - Mode: " + combine_mode);

        } catch (Exception e) {
            IJ.error("Failed to combine datasets: " + e.getMessage());
            e.printStackTrace();
        }
    }
}