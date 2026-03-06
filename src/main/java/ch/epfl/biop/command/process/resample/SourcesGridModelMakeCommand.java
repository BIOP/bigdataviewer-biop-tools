package ch.epfl.biop.command.process.resample;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.SourceHelper;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = BdvPgMenus.RootMenu+"Process>Fuse & Resample>Source - Define Resampling Grid",
        description = "Creates an empty model source that spans the bounding box of multiple sources with custom voxel size")
public class SourcesGridModelMakeCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Source(s)",
            description = "The sources whose combined bounding box defines the model extent")
    SourceAndConverter<?>[] sources;

    @Parameter(label = "Voxel Size X",
            description = "Output voxel size in X dimension")
    double vox_size_x;

    @Parameter(label = "Voxel Size Y",
            description = "Output voxel size in Y dimension")
    double vox_size_y;

    @Parameter(label = "Voxel Size Z",
            description = "Output voxel size in Z dimension")
    double vox_size_z;

    @Parameter(label = "Model Timepoint",
            description = "Reference timepoint used to compute the bounding box")
    int timepoint = 0;

    @Parameter(label = "Number of Timepoints",
            description = "Number of timepoints in the model source")
    int n_timepoints = 1;

    @Parameter(label = "Resolution Levels",
            description = "Number of pyramid resolution levels to create")
    int n_resolution_levels = 1;

    @Parameter(label = "X Downscale Factor",
            description = "Downscaling factor between resolution levels in X")
    int downscale_x = 1;

    @Parameter(label = "Y Downscale Factor",
            description = "Downscaling factor between resolution levels in Y")
    int downscale_y = 1;

    @Parameter(label = "Z Downscale Factor",
            description = "Downscaling factor between resolution levels in Z")
    int downscale_z = 1;

    @Parameter(label = "Model Name",
            description = "Name for the model source")
    String name; // CSV separate for multiple sources

    @Parameter(type = ItemIO.OUTPUT,
            description = "The resulting model source spanning all input sources")
    SourceAndConverter<?> source_out;

    @Override
    public void run() {
        source_out = SourceHelper.getModelFusedMultiSources(sources,
                timepoint, n_timepoints,
                vox_size_x, vox_size_y, vox_size_z,
                n_resolution_levels,
                downscale_x,downscale_y,downscale_z,name);
    }

}
