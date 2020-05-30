package ch.epfl.biop.scijava.command;

import bdv.util.BdvHandle;
import ch.epfl.biop.bdv.edit.SourceSelectorBehaviour;
import ch.epfl.biop.bdv.edit.ToggleListener;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;


@Plugin(type = Command.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Bdv>Edit Sources")
public class BdvAddSourceEditorCommand implements Command {

    @Parameter
    BdvHandle bdvh;

    @Override
    public void run() {
        SourceSelectorBehaviour ssb = new SourceSelectorBehaviour(bdvh, "E");

        Behaviours editor = new Behaviours(new InputTriggerConfig());

        ClickBehaviour delete = (x, y) -> bdvh.getViewerPanel().state().removeSources(ssb.getSourceSelectorOverlay().getSelectedSources());

        editor.behaviour(delete, "remove-sources-from-bdv", new String[]{"DELETE"});

        ssb.addToggleListener(new ToggleListener() {
            @Override
            public void enable() {
                editor.install(bdvh.getTriggerbindings(), "sources-editor");
            }

            @Override
            public void disable() {
                bdvh.getTriggerbindings().removeInputTriggerMap("sources-editor");
                bdvh.getTriggerbindings().removeBehaviourMap("sources-editor");
            }
        });

        ssb.enable();
    }
}
