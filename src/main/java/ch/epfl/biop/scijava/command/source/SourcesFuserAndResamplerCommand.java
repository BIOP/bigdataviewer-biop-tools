package ch.epfl.biop.scijava.command.source;

import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceFuserAndResampler;
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

    @Parameter(choices = {AlphaFusedResampledSource.SUM, AlphaFusedResampledSource.AVERAGE})
    String blending_mode;

    @Override
    public void run() {
        // Should not be parallel
        List<SourceAndConverter> sacs_list = Arrays.asList(sacs);
        sac_out = new SourceFuserAndResampler(sacs_list, blending_mode,  model, name, reusemipmaps, cache, interpolate, defaultmipmaplevel, cache_x, cache_y, cache_z, cache_bounds, n_threads).get();
    }

}
