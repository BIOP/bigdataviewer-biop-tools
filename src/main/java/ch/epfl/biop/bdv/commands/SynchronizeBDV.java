package ch.epfl.biop.bdv.commands;

import bdv.util.BdvHandle;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.Arrays;

@Plugin(type = Command.class, menuPath = "Plugins>BIOP>BDV>Synchronize BDV windows")
public class SynchronizeBDV implements Command {

    @Parameter
    public BdvHandle hMaster;

    @Parameter
    public BdvHandle hSlave;

    @Parameter
    public int syncDelayInMs = 100;

    @Override
    public void run() {
        if (hMaster==hSlave) {
            System.err.println("BDV windows are identical : a very logical person would say that they are indeed already synchronized.");
        } else {

            Thread p = new Thread(new Runnable() {
                @Override
                public void run() {
                    System.out.println("-- Starting sync between "+hMaster+" and "+hSlave);
                    while (bdvStillHere()) {
                        try {
                            Thread.sleep(syncDelayInMs);
                            AffineTransform3D atM = new AffineTransform3D();
                            hMaster.getViewerPanel().getState().getViewerTransform(atM);
                            AffineTransform3D atS = new AffineTransform3D();
                            hSlave.getViewerPanel().getState().getViewerTransform(atS);
                            if (!Arrays.equals(atS.getRowPackedCopy(), atM.getRowPackedCopy())) {
                                hSlave.getViewerPanel().setCurrentViewerTransform(atM.copy());
                                hSlave.getViewerPanel().requestRepaint();
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    System.out.println("-- Stopping sync between "+hMaster+" and "+hSlave);
                }
            });
            p.start();
        }
    }

    boolean bdvStillHere () {
        return ((hMaster.getViewerPanel()!=null)&&(hSlave.getViewerPanel()!=null));
    }
}
