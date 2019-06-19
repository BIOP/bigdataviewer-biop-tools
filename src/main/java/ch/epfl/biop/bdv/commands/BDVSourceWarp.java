package ch.epfl.biop.bdv.commands;

import bdv.img.WarpedSource;
import bdv.util.BWBdvHandle;
import bdv.util.BdvHandle;
import bdv.viewer.BigWarpViewerPanel;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, initializer = "init", menuPath = "Plugins>BIOP>BDV>Warp Source (require BigWarp windows)")
public class BDVSourceWarp extends BDVSourceFunctionalInterfaceCommand {
    @Parameter(label = "Bdv Frame from BigWarp")
    BdvHandle bdv_warp;

    //@Parameter
    //boolean reuseMipMaps;

    public BDVSourceWarp() {
        this.f = src -> {
            /*new ResampledSource(
                    src,
                    bdv_dst.getViewerPanel()
                            .getState()
                            .getSources()
                            .get(idxSourceDst)
                            .getSpimSource(),
                    reuseMipMaps);*/
            if (bdv_warp instanceof BWBdvHandle) {
                /*if (bdv_warp instanceof BWBdvHandle) {
                    BWBdvHandle bdv = (BWBdvHandle) bdv_warp;
                    bdv.getBW().getTransform():
                    WarpedSource<?> srcW = new WarpedSource<>(src, src.getName()+"_warped");
                    srcW.updateTransform(bdv.getBW().getTransform().get);

                }
                BigWarpViewerPanel bwPanel = (BigWarpViewerPanel) bdv_warp.getViewerPanel();*/
                //bwPanel.
                //((BigWarpViewerFrame) bwPanel.getParent())
            }
            return null;
        };
    }
}
