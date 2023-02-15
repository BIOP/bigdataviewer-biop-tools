package ch.epfl.biop.scijava;

import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ch.epfl.biop.bdv.img.bioformats.command.OpenSampleCommand;
import ch.epfl.biop.bdv.img.omero.command.CreateBdvDatasetOMEROCommand;
import ch.epfl.biop.bdv.img.qupath.command.CreateBdvDatasetQuPathCommand;
import ch.epfl.biop.kheops.command.KheopsExportSourcesCommand;
import org.scijava.Priority;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.SciJavaService;
import org.scijava.service.Service;
import sc.fiji.bdvpg.bdv.supplier.biop.BdvSetBiopViewerSettingsCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;

/**
 * The only reason this service exist is to register some Commands, which
 * are not BdvPlaygroundCommand, but which we'd like to have in the contextual menu
 * accessible in bigdataviewer playground tree view
 */
@Plugin(type = Service.class, headless = true, priority = Priority.EXTREMELY_LOW)
public class RegisterExtraCommandService extends AbstractService implements
        SciJavaService {

    @Parameter
    SourceAndConverterService sourceAndConverterService;

    public void initialize() {
        sourceAndConverterService.registerScijavaCommand(BdvSetBiopViewerSettingsCommand.class);
        sourceAndConverterService.registerScijavaCommand(OpenSampleCommand.class);
        sourceAndConverterService.registerScijavaCommand(CreateBdvDatasetBioFormatsCommand.class);
        sourceAndConverterService.registerScijavaCommand(CreateBdvDatasetOMEROCommand.class);
        sourceAndConverterService.registerScijavaCommand(CreateBdvDatasetQuPathCommand.class);
        sourceAndConverterService.registerScijavaCommand(KheopsExportSourcesCommand.class);
    }
}
