/*-
 * #%L
 * BigDataViewer tools and plugins for Fiji: fusion, deconvolution, registration and data processing.
 * %%
 * Copyright (C) 2018 - 2026 EPFL BIOP and University of Geneva
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package ch.epfl.biop.command.workflow.mastodon;

import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.mamut.KeyConfigScopes;
import org.mastodon.mamut.MamutMenuBuilder;
import org.mastodon.mamut.ProjectModel;
import org.mastodon.mamut.plugin.MamutPlugin;
import org.mastodon.ui.keymap.KeyConfigContexts;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionProvider;
import org.scijava.ui.behaviour.io.gui.CommandDescriptions;
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
    @Plugin( type = Descriptions.class )
    public static class Descriptions extends CommandDescriptionProvider
    {
        public Descriptions()
        {
            super( KeyConfigScopes.MAMUT, KeyConfigContexts.MASTODON );
        }

        @Override
        public void getCommandDescriptions( final CommandDescriptions descriptions )
        {
            descriptions.add(ACTION_WARP_SPOTS, ACTION_WARP_SPOTS_KEYS, "Warp spots" );
        }
    }

    @SuppressWarnings( "unused" )
    private ProjectModel appModel;

    private static final Map< String, String > menuTexts = new HashMap<>();

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
            command.run(MamutWarperCommand.class, true, "app_model", appModel);
        }
    };

    @Override
    public List< ViewMenuBuilder.MenuItem > getMenuItems()
    {
        return Collections.singletonList(
                MamutMenuBuilder.menu("Plugins",
                        MamutMenuBuilder.item(ACTION_WARP_SPOTS)));
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
    public void setAppPluginModel( final ProjectModel appModel )
    {
        this.appModel = appModel;
    }
}
