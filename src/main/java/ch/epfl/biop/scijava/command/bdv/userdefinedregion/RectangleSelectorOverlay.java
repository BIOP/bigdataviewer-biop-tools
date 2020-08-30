package ch.epfl.biop.scijava.command.bdv.userdefinedregion;

import bdv.tools.boundingbox.RenderBoxHelper;
import bdv.tools.boundingbox.TransformedBox;
import bdv.util.BdvOverlay;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import ch.epfl.biop.bdv.select.SourceSelectorBehaviour;
import ch.epfl.biop.bdv.select.SourceSelectorOverlay;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.util.Behaviours;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.util.List;
import java.util.*;

/**
 * Works with {@link SourceSelectorBehaviour}
 *
 * Displays box overlays on top of visible sources of all visible {@link SourceAndConverter} of a {@link ViewerPanel}
 *
 * The coloring differs depending on the state (selected or not selected)
 *
 * An inner interface {@link OverlayStyle} is used to define how the overlay box are colored.
 * The coloring can be modified with the {@link SourceSelectorOverlay#getStyles()} function
 * and by modify using the values contained in "DEFAULT" and "SELECTED"
 *
 * GUI functioning:
 *
 * The user can draw a rectangle and all sources which interesects this rectangle AT THE CURRENT PLANE SLICING of Bdv
 * will be involved in the next selection change event.
 * Either the user was holding no extra key:
 * - the involved sources will define the new selection set
 * The user was holding CTRL:
 * - the involved sources will be removed from the current selection set
 * The user was holding SHIFT:
 * - the involved sources are added to the current selection set
 * Note : changing the key pressing DURING the rectangle drawing will not be taken into account,
 * contrary to an expected standard behaviour TODO : can this be improved ?
 *
 * Note : The user can perform a single click as well with the modifier keys, no need to drag
 * this is because a single click also triggers a {@link DragBehaviour}
 *
 * Note : the overlay can be very slow to draw - because it's java graphics 2D... It's
 * especially visible is the zoom is very big... Clipping is badly done TODO ?
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL, 2020
 *
 */

public class RectangleSelectorOverlay extends BdvOverlay {

    final ViewerPanel viewer;

    boolean isCurrentlySelecting = false;

    int xCurrentSelectStart, yCurrentSelectStart, xCurrentSelectEnd, yCurrentSelectEnd;

    RealPoint ptStartGlobal, ptEndGlobal;

    private int canvasWidth;

    private int canvasHeight;

    final RectangleSelectorBehaviour rsb;

    Map<String, SourceSelectorOverlay.OverlayStyle> styles = new HashMap<>();

    public RectangleSelectorOverlay(ViewerPanel viewer, RectangleSelectorBehaviour rsb) {
        this.rsb = rsb;
        this.viewer = viewer;
        styles.put("SELECTED", new RectangleSelectorOverlay.SelectedOverlayStyle());

        ptStartGlobal = new RealPoint(3);
        ptEndGlobal = new RealPoint(3);
    }

    protected void addSelectionBehaviours(Behaviours behaviours) {
        behaviours.behaviour( new RectangleSelectSourcesBehaviour( RectangleSelectorBehaviour.SET ), "select-set-rectangle", new String[] { "button1" });
    }

    synchronized void startCurrentSelection(int x, int y) {
        xCurrentSelectStart = x;
        yCurrentSelectStart = y;
        viewer.displayToGlobalCoordinates(xCurrentSelectStart,yCurrentSelectStart, ptStartGlobal);
    }

    synchronized void updateCurrentSelection(int xCurrent, int yCurrent) {
        xCurrentSelectEnd = xCurrent;
        yCurrentSelectEnd = yCurrent;
        isCurrentlySelecting = true;
        viewer.displayToGlobalCoordinates(xCurrentSelectEnd,yCurrentSelectEnd, ptEndGlobal);
    }

