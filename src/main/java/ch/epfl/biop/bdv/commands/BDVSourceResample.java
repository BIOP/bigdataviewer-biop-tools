package ch.epfl.biop.bdv.commands;

import bdv.util.BdvHandle;
import ch.epfl.biop.bdv.process.ResampledSource;
import ch.epfl.biop.bdv.scijava.command.BDVSourceFunctionalInterfaceCommand;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;


@Plugin(type = Command.class, initializer = "init", menuPath = ScijavaBdvRootMenu+"Transformation>Resample Source")
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
