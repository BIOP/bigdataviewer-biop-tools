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
package ch.epfl.biop.viewer.bdv;

import bdv.util.BdvOverlay;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import ch.epfl.biop.bdv.select.SourceSelectorOverlay;
import net.imglib2.RealPoint;
import org.scijava.ui.behaviour.Behaviour;
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
        behaviours.behaviour( new AddGlobalPointBehaviour(viewer, psb ), "add_point_global_hack", new String[] { "shift alt ctrl P" }); // let's hope nobody presses that TODO : fix
        behaviours.behaviour( new AddPointBehaviour( RectangleSelectorBehaviour.SET ), "add_point_display", new String[] { "button1" });
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

    /**
     * Drag Selection Behaviour
     */
    public static class AddGlobalPointBehaviour implements Behaviour {

        public AddGlobalPointBehaviour(ViewerPanel viewer, PointsSelectorBehaviour psb) {
            this.viewer = viewer;
            this.psb = psb;
        }

        final ViewerPanel viewer;
        final PointsSelectorBehaviour psb;

        public void addGlobalPoint(double x, double y, double z) {
            RealPoint ptGlobalCoordinates = new RealPoint(3);
            ptGlobalCoordinates.setPosition(x,0);
            ptGlobalCoordinates.setPosition(y,1);
            ptGlobalCoordinates.setPosition(z,2);
            psb.addPoint(ptGlobalCoordinates);
            viewer.getDisplay().repaint();
        }

    }

}
