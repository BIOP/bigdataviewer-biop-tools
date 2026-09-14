/*-
 * #%L
 * BigDataViewer tools and plugins for Fiji: fusion, deconvolution, registration and data processing.
 * %%
 * Copyright (C) 2018 - 2026 EPFL BIOP and University of Geneva
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
import ch.epfl.biop.command.workflow.lls7.LLS7DatasetOpenCommand;
import ch.epfl.biop.command.workflow.lls7.LLS7ZDriftCompensateCommand;
import net.imagej.ImageJ;
import org.apache.commons.io.FilenameUtils;
import sc.fiji.bdvpg.scijava.service.SourceService;

import java.io.File;

public class DemoZDriftCompensate {

    public static void main(String... args) throws Exception {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        File f = ch.epfl.biop.DatasetHelper.getDataset("https://zenodo.org/records/14903188/files/RBC_full_time_series.czi");

        ij.command().run(LLS7DatasetOpenCommand.class, true,
                "czi_file", f,
                "legacy_xy_mode", false).get();

        String datasetNameLattice = FilenameUtils.removeExtension(f.getName());

        SourceAndConverter[] sources = ij.context().getService(SourceService.class).tree().getSources(datasetNameLattice)
                .toArray(new SourceAndConverter[0]);

        ij.command().run(LLS7ZDriftCompensateCommand.class, true,
                "model_source", sources[0],
                        "sources_to_correct", sources,
                        "threshold", 225,
                        "mode", "Append",
                        "debug", true
                ).get();
    }

}