    synchronized void endCurrentSelection(int x, int y, String mode) {
        xCurrentSelectEnd = x;
        yCurrentSelectEnd = y;
        isCurrentlySelecting = false;
        // Selection is done : but we need to access the trigger keys to understand what's happening

        viewer.displayToGlobalCoordinates(xCurrentSelectEnd,yCurrentSelectEnd, ptEndGlobal);

        rsb.processSelectionEvent(ptStartGlobal, ptEndGlobal);
    }

    Rectangle getCurrentSelectionRectangle() {
        int x0, y0, w, h;
        if (xCurrentSelectStart>xCurrentSelectEnd) {
            x0 = xCurrentSelectEnd;
            w = xCurrentSelectStart-xCurrentSelectEnd;
        } else {
            x0 = xCurrentSelectStart;
            w = xCurrentSelectEnd-xCurrentSelectStart;
        }
        if (yCurrentSelectStart>yCurrentSelectEnd) {
            y0 = yCurrentSelectEnd;
            h = yCurrentSelectStart-yCurrentSelectEnd;
        } else {
            y0 = yCurrentSelectStart;
            h = yCurrentSelectEnd-yCurrentSelectStart;
        }
        // Hack : allows selection on double or single click
        if (w==0) w = 1;
        if (h==0) h = 1;
        return new Rectangle(x0, y0, w, h);
    }

    Font font = new Font("Courier", Font.PLAIN, 20);

    @Override
    public synchronized void draw(Graphics2D g) {
        g.setStroke( styles.get("SELECTED").getNormalStroke() );
        g.setPaint( styles.get("SELECTED").getBackColor() );
        g.setFont(font);
        g.drawString("Please select a region", 50, viewer.getHeight()-50);
        if (isCurrentlySelecting) {
            g.draw(getCurrentSelectionRectangle());
        }

    }

    @Override
    public void setCanvasSize( final int width, final int height ) {
        this.canvasWidth = width;
        this.canvasHeight = height;
    }

    /**
     * STYLES
     */

    public interface OverlayStyle {

        Color getBackColor();

        Color getFrontColor();

        Color getIntersectionColor();

        Color getIntersectionFillColor();

        Stroke getNormalStroke();

        Stroke getIntersectionStroke();

    }

    public class SelectedOverlayStyle implements SourceSelectorOverlay.OverlayStyle {
        Color backColor = new Color(0xF7BF18);

        Color frontColor = new Color(0xC7F718);

        Color intersectionFillColor = new Color(0x30B66A00, true );

        Stroke normalStroke = new BasicStroke();

        Stroke intersectionStroke = new BasicStroke( 1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 10f, 10f }, 0f );

        Color intersectionColor = Color.WHITE.darker();

        public Color getBackColor() {
            return backColor;
        }

        public Color getFrontColor() {
            return frontColor;
        }

        @Override
        public Color getIntersectionColor() {
            return intersectionColor;
        }

        public Color getIntersectionFillColor() {
            return intersectionFillColor;
        }

        public Stroke getNormalStroke() {
            return normalStroke;
        }

        @Override
        public Stroke getIntersectionStroke() {
            return intersectionStroke;
        }

    }

    /**
     * Drag Selection Behaviour
     */
    class RectangleSelectSourcesBehaviour implements DragBehaviour {

        final String mode;

        public RectangleSelectSourcesBehaviour(String mode) {
            this.mode = mode;
        }

        @Override
        public void init(int x, int y) {
            startCurrentSelection(x,y);
            viewer.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            switch(mode) {
                case SourceSelectorBehaviour.SET :
                    viewer.showMessage("Set Selection" );
                    break;
            }
        }

        @Override
        public void drag(int x, int y) {
            updateCurrentSelection(x,y);
            viewer.getDisplay().repaint();
        }

        @Override
        public void end(int x, int y) {
            endCurrentSelection(x,y,mode);
            viewer.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
    }

}
