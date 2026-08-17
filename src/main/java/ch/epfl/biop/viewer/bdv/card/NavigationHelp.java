package ch.epfl.biop.viewer.bdv.card;

import bdv.KeyConfigContexts;
import bdv.TransformEventHandler2D;
import bdv.TransformEventHandler3D;
import bdv.util.BdvHandle;
import org.scijava.ui.behaviour.InputTrigger;
import sc.fiji.bdvpg.viewer.bdv.config.BdvKeymapHelper;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Describes in plain words the mouse gestures which navigate a BigDataViewer window, to put
 * them in a card of a window whose users are not expected to know BigDataViewer already.
 * <p>
 * The gestures are not the same in every window: the bindings are user configurable - the
 * keymap page of the BDV preferences, bound to ctrl COMMA, edits them - and the BIOP keymap
 * differs from the BigDataViewer defaults. They are therefore read from the keymap of the
 * window rather than hardcoded.
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL
 */
public class NavigationHelp {

    /**
     * Describes the gestures which translate, rotate and zoom a window.
     *
     * @param bdvh the window whose keymap is read
     * @return an HTML fragment, one line per gesture, without the enclosing html tag
     */
    public static String html(BdvHandle bdvh) {
        // The 2D and the 3D transform handlers have different commands and different bindings
        final boolean is2D = bdvh.getViewerPanel()
                .getTransformEventHandler() instanceof TransformEventHandler2D;

        final String translate = is2D
                ? triggers(bdvh, TransformEventHandler2D.DRAG_TRANSLATE,
                TransformEventHandler2D.DRAG_TRANSLATE_KEYS, " drag")
                : triggers(bdvh, TransformEventHandler3D.DRAG_TRANSLATE,
                TransformEventHandler3D.DRAG_TRANSLATE_KEYS, " drag");

        final String rotate = is2D
                ? triggers(bdvh, TransformEventHandler2D.DRAG_ROTATE,
                TransformEventHandler2D.DRAG_ROTATE_KEYS, " drag")
                : triggers(bdvh, TransformEventHandler3D.DRAG_ROTATE,
                TransformEventHandler3D.DRAG_ROTATE_KEYS, " drag");

        final String zoom = is2D
                ? triggers(bdvh, TransformEventHandler2D.ZOOM_NORMAL,
                TransformEventHandler2D.ZOOM_NORMAL_KEYS, "")
                : triggers(bdvh, TransformEventHandler3D.ZOOM_NORMAL,
                TransformEventHandler3D.ZOOM_NORMAL_KEYS, "");

        return "<b>" + translate + "</b> &ndash; translate<br>" +
                "<b>" + rotate + "</b> &ndash; rotate<br>" +
                "<b>" + zoom + "</b> &ndash; zoom";
    }

    /**
     * Tells which mouse or key triggers a command is bound to in a window, as a short readable
     * string - 'Left click drag', 'Mouse wheel'.
     * <p>
     * Every trigger the command is bound to is listed: the alternatives are gestures a user may
     * well be used to rather than variants of one another, the BIOP keymap for instance
     * rotating on a middle drag and on a shift left drag alike.
     *
     * @param bdvh        the window whose keymap is read
     * @param commandName name of the command, see {@link TransformEventHandler2D}
     * @param defaults    triggers to fall back on when the keymap does not mention the command
     * @param suffix      appended to each trigger, ' drag' for the dragging commands
     * @return the triggers, separated by ' or ', or an empty string if the command is unbound
     */
    public static String triggers(BdvHandle bdvh, String commandName, String[] defaults,
                                  String suffix) {

        Set<InputTrigger> bound = BdvKeymapHelper.getConfig(bdvh)
                .getInputs(commandName, KeyConfigContexts.BIGDATAVIEWER);

        Stream<String> names = bound.isEmpty()
                ? Arrays.stream(defaults)
                : bound.stream().map(InputTrigger::toString);

        String text = names
                .filter(trigger -> !NOT_MAPPED.equals(trigger))
                .map(trigger -> MODIFIER.matcher(trigger).replaceAll("$1 + ")
                        .replace("button1", "left click")
                        .replace("button2", "middle click")
                        .replace("button3", "right click")
                        .replace("scroll", "mouse wheel") + suffix)
                .collect(Collectors.joining(" or "));

        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Modifier keys, as {@link InputTrigger#toString()} writes them - 'shift button1'. The
     * trailing space is part of the match so that the replacement spells them out as
     * 'shift + button1'.
     */
    private static final Pattern MODIFIER = Pattern.compile("\\b(shift|ctrl|meta|alt)\\b ");

    /** How {@link InputTrigger#toString()} spells a binding which is deliberately blocked */
    private static final String NOT_MAPPED = "not mapped";

}
