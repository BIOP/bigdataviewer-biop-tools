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
import sc.fiji.bdvpg.bdv.supplier.biop.BdvSetBiopViewerSettingsCommand;
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
