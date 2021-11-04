package ch.epfl.biop.bdv.command.userdefinedregion;

import bdv.util.BdvOverlay;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import ch.epfl.biop.bdv.select.SourceSelectorBehaviour;
import ch.epfl.biop.bdv.select.SourceSelectorOverlay;
import net.imglib2.RealPoint;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.util.Behaviours;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.*;

/**
 * TODO : update javadoc, which is wrong
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

    //final String message;

    public RectangleSelectorOverlay(ViewerPanel viewer, RectangleSelectorBehaviour rsb, String message) {
        //this.message = message;
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

    RealPoint rpA, rpB, rpBprim, rpC, rpDprim, rpD, rpA_screen, rpB_screen, rpBprim_screen, rpC_screen, rpDprim_screen, rpD_screen;

    Point2D[] getLastCorners() {

        Point2D[] corners = new Point2D.Double[6];
        corners[0] = new Point2D.Double();
        corners[1] = new Point2D.Double();
        corners[2] = new Point2D.Double();
        corners[3] = new Point2D.Double();
        corners[4] = new Point2D.Double();
        corners[5] = new Point2D.Double();

        viewer.state().getViewerTransform().apply(rpA, rpA_screen);
        viewer.state().getViewerTransform().apply(rpB, rpB_screen);
        viewer.state().getViewerTransform().apply(rpBprim, rpBprim_screen);
        viewer.state().getViewerTransform().apply(rpC, rpC_screen);
        viewer.state().getViewerTransform().apply(rpDprim, rpDprim_screen);
        viewer.state().getViewerTransform().apply(rpD, rpD_screen);

        corners[0].setLocation(rpA_screen.getDoublePosition(0), rpA_screen.getDoublePosition(1));
        corners[1].setLocation(rpB_screen.getDoublePosition(0), rpB_screen.getDoublePosition(1));
        corners[2].setLocation(rpBprim_screen.getDoublePosition(0), rpBprim_screen.getDoublePosition(1));
        corners[3].setLocation(rpC_screen.getDoublePosition(0), rpC_screen.getDoublePosition(1));
        corners[4].setLocation(rpDprim_screen.getDoublePosition(0), rpDprim_screen.getDoublePosition(1));
        corners[5].setLocation(rpD_screen.getDoublePosition(0), rpD_screen.getDoublePosition(1));

        return corners;
    }

    Point2D[] getCurrentCorners() {

        rpA = new RealPoint(3);
        rpB = new RealPoint(3);
        rpBprim = new RealPoint(3);
        rpC = new RealPoint(3);
        rpDprim = new RealPoint(3);
        rpD = new RealPoint(3);

        rpA_screen = new RealPoint(3);
        rpB_screen = new RealPoint(3);
        rpBprim_screen = new RealPoint(3);
        rpC_screen = new RealPoint(3);
        rpDprim_screen = new RealPoint(3);
        rpD_screen = new RealPoint(3);

        viewer.displayToGlobalCoordinates(xCurrentSelectStart, yCurrentSelectStart, rpA);
        viewer.displayToGlobalCoordinates(xCurrentSelectEnd, yCurrentSelectEnd, rpC);
        rpB.setPosition(new double[]{rpA.getDoublePosition(0), rpC.getDoublePosition(1), rpA.getDoublePosition(2)});
        rpBprim.setPosition(new double[]{rpA.getDoublePosition(0), rpC.getDoublePosition(1), rpC.getDoublePosition(2)});
        rpDprim.setPosition(new double[]{rpC.getDoublePosition(0), rpA.getDoublePosition(1), rpC.getDoublePosition(2)});
        rpD.setPosition(new double[]{rpC.getDoublePosition(0), rpA.getDoublePosition(1), rpA.getDoublePosition(2)});

        Point2D[] corners = new Point2D.Double[6];
        corners[0] = new Point2D.Double();
        corners[1] = new Point2D.Double();
        corners[2] = new Point2D.Double();
        corners[3] = new Point2D.Double();
        corners[4] = new Point2D.Double();
        corners[5] = new Point2D.Double();

        viewer.state().getViewerTransform().apply(rpA, rpA_screen);
        viewer.state().getViewerTransform().apply(rpB, rpB_screen);
        viewer.state().getViewerTransform().apply(rpBprim, rpBprim_screen);
        viewer.state().getViewerTransform().apply(rpC, rpC_screen);
        viewer.state().getViewerTransform().apply(rpDprim, rpDprim_screen);
        viewer.state().getViewerTransform().apply(rpD, rpD_screen);

        corners[0].setLocation(rpA_screen.getDoublePosition(0), rpA_screen.getDoublePosition(1));
        corners[1].setLocation(rpB_screen.getDoublePosition(0), rpB_screen.getDoublePosition(1));
        corners[2].setLocation(rpBprim_screen.getDoublePosition(0), rpBprim_screen.getDoublePosition(1));
        corners[3].setLocation(rpC_screen.getDoublePosition(0), rpC_screen.getDoublePosition(1));
        corners[4].setLocation(rpDprim_screen.getDoublePosition(0), rpDprim_screen.getDoublePosition(1));
        corners[5].setLocation(rpD_screen.getDoublePosition(0), rpD_screen.getDoublePosition(1));

        return corners;
    }

    Font font = new Font("Courier", Font.PLAIN, 20);

    @Override
    public synchronized void draw(Graphics2D g) {
        g.setStroke( styles.get("SELECTED").getNormalStroke() );
        g.setPaint( styles.get("SELECTED").getBackColor() );
        g.setFont(font);
        //g.drawString(message, 50, viewer.getHeight()-50);
        if (isCurrentlySelecting) {
            Point2D[] corners = getCurrentCorners();
            g.drawLine((int) corners[0].getX(),(int)  corners[0].getY(), (int) corners[1].getX(),(int)  corners[1].getY());
            g.setStroke( styles.get("SELECTED").getIntersectionStroke() );
            g.drawLine((int) corners[1].getX(),(int)  corners[1].getY(), (int) corners[2].getX(),(int)  corners[2].getY());
            g.setStroke( styles.get("SELECTED").getNormalStroke() );
            g.drawLine((int) corners[2].getX(),(int)  corners[2].getY(), (int) corners[3].getX(),(int)  corners[3].getY());
            g.drawLine((int) corners[3].getX(),(int)  corners[3].getY(), (int) corners[4].getX(),(int)  corners[4].getY());
            g.setStroke( styles.get("SELECTED").getIntersectionStroke() );
            g.drawLine((int) corners[4].getX(),(int)  corners[4].getY(), (int) corners[5].getX(),(int)  corners[5].getY());
            g.setStroke( styles.get("SELECTED").getNormalStroke() );
            g.drawLine((int) corners[5].getX(),(int)  corners[5].getY(), (int) corners[0].getX(),(int)  corners[0].getY());
        } else if (lastRectangleDrawn) {
            Point2D[] corners = getLastCorners();
            g.drawLine((int) corners[0].getX(),(int)  corners[0].getY(), (int) corners[1].getX(),(int)  corners[1].getY());
            g.setStroke( styles.get("SELECTED").getIntersectionStroke() );
            g.drawLine((int) corners[1].getX(),(int)  corners[1].getY(), (int) corners[2].getX(),(int)  corners[2].getY());
            g.setStroke( styles.get("SELECTED").getNormalStroke() );
            g.drawLine((int) corners[2].getX(),(int)  corners[2].getY(), (int) corners[3].getX(),(int)  corners[3].getY());
            g.drawLine((int) corners[3].getX(),(int)  corners[3].getY(), (int) corners[4].getX(),(int)  corners[4].getY());
            g.setStroke( styles.get("SELECTED").getIntersectionStroke() );
            g.drawLine((int) corners[4].getX(),(int)  corners[4].getY(), (int) corners[5].getX(),(int)  corners[5].getY());
            g.setStroke( styles.get("SELECTED").getNormalStroke() );
            g.drawLine((int) corners[5].getX(),(int)  corners[5].getY(), (int) corners[0].getX(),(int)  corners[0].getY());
        }

    }

    @Override
    public void setCanvasSize( final int width, final int height ) {
        this.canvasWidth = width;
        this.canvasHeight = height;
    }

    boolean lastRectangleDrawn = false;

    public void drawLastRectangle() {
        lastRectangleDrawn = true;
    }

    public void removeLastRectangle() {
        lastRectangleDrawn = false;
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
        Color backColor = new Color(0xF8E7A3);

        Color frontColor = new Color(0xC7F718);

        Color intersectionFillColor = new Color(0x30B66A00, true );

        Stroke normalStroke = new BasicStroke(3);

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
