package bdv.util;

import bdv.tools.brightness.ConverterSetup;
import bdv.tools.transformation.ManualTransformationEditor;
import bdv.viewer.SourceAndConverter;
import bigwarp.BigWarp;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import java.util.List;

public class BWBdvHandle extends BdvHandle {

    BigWarp bw;

    boolean moving;

    public BWBdvHandle(BigWarp bw, boolean moving ) {
        super(BdvOptions.options());
        this.moving=moving;
        this.bw = bw;
        if (moving) {
            this.viewer = bw.getViewerFrameP().getViewerPanel();

        } else {
            this.viewer = bw.getViewerFrameQ().getViewerPanel();
        }
    }

    @Override
    public void close() {
        bw.closeAll();
    }

    @Override
    public ManualTransformationEditor getManualTransformEditor() {
        System.err.println("Cannot edit transformation in BigWarp BdvHandle");
        return null;
    }

    @Override
    public InputActionBindings getKeybindings() {
        System.err.println("Cannot get keyBindings in BigWarp BdvHandle");
        return null;
    }

    @Override
    public TriggerBehaviourBindings getTriggerbindings() {
        System.err.println("Cannot get TriggerBindings in BigWarp BdvHandle");
        return null;
    }

    boolean createViewer(
            List< ? extends ConverterSetup> converterSetups,
            List< ? extends SourceAndConverter< ? >> sources,
            int numTimepoints ) {
        System.err.println("Cannot add sources in BigWarp BdvHandle");
        return false;
    }

    @Override
    public String toString() {
        if (moving) {
            return "BigWarp Moving Frame";
        } else {
            return "BigWarp Fixed Frame";
        }
    }

}