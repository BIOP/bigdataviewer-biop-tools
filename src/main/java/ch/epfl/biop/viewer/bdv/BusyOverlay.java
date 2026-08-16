package ch.epfl.biop.viewer.bdv;

import bdv.viewer.OverlayRenderer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;

/**
 * A minimal {@link OverlayRenderer} which displays a "busy" banner (spinner + message +
 * elapsed time) on top of a BigDataViewer window, typically while a long computation runs
 * in a background thread.
 * <p>
 * This overlay is deliberately dumb and lock-free: {@link #drawOverlays(Graphics)} is called
 * on the Event Dispatch Thread and only reads volatile fields. It never acquires a lock on
 * the object being computed, so the EDT cannot be blocked by the background task, however
 * long that task holds its own monitors.
 * <p>
 * Add it with {@code bdvHandle.getViewerPanel().getDisplay().overlays().add(overlay)}: that
 * list is thread safe, and - contrary to {@code BdvFunctions.showOverlay} - it does not add a
 * source to the viewer state.
 * <p>
 * Note that BigDataViewer only repaints the canvas when something changes: to animate the
 * spinner, the caller is expected to periodically call
 * {@code bdvHandle.getViewerPanel().getDisplay().repaint()} (a cheap operation which
 * re-composites the overlays over the already rendered image, without re-rendering
 * the sources).
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL
 */
public class BusyOverlay implements OverlayRenderer {

    private static final Color BACKGROUND = new Color(0, 0, 0, 170);
    private static final Color BORDER = new Color(255, 255, 255, 60);
    private static final Color TEXT = new Color(255, 255, 255, 230);
    private static final Color SPINNER_TRACK = new Color(255, 255, 255, 60);
    private static final Color SPINNER = new Color(255, 190, 60);

    private static final int BANNER_HEIGHT = 30;
    private static final int BANNER_TOP_MARGIN = 10;
    private static final int SPINNER_DIAMETER = 16;
    private static final int PADDING = 12;
    private static final int GAP = 8;

    private volatile boolean busy = false;
    private volatile String message = "Working";
    private volatile long startTimeMs = 0;

    private volatile int canvasWidth = 0;
    private volatile int canvasHeight = 0;

    private boolean errorReported = false;

    /**
     * Switches the banner on or off. Can be called from any thread.
     * The elapsed time counter is reset each time the overlay transitions from idle to busy.
     *
     * @param busy whether the banner should be displayed
     * @param message the text to display, or null to keep the current one
     */
    public synchronized void setBusy(boolean busy, String message) {
        if (message != null) this.message = message;
        if (busy && !this.busy) this.startTimeMs = System.currentTimeMillis();
        this.busy = busy;
    }

    public boolean isBusy() {
        return busy;
    }

    @Override
    public void drawOverlays(Graphics g) {
        if (!busy) return;
        try {
            draw((Graphics2D) g);
        } catch (Exception e) {
            // Never let a painting problem break the viewer, and never spam the console either
            if (!errorReported) {
                errorReported = true;
                System.err.println("Error while drawing the busy overlay: " + e.getMessage());
            }
        }
    }

    private void draw(Graphics2D g) {
        final int width = canvasWidth;
        if (width <= 0) return;

        final String text = getText();

        final Object aaHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        final Font oldFont = g.getFont();
        final Color oldColor = g.getColor();
        final Stroke oldStroke = g.getStroke();

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(oldFont.deriveFont(Font.BOLD, 13f));

            final FontMetrics fm = g.getFontMetrics();
            final int textWidth = fm.stringWidth(text);
            final int bannerWidth = PADDING + SPINNER_DIAMETER + GAP + textWidth + PADDING;
            final int x = Math.max(0, (width - bannerWidth) / 2);
            final int y = BANNER_TOP_MARGIN;

            final RoundRectangle2D.Float banner =
                    new RoundRectangle2D.Float(x, y, bannerWidth, BANNER_HEIGHT, 10, 10);
            g.setColor(BACKGROUND);
            g.fill(banner);
            g.setColor(BORDER);
            g.setStroke(new BasicStroke(1f));
            g.draw(banner);

            // Spinner: one full turn per second, derived from the clock so that no state is needed
            final float spinnerX = x + PADDING;
            final float spinnerY = y + (BANNER_HEIGHT - SPINNER_DIAMETER) / 2f;
            final float startAngle = 90 - (System.currentTimeMillis() % 1000L) * 0.36f;

            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(SPINNER_TRACK);
            g.draw(new Arc2D.Float(spinnerX, spinnerY, SPINNER_DIAMETER, SPINNER_DIAMETER,
                    0, 360, Arc2D.OPEN));
            g.setColor(SPINNER);
            g.draw(new Arc2D.Float(spinnerX, spinnerY, SPINNER_DIAMETER, SPINNER_DIAMETER,
                    startAngle, -100, Arc2D.OPEN));

            g.setColor(TEXT);
            g.drawString(text,
                    x + PADDING + SPINNER_DIAMETER + GAP,
                    y + (BANNER_HEIGHT + fm.getAscent()) / 2 - 1);
        } finally {
            g.setStroke(oldStroke);
            g.setColor(oldColor);
            g.setFont(oldFont);
            if (aaHint != null) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aaHint);
            }
        }
    }

    private String getText() {
        final long start = startTimeMs;
        if (start <= 0) return message;
        return message + " - " + formatDuration((System.currentTimeMillis() - start) / 1000L);
    }

    private static String formatDuration(long seconds) {
        if (seconds < 0) seconds = 0;
        if (seconds < 60) return seconds + " s";
        return (seconds / 60) + " min " + String.format("%02d", seconds % 60) + " s";
    }

    @Override
    public void setCanvasSize(int width, int height) {
        this.canvasWidth = width;
        this.canvasHeight = height;
    }
}
