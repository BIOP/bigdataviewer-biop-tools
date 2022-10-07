package ch.epfl.biop.mastodon;

import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.mamut.MamutMenuBuilder;
import org.mastodon.mamut.plugin.MamutPlugin;
import org.mastodon.mamut.plugin.MamutPluginAppModel;
import org.mastodon.ui.keymap.CommandDescriptionProvider;
import org.mastodon.ui.keymap.CommandDescriptions;
import org.mastodon.ui.keymap.KeyConfigContexts;
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
    private static final String ACTION_WARP_SPOTS = "[Warp spots] warp spots";

    private static final String[] ACTION_WARP_SPOTS_KEYS = new String[] {"not mapped"};

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
            descriptions.add(ACTION_WARP_SPOTS, ACTION_WARP_SPOTS_KEYS, "Warp spots" );
        }
    }

    @SuppressWarnings( "unused" )
    private MamutPluginAppModel appModel;

    private static Map< String, String > menuTexts = new HashMap<>();

    static
    {
        menuTexts.put(ACTION_WARP_SPOTS, "Warp spots according to a json elliptical transform file" );
    }

    @Parameter
    CommandService command;

    private final AbstractNamedAction warpSpots = new AbstractNamedAction(ACTION_WARP_SPOTS)
    {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed( final ActionEvent e ) {
            command.run(MamutWarperCommand.class, true, "appModel", appModel);
        }
    };

    @Override
    public List< ViewMenuBuilder.MenuItem > getMenuItems()
    {
        return Arrays.asList(
                MamutMenuBuilder.menu( "Plugins",
                        MamutMenuBuilder.item(ACTION_WARP_SPOTS) ) );
    }

    @Override
    public Map< String, String > getMenuTexts()
    {
        return menuTexts;
    }

    @Override
    public void installGlobalActions( final Actions actions )
    {
        actions.namedAction(warpSpots, ACTION_WARP_SPOTS_KEYS);
    }

    @Override
    public void setAppPluginModel( final MamutPluginAppModel appModel )
    {
        this.appModel = appModel;
    }
}