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
package ch.epfl.biop.scijava;

import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.bdv.img.bioformats.command.OpenSampleCommand;
import ch.epfl.biop.bdv.img.omero.command.DatasetFromOMEROCreateCommand;
import ch.epfl.biop.bdv.img.qupath.command.DatasetFromQuPathCreateCommand;
import ch.epfl.biop.kheops.command.KheopsExportSourcesCommand;
import org.apache.commons.io.FilenameUtils;
import org.scijava.Priority;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.SciJavaService;
import org.scijava.service.Service;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.scijava.service.tree.swingdnd.SourceServiceTreeTransferHandler;

import java.io.File;

/**
 * The only reason this service exist is to register some Commands, which
 * are not BdvPlaygroundCommand, but which we'd like to have in the contextual menu
 * accessible in bigdataviewer playground tree view
 */
@Plugin(type = Service.class, headless = true, priority = Priority.EXTREMELY_LOW)
public class RegisterBdvPlaygroundExtrasService extends AbstractService implements
        SciJavaService {

    @Parameter
    SourceService SourceService;

    @Parameter
    CommandService cs;

    public void initialize() {
        SourceService.registerScijavaCommand(OpenSampleCommand.class);
        SourceService.registerScijavaCommand(DatasetFromBioFormatsCreateCommand.class);
        SourceService.registerScijavaCommand(DatasetFromOMEROCreateCommand.class);
        SourceService.registerScijavaCommand(DatasetFromQuPathCreateCommand.class);
        SourceService.registerScijavaCommand(KheopsExportSourcesCommand.class);
        // Adds transfer handler

        BdvPlaygroundFileHandler bfHandler = new BdvPlaygroundFileHandler() {
            @Override
            public boolean acceptFile(File f) {
                return new loci.formats.ImageReader().isThisType(f.getAbsolutePath());
            }

            @Override
            public void loadFile(File f) {
                cs.run(DatasetFromBioFormatsCreateCommand.class,true,
                        "files", new File[]{f},
                            "datasetname", f.getName()
                        );
            }

            @Override
            public int getPriority() {
                return 0;
            }
        };
        addFileHandler(bfHandler);

        BdvPlaygroundFileHandler qpHandler = new BdvPlaygroundFileHandler() {
            @Override
            public boolean acceptFile(File f) {
                return FilenameUtils.getExtension(f.getAbsolutePath()).equals("qpproj");
            }

            @Override
            public void loadFile(File f) {
                cs.run(DatasetFromQuPathCreateCommand.class,true,
                        "qupath_project", f,
                        "datasetname", ""
                );
            }

            @Override
            public int getPriority() {
                return 1;
            }
        };
        addFileHandler(qpHandler);

    }

    private void addFileHandler(BdvPlaygroundFileHandler handler) {
        SourceServiceTreeTransferHandler.addFileHandler(
                handler.getPriority(),
                handler::acceptFile,
                handler::loadFile);
    }

    public interface BdvPlaygroundFileHandler {
        boolean acceptFile(File f);
        void loadFile(File f);
        int getPriority();
    }
}
