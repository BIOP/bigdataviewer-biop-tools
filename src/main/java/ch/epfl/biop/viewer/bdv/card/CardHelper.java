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
package ch.epfl.biop.viewer.bdv.card;

import bdv.ui.splitpanel.SplitPanel;
import bdv.util.BdvHandle;

import javax.swing.SwingUtilities;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.HashMap;
import java.util.Map;

import static bdv.ui.BdvDefaultCards.*;
import static bdv.ui.BdvDefaultCards.DEFAULT_SOURCES_CARD;

/**
 * Some common functions to deal with cards in BigDataViewer
 */
public class CardHelper {

    public static CardState getCardState(BdvHandle bdvh) {
        CardState cs = new CardState();
        cs.iniSplitPanelState = bdvh.getSplitPanel().isCollapsed();
        cs.iniCardState.put(DEFAULT_SOURCEGROUPS_CARD, bdvh.getCardPanel().isCardExpanded(DEFAULT_SOURCEGROUPS_CARD));
        cs.iniCardState.put(DEFAULT_VIEWERMODES_CARD, bdvh.getCardPanel().isCardExpanded(DEFAULT_VIEWERMODES_CARD));
        cs.iniCardState.put(DEFAULT_SOURCES_CARD, bdvh.getCardPanel().isCardExpanded(DEFAULT_SOURCES_CARD));
        return cs;
    }

    public static void restoreCardState(BdvHandle bdvh, CardState cs) {
        bdvh.getSplitPanel().setCollapsed(cs.iniSplitPanelState);
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCEGROUPS_CARD, cs.iniCardState.get(DEFAULT_SOURCEGROUPS_CARD));
        bdvh.getCardPanel().setCardExpanded(DEFAULT_VIEWERMODES_CARD, cs.iniCardState.get(DEFAULT_VIEWERMODES_CARD));
        bdvh.getCardPanel().setCardExpanded(DEFAULT_SOURCES_CARD, cs.iniCardState.get(DEFAULT_SOURCES_CARD));
    }

    /**
     * Makes the card panel of a window visible, with a usable width.
     * <p>
     * This cannot be done while the window is being built: {@code SplitPanel.setCollapsed(false)}
     * places the divider from the current width of the panel, which is still zero as long as the
     * frame has not been laid out. The panel then ends up expanded but with a zero width - it
     * simply looks absent. The expansion is therefore deferred to the first layout pass, and the
     * divider is placed explicitly rather than left to the default.
     *
     * @param bdvh  the viewer whose card panel must be shown
     * @param width width to give the card panel, capped to half of the window
     */
    public static void expandCardPanel(BdvHandle bdvh, int width) {
        final SplitPanel splitPanel = bdvh.getSplitPanel();

        final Runnable expand = () -> {
            int panelWidth = splitPanel.getWidth();
            if (panelWidth <= 0) return;
            // setCollapsed is a no-op if the panel is already expanded: collapse it first so that
            // the divider is recomputed
            splitPanel.setCollapsed(true);
            splitPanel.setCollapsed(false);
            splitPanel.setDividerLocation(Math.max(panelWidth / 2, panelWidth - width));
        };

        SwingUtilities.invokeLater(() -> {
            if (splitPanel.getWidth() > 0) {
                expand.run();
            } else {
                // The frame has not been laid out yet: wait for it
                splitPanel.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent e) {
                        if (splitPanel.getWidth() <= 0) return;
                        splitPanel.removeComponentListener(this);
                        expand.run();
                    }
                });
            }
        });
    }

    public static class CardState {
        boolean iniSplitPanelState;
        final Map<String, Boolean> iniCardState = new HashMap<>();
    }
}
