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
package ch.epfl.biop.viewer.bdv.graphicalhandle;

import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import java.awt.*;
import java.util.function.Supplier;

/**
 * Circular Graphical Handle {@link GraphicalHandle}, which radius, position, and color is defined by
 * functional interfaces.
 */

public class CircleGraphicalHandle extends GraphicalHandle{

    Supplier<Integer[]> coords;
    Supplier<Integer> radius;
    Supplier<Integer[]> color;

    public CircleGraphicalHandle(GraphicalHandleListener ghl,
                                 Behaviours behaviours,
                                 TriggerBehaviourBindings bindings,
                                 String nameMap,
                                 Supplier<Integer[]> coords,
                                 Supplier<Integer> radius,
                                 Supplier<Integer[]> color) {
        super(ghl, behaviours, bindings, nameMap);
        this.radius = radius;
        this.color = color;
        this.coords = coords;
    }

    public CircleGraphicalHandle(GraphicalHandleListener ghl,
                                 Behaviour behaviour,String behaviourName, String trigger,
                                 TriggerBehaviourBindings bindings,
                                 Supplier<Integer[]> coords,
                                 Supplier<Integer> radius,
                                 Supplier<Integer[]> color) {
        super(ghl, wrapBehaviours(behaviour, behaviourName, trigger), bindings, behaviour.toString());
        this.radius = radius;
        this.color = color;
        this.coords = coords;
    }

    static Behaviours wrapBehaviours(Behaviour behaviour, String behaviourName, String trigger) {
        Behaviours behaviours = new Behaviours(new InputTriggerConfig());
        behaviours.behaviour(behaviour, behaviourName, trigger);
        return behaviours;
    }

    @Override
    public synchronized void enabledDraw(Graphics2D g) {
        Integer r = radius.get();
        Integer[] pos = coords.get();
        Integer[] c = color.get();
        g.setColor(new Color(c[0], c[1], c[2], c[3]));
        if (this.mouseAbove) {
            r=(int) (r*1.2);
        }
        g.fillOval(pos[0] - r, pos[1] - r, 2*r, 2*r);
    }

    @Override
    public synchronized void disabledDraw(Graphics2D g) {

    }

    @Override
    synchronized boolean isPresentAt(int x, int y) {
        Integer[] pos = coords.get();
        double r = (double)(radius.get());
        if ((pos == null) || (pos[0] == null) || (pos[1] == null)) return false;
        double dx = (double)pos[0]-(double)x;
        double dy = (double)pos[1]-(double)y;
        double d2 = dx*dx+dy*dy;
        return d2<(r*r);
    }

    @Override
    public int[] getScreenCoordinates() {
        int[] unboxed = new int[3];
        Integer[] c = coords.get();
        for (int i = 0;i<3;i++) {
            unboxed[i] = c[i];
        }
        return unboxed;
    }
}
