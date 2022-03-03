package ch.epfl.biop.bdv.gui.card;

import bdv.util.BdvHandle;

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

    public static class CardState {
        boolean iniSplitPanelState;
        Map<String, Boolean> iniCardState = new HashMap<>();
    }
}
