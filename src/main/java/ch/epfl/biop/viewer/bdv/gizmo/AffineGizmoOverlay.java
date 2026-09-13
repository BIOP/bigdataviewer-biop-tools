package ch.epfl.biop.viewer.bdv.gizmo;

import bdv.util.BdvHandle;
import bdv.util.BdvOverlay;
import ch.epfl.biop.viewer.bdv.gizmo.AffineGizmo.Handle;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ui.behaviour.BehaviourMap;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.InputTrigger;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Map;
import java.util.Set;

/**
 * Draws an {@link AffineGizmo} on a BigDataViewer window and lets the user drag its handles with the left button,
 * shift to constrain the drag. The left button is taken over only while a handle is hovered, so navigating the
 * window works everywhere else.
 */
public class AffineGizmoOverlay extends BdvOverlay {

    private static final String GIZMO = "affine_gizmo";

    /** Id BigDataViewer installs its navigation behaviours under */
    private static final String BDV_TRANSFORM = "transform";

    private static final InputTrigger LEFT_BUTTON = InputTrigger.getFromString("button1");

    private static final InputTrigger SHIFT_LEFT_BUTTON = InputTrigger.getFromString("shift button1");

    /** Distance, in screen pixels, under which a handle is grabbed */
    private static final int GRAB_RADIUS = 9;

    private static final int HANDLE_RADIUS = 5;

    private static final Color X_COLOR = new Color(255, 54, 83), Y_COLOR = new Color(118, 178, 23),
            CENTER_COLOR = Color.WHITE, SIMILARITY_COLOR = new Color(255, 200, 40);

    private final BdvHandle bdvh;

    private final AffineGizmo gizmo;

    private final Runnable onChange;

    private final BehaviourMap behaviours = new BehaviourMap();

    private volatile Handle hovered;

    /**
     * @param bdvh the window the gizmo is shown in, see {@link #install()}
     * @param gizmo the gizmo edited
     * @param onChange called on the event dispatch thread each time a drag changes the transform
     */
    public AffineGizmoOverlay(BdvHandle bdvh, AffineGizmo gizmo, Runnable onChange) {
        this.bdvh = bdvh;
        this.gizmo = gizmo;
        this.onChange = onChange;
        behaviours.put(GIZMO, new HandleDrag(false));
        behaviours.put(GIZMO + "_constrained", new HandleDrag(true));
    }

    /**
     * Starts tracking the mouse: to be called once the overlay is added to the window
     */
    public void install() {
        bdvh.getViewerPanel().getDisplay().addHandler(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHovered(handleAt(e.getX(), e.getY()));
            }
        });
    }

    @Override
    protected void draw(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double[] o = screen(Handle.CENTER), x = screen(Handle.X_AXIS), y = screen(Handle.Y_AXIS), s = screen(Handle.SIMILARITY);

        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
        g.setColor(SIMILARITY_COLOR);
        line(g, x, s);
        line(g, y, s);

        g.setStroke(new BasicStroke(2.5f));
        g.setColor(X_COLOR);
        line(g, o, x);
        g.setColor(Y_COLOR);
        line(g, o, y);

        handle(g, Handle.CENTER, o, CENTER_COLOR);
        handle(g, Handle.X_AXIS, x, X_COLOR);
        handle(g, Handle.Y_AXIS, y, Y_COLOR);
        handle(g, Handle.SIMILARITY, s, SIMILARITY_COLOR);
    }

    private void handle(Graphics2D g, Handle handle, double[] p, Color color) {
        int r = (handle == hovered) ? GRAB_RADIUS : HANDLE_RADIUS;
        g.setColor(color);
        g.fillOval((int) Math.round(p[0]) - r, (int) Math.round(p[1]) - r, 2 * r, 2 * r);
        g.setStroke(new BasicStroke(1f));
        g.setColor(Color.BLACK);
        g.drawOval((int) Math.round(p[0]) - r, (int) Math.round(p[1]) - r, 2 * r, 2 * r);
    }

    private static void line(Graphics2D g, double[] a, double[] b) {
        g.drawLine((int) Math.round(a[0]), (int) Math.round(a[1]), (int) Math.round(b[0]), (int) Math.round(b[1]));
    }

    /** @return the handle closest to the screen position, within the grab radius, or null */
    private Handle handleAt(int x, int y) {
        Handle closest = null;
        double closestDistance = GRAB_RADIUS * GRAB_RADIUS;
        for (Handle handle : Handle.values()) {
            double[] p = screen(handle);
            double d = (p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y);
            if (d <= closestDistance) {
                closest = handle;
                closestDistance = d;
            }
        }
        return closest;
    }

    private void setHovered(Handle handle) {
        if (handle == hovered) return;
        final TriggerBehaviourBindings bindings = bdvh.getTriggerbindings();
        if (hovered == null) installBindings(bindings);
        if (handle == null) {
            bindings.removeBehaviourMap(GIZMO);
            bindings.removeInputTriggerMap(GIZMO);
        }
        hovered = handle;
        bdvh.getViewerPanel().getDisplay().repaint();
    }

    /**
     * Gives the left button to the gizmo while a handle is hovered. The navigation triggers of BigDataViewer are
     * blocked, so they are copied first, all but the left button: zooming keeps working over a handle.
     * See {@code sc.fiji.bdvpg.viewer.bdv.overlay.AxesOverlay}, which does the same.
     */
    private void installBindings(TriggerBehaviourBindings bindings) {
        final InputTriggerMap map = new InputTriggerMap();
        for (Map.Entry<InputTrigger, Set<String>> entry : bindings.getConcatenatedInputTriggerMap().getAllBindings().entrySet()) {
            if (LEFT_BUTTON.equals(entry.getKey()) || SHIFT_LEFT_BUTTON.equals(entry.getKey())) continue;
            for (String behaviourName : entry.getValue()) {
                map.put(entry.getKey(), behaviourName);
            }
        }
        map.put(LEFT_BUTTON, GIZMO);
        map.put(SHIFT_LEFT_BUTTON, GIZMO + "_constrained");
        bindings.addBehaviourMap(GIZMO, behaviours);
        bindings.addInputTriggerMap(GIZMO, map, BDV_TRANSFORM);
    }

    private double[] screen(Handle handle) {
        double[] p = gizmo.position(handle);
        double[] screen = {p[0], p[1], 0};
        viewerTransform().apply(screen, screen);
        return screen;
    }

    private double[] world(int x, int y) {
        double[] world = {x, y, 0};
        viewerTransform().applyInverse(world, world);
        return world;
    }

    private AffineTransform3D viewerTransform() {
        AffineTransform3D transform = new AffineTransform3D();
        bdvh.getViewerPanel().state().getViewerTransform(transform);
        return transform;
    }

    private class HandleDrag implements DragBehaviour {

        private final boolean constrained;

        private boolean dragging;

        HandleDrag(boolean constrained) {
            this.constrained = constrained;
        }

        @Override
        public void init(int x, int y) {
            Handle handle = hovered;
            dragging = handle != null;
            if (!dragging) return;
            double[] p = world(x, y);
            gizmo.startDrag(handle, p[0], p[1]);
        }

        @Override
        public void drag(int x, int y) {
            if (!dragging) return;
            double[] p = world(x, y);
            gizmo.drag(p[0], p[1], constrained);
            onChange.run();
            bdvh.getViewerPanel().getDisplay().repaint();
        }

        @Override
        public void end(int x, int y) {
            if (!dragging) return;
            gizmo.endDrag();
            dragging = false;
            setHovered(handleAt(x, y));
        }
    }

}
