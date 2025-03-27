package ch.epfl.biop.scijava.command.source;

import bdv.util.source.alpha.AlphaSource;
import bdv.util.source.alpha.AlphaSourceDistanceL1RAI;
import bdv.util.source.alpha.AlphaSourceHelper;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceFuserAndResampler;
import ij.IJ;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import java.util.Arrays;
import java.util.List;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Fuse and Resample Sources Based on Model Source")
public class SourcesFuserAndResamplerCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Select Source(s)")
    SourceAndConverter[] sacs;

    @Parameter
    SourceAndConverter model;

    @Parameter(label="Re-use MipMaps")
    boolean reusemipmaps;

    @Parameter(label="MipMap level if not re-used (0 = max resolution)")
    int defaultmipmaplevel;

    @Parameter
    boolean interpolate;

    @Parameter
    boolean cache;

    @Parameter
    int cache_x = 64, cache_y =64, cache_z =64;

    @Parameter(label = "Number of blocks kept in memory, negative values = no bounds")
    int cache_bounds = -1;

    @Parameter
    int n_threads = 4;

    @Parameter(label="Name of the fused resampled source")
    String name; // CSV separate for multiple sources

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter sac_out;

    @Parameter(choices = {AlphaFusedResampledSource.SUM, AlphaFusedResampledSource.AVERAGE,
            "SMOOTH "+AlphaFusedResampledSource.AVERAGE,
            AlphaFusedResampledSource.MAX})

    String blending_mode;

    @Override
    public void run() {

        // Should not be parallel
        List<SourceAndConverter> sacs_list = Arrays.asList(sacs);
        if (blending_mode.equals("SMOOTH "+AlphaFusedResampledSource.AVERAGE)) {

            VoxelDimensions voxelDimensions = model.getSpimSource().getVoxelDimensions();
            String unit = voxelDimensions.unit();
            IJ.log("Units is assumed to be micrometers, and it is "+unit);
            double vox_size_x_micrometer = voxelDimensions.dimension(0);
            double vox_size_y_micrometer = voxelDimensions.dimension(1);
            double vox_size_z_micrometer = voxelDimensions.dimension(2);

            for (SourceAndConverter<?> source: sacs) {
                AlphaSource alpha = new AlphaSourceDistanceL1RAI(source.getSpimSource(),
                        (float) vox_size_x_micrometer,
                        (float) vox_size_y_micrometer,
                        (float) vox_size_z_micrometer);
                AlphaSourceHelper.setAlphaSource(source, alpha);
            }
        }

        sac_out = new SourceFuserAndResampler(sacs_list, blending_mode,  model, name, reusemipmaps, cache, interpolate, defaultmipmaplevel, cache_x, cache_y, cache_z, cache_bounds, n_threads).get();
    }

}
