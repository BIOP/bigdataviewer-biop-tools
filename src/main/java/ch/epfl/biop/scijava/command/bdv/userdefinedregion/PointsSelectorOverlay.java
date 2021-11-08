package ch.epfl.biop.scijava.command.bdv.userdefinedregion;

import bdv.util.BdvOverlay;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import ch.epfl.biop.bdv.select.SourceSelectorOverlay;
import net.imglib2.RealPoint;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.util.Behaviours;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

/**
 *
 * TODO : update javadoc, which is wrong
 *
 * Displays box overlays on top of visible sources of all visible {@link SourceAndConverter} of a {@link ViewerPanel}
 *
 * The coloring differs depending on the state (selected or not selected)
 *
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

public class PointsSelectorOverlay extends BdvOverlay implements MouseMotionListener {

    final ViewerPanel viewer;

    RealPoint currentPt;

    final PointsSelectorBehaviour psb;

    public PointsSelectorOverlay(ViewerPanel viewer, PointsSelectorBehaviour psb) {
        this.psb = psb;
        this.viewer = viewer;
        currentPt = new RealPoint(3);
    }

    protected void addSelectionBehaviours(Behaviours behaviours) {
        behaviours.behaviour( new AddPointBehaviour( RectangleSelectorBehaviour.SET ), "select-set-rectangle", new String[] { "button1" });
    }

    @Override
    public synchronized void draw(Graphics2D g) {
        psb.getGraphicalHandles().forEach(gh -> gh.draw(g));
    }

    @Override
    public void setCanvasSize( final int width, final int height ) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        psb.mouseDragged(e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        psb.mouseMoved(e);
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
