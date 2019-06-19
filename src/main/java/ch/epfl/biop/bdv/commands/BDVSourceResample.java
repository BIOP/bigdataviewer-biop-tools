package ch.epfl.biop.bdv.commands;

import bdv.util.BdvHandle;
import ch.epfl.biop.bdv.process.ResampledSource;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;


@Plugin(type = Command.class, initializer = "init", menuPath = "Plugins>BIOP>BDV>Resample Source")
public class BDVSourceResample extends BDVSourceFunctionalInterfaceCommand {

    @Parameter(label = "Bdv Frame containing source resampling template")
    BdvHandle bdv_dst;

    @Parameter(label = "Index of the source resampling template")
    int idxSourceDst;

    @Parameter
    boolean reuseMipMaps;

    public BDVSourceResample() {
        this.f = src -> new ResampledSource(
                            src,
                            bdv_dst.getViewerPanel()
                                   .getState()
                                   .getSources()
                                   .get(idxSourceDst)
                                   .getSpimSource(),
                            reuseMipMaps);
    }

}
