package ch.epfl.biop.scijava;

import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ch.epfl.biop.bdv.img.bioformats.command.OpenSampleCommand;
import ch.epfl.biop.bdv.img.omero.command.CreateBdvDatasetOMEROCommand;
import ch.epfl.biop.bdv.img.qupath.command.CreateBdvDatasetQuPathCommand;
import ch.epfl.biop.kheops.command.KheopsExportSourcesCommand;
import org.apache.commons.io.FilenameUtils;
import org.scijava.Priority;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.SciJavaService;
import org.scijava.service.Service;
import sc.fiji.bdvpg.bdv.supplier.biop.BdvSetBiopViewerSettingsCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.scijava.services.ui.swingdnd.SourceAndConverterServiceUITransferHandler;

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
    SourceAndConverterService sourceAndConverterService;

    @Parameter
    CommandService cs;

    public void initialize() {
        sourceAndConverterService.registerScijavaCommand(BdvSetBiopViewerSettingsCommand.class);
        sourceAndConverterService.registerScijavaCommand(OpenSampleCommand.class);
        sourceAndConverterService.registerScijavaCommand(CreateBdvDatasetBioFormatsCommand.class);
        sourceAndConverterService.registerScijavaCommand(CreateBdvDatasetOMEROCommand.class);
        sourceAndConverterService.registerScijavaCommand(CreateBdvDatasetQuPathCommand.class);
        sourceAndConverterService.registerScijavaCommand(KheopsExportSourcesCommand.class);
        // Adds transfer handler

        BdvPlaygroundFileHandler bfHandler = new BdvPlaygroundFileHandler() {
            @Override
            public boolean acceptFile(File f) {
                return new loci.formats.ImageReader().isThisType(f.getAbsolutePath());
            }

            @Override
            public void loadFile(File f) {
                cs.run(CreateBdvDatasetBioFormatsCommand.class,true,
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
                cs.run(CreateBdvDatasetQuPathCommand.class,true,
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
        SourceAndConverterServiceUITransferHandler.addFileHandler(
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
