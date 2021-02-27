package ch.epfl.biop.bdv.userdefinedregion;

import bdv.util.BdvOverlay;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import ch.epfl.biop.bdv.select.SourceSelectorBehaviour;
import ch.epfl.biop.bdv.select.SourceSelectorOverlay;
import net.imglib2.RealPoint;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.util.Behaviours;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 *
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

public class PointsSelectorOverlay extends BdvOverlay {

    final ViewerPanel viewer;

    RealPoint currentPt;

    private int canvasWidth;

    private int canvasHeight;

    final PointsSelectorBehaviour psb;

    Map<String, SourceSelectorOverlay.OverlayStyle> styles = new HashMap<>();

    String message;

    public PointsSelectorOverlay(ViewerPanel viewer, PointsSelectorBehaviour psb, String message) {
        this.message = message + " - press escape to exit";
        this.psb = psb;
        this.viewer = viewer;
        styles.put("SELECTED", new PointsSelectorOverlay.SelectedOverlayStyle());

        currentPt = new RealPoint(3);
    }

    protected void addSelectionBehaviours(Behaviours behaviours) {
        behaviours.behaviour( new AddPointBehaviour( RectangleSelectorBehaviour.SET ), "select-set-rectangle", new String[] { "button1" });
        //behaviours.behaviour( (ClickBehaviour) (x,y) -> psb.userDone = true , "user-is-done", new String[] { "Q", "CTRL C" });

    }

    Font font = new Font("Courier", Font.PLAIN, 20);

    @Override
    public synchronized void draw(Graphics2D g) {
        g.setStroke( styles.get("SELECTED").getNormalStroke() );
        g.setPaint( styles.get("SELECTED").getBackColor() );
        g.setFont(font);
        g.drawString(message, 50, viewer.getHeight()-50);

        g.setColor( styles.get("SELECTED").getFrontColor());

        psb.getPoints().forEach(pt -> {
            RealPoint localC = new RealPoint(pt);
            viewer.state().getViewerTransform().apply(localC, localC);
            g.drawOval((int) (localC.getDoublePosition(0)-10), (int) (localC.getDoublePosition(1)-10), 20,20);
        });

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
    class AddPointBehaviour implements ClickBehaviour {

        final String mode;

        public AddPointBehaviour(String mode) {
            this.mode = mode;
        }

        @Override
        public void click(int x, int y) {
            RealPoint ptGlobalCoordinates = new RealPoint(3);
            viewer.displayToGlobalCoordinates(x,y, ptGlobalCoordinates);
            viewer.getDisplay().repaint();
            psb.addPoint(ptGlobalCoordinates);
        }

    }

}
