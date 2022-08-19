package ch.epfl.biop.mastodon;

import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.mamut.MamutAppModel;
import org.mastodon.mamut.MamutMenuBuilder;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.plugin.MamutPlugin;
import org.mastodon.mamut.plugin.MamutPluginAppModel;
import org.mastodon.ui.keymap.CommandDescriptionProvider;
import org.mastodon.ui.keymap.CommandDescriptions;
import org.mastodon.ui.keymap.KeyConfigContexts;
import org.scijava.Context;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.Actions;


// Take inspiration from https://github.com/mastodon-sc/mastodon-ext-viewers for export to ImageJ
// Otherwise make a custom BdvOverlay

@Plugin( type = MamutPlugin.class )
public class MamutWarperPlugin implements MamutPlugin
{
    private static final String ACTION_CREATE_WARPED_VIEW = "[Warp dataset] warp dataset";

    private static final String[] ACTION_CREATE_WARPED_VIEW_KEYS = new String[] {"not mapped"};

    /*
     * Command descriptions for all provided commands
     */
    @Plugin( type = CommandDescriptionProvider.class )
    public static class Descriptions extends CommandDescriptionProvider
    {
        public Descriptions()
        {
            super( KeyConfigContexts.MASTODON );
        }

        @Override
        public void getCommandDescriptions( final CommandDescriptions descriptions )
        {
            descriptions.add( ACTION_CREATE_WARPED_VIEW, ACTION_CREATE_WARPED_VIEW_KEYS, "Warped dataset and detections" );
        }
    }

    @SuppressWarnings( "unused" )
    private MamutPluginAppModel appModel;

    private static Map< String, String > menuTexts = new HashMap<>();

    static
    {
        menuTexts.put( ACTION_CREATE_WARPED_VIEW, "Create a warped view of the dataset" );
    }

    @Parameter
    CommandService command;

    private final AbstractNamedAction createWarpedView = new AbstractNamedAction( ACTION_CREATE_WARPED_VIEW )
    {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed( final ActionEvent e )
        {

            command.run(MamutWarperCommand.class, true, "appModel", appModel);

        }
    };

    @Override
    public List< ViewMenuBuilder.MenuItem > getMenuItems()
    {
        return Arrays.asList(
                MamutMenuBuilder.menu( "Plugins",
                        MamutMenuBuilder.item( ACTION_CREATE_WARPED_VIEW ) ) );
    }

    @Override
    public Map< String, String > getMenuTexts()
    {
        return menuTexts;
    }

    @Override
    public void installGlobalActions( final Actions actions )
    {
        actions.namedAction(createWarpedView, ACTION_CREATE_WARPED_VIEW_KEYS );
    }

    @Override
    public void setAppPluginModel( final MamutPluginAppModel appModel )
    {
        this.appModel = appModel;
    }
}