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
package ch.epfl.biop.viewer.bdv.gizmo;

import net.imglib2.realtransform.AffineTransform3D;

import java.util.Arrays;

/**
 * Model of a gizmo editing the in-plane part of an affine transform with four handles.
 * <p>
 * The transform M is held as an origin O and two axes U and V around an anchor c:
 * M·p = O + U·(p−c)<sub>x</sub> + V·(p−c)<sub>y</sub>. The handles sit at O (center), O + U·L (x axis),
 * O + V·L (y axis) and O + (U+V)·L (similarity), where L is a length in world units.
 * <ul>
 *     <li>center: translates; constrained, along x or y only</li>
 *     <li>x or y axis: sets the axis freely, which scales and shears; constrained, keeps its direction</li>
 *     <li>similarity: rotates and scales both axes together; constrained, rotates only</li>
 * </ul>
 * Only the xy block and the xy translation of the transform are edited, the other coefficients are kept.
 * All coordinates are world coordinates.
 */
public class AffineGizmo {

    public enum Handle { CENTER, X_AXIS, Y_AXIS, SIMILARITY }

    private final AffineTransform3D transform;

    private final double cx, cy, length;

    // Origin and axes: {ox, oy, ux, uy, vx, vy}
    private final double[] state = new double[6];

    // Origin and axes when the drag started
    private final double[] start = new double[6];

    private Handle dragged;

    // Vector from the mouse to the dragged handle when the drag started: the handle does not jump to the mouse
    private double grabX, grabY;

    /**
     * @param initial transform to edit, not modified
     * @param cx anchor, x: the point of the moving space the center handle represents
     * @param cy anchor, y
     * @param length distance L of the axis handles from the center, for axes of unit length
     */
    public AffineGizmo(AffineTransform3D initial, double cx, double cy, double length) {
        this.transform = initial.copy();
        this.cx = cx;
        this.cy = cy;
        this.length = length;
        reset();
    }

    /**
     * Goes back to the initial transform, ending any drag
     */
    public void reset() {
        dragged = null;
        System.arraycopy(initialState(), 0, state, 0, 6);
    }

    /**
     * @return true if the transform is not the initial one
     */
    public boolean isChanged() {
        return !Arrays.equals(state, initialState());
    }

    private double[] initialState() {
        return new double[]{
                transform.get(0, 0) * cx + transform.get(0, 1) * cy + transform.get(0, 3),
                transform.get(1, 0) * cx + transform.get(1, 1) * cy + transform.get(1, 3),
                transform.get(0, 0), transform.get(1, 0),
                transform.get(0, 1), transform.get(1, 1)};
    }

    /**
     * @return the edited transform
     */
    public AffineTransform3D getTransform() {
        AffineTransform3D result = transform.copy();
        double ox = state[0], oy = state[1], ux = state[2], uy = state[3], vx = state[4], vy = state[5];
        result.set(ux, 0, 0);
        result.set(uy, 1, 0);
        result.set(vx, 0, 1);
        result.set(vy, 1, 1);
        result.set(ox - ux * cx - vx * cy, 0, 3);
        result.set(oy - uy * cx - vy * cy, 1, 3);
        return result;
    }

    /**
     * @return the world position {x, y} of a handle
     */
    public double[] position(Handle handle) {
        double ox = state[0], oy = state[1], ux = state[2], uy = state[3], vx = state[4], vy = state[5];
        switch (handle) {
            case X_AXIS: return new double[]{ox + ux * length, oy + uy * length};
            case Y_AXIS: return new double[]{ox + vx * length, oy + vy * length};
            case SIMILARITY: return new double[]{ox + (ux + vx) * length, oy + (uy + vy) * length};
            default: return new double[]{ox, oy};
        }
    }

    /**
     * Starts dragging a handle
     * @param handle the handle grabbed
     * @param x world position of the mouse, x
     * @param y world position of the mouse, y
     */
    public void startDrag(Handle handle, double x, double y) {
        dragged = handle;
        System.arraycopy(state, 0, start, 0, 6);
        double[] p = position(handle);
        grabX = p[0] - x;
        grabY = p[1] - y;
    }

    /**
     * Moves the dragged handle, does nothing if no drag was started
     * @param x world position of the mouse, x
     * @param y world position of the mouse, y
     * @param constrained see the class documentation
     */
    public void drag(double x, double y, boolean constrained) {
        if (dragged == null) return;
        double tx = x + grabX, ty = y + grabY;
        double ox = start[0], oy = start[1];
        switch (dragged) {
            case CENTER:
                if (constrained) {
                    if (Math.abs(tx - ox) > Math.abs(ty - oy)) ty = oy; else tx = ox;
                }
                state[0] = tx;
                state[1] = ty;
                break;
            case X_AXIS:
                setAxis(2, (tx - ox) / length, (ty - oy) / length, constrained);
                break;
            case Y_AXIS:
                setAxis(4, (tx - ox) / length, (ty - oy) / length, constrained);
                break;
            case SIMILARITY:
                double dx = (start[2] + start[4]) * length, dy = (start[3] + start[5]) * length;
                double norm = dx * dx + dy * dy;
                if (norm == 0) return;
                // Rotation and scaling (re, im) sending the start handle vector to the target one, as complex numbers
                double re = ((tx - ox) * dx + (ty - oy) * dy) / norm;
                double im = ((ty - oy) * dx - (tx - ox) * dy) / norm;
                if (constrained) {
                    double scale = Math.hypot(re, im);
                    if (scale == 0) return;
                    re /= scale;
                    im /= scale;
                }
                for (int i = 2; i < 6; i += 2) {
                    state[i] = re * start[i] - im * start[i + 1];
                    state[i + 1] = im * start[i] + re * start[i + 1];
                }
                break;
        }
    }

    /**
     * Ends the drag
     */
    public void endDrag() {
        dragged = null;
    }

    /**
     * @return true between {@link #startDrag} and {@link #endDrag} or {@link #reset}
     */
    public boolean isDragging() {
        return dragged != null;
    }

    /**
     * The change made by the current drag, from its start to now, written at the gizmo origin: the axes were
     * multiplied by a 2×2 matrix A and the origin was moved by a vector t. See {@link #applyChange(double[])}.
     * @return {a00, a01, a10, a11, tx, ty}, or null if no drag is in progress, or if the axes were collinear when the
     * drag started and have changed since
     */
    public double[] getDragChange() {
        if (dragged == null) return null;
        double tx = state[0] - start[0], ty = state[1] - start[1];
        // A = [U V] · [U0 V0]^-1
        double ux = start[2], uy = start[3], vx = start[4], vy = start[5];
        double det = ux * vy - vx * uy;
        if (det == 0) {
            for (int i = 2; i < 6; i++) {
                if (state[i] != start[i]) return null;
            }
            return new double[]{1, 0, 0, 1, tx, ty};
        }
        double i00 = vy / det, i01 = -vx / det, i10 = -uy / det, i11 = ux / det;
        return new double[]{
                state[2] * i00 + state[4] * i10, state[2] * i01 + state[4] * i11,
                state[3] * i00 + state[5] * i10, state[3] * i01 + state[5] * i11,
                tx, ty};
    }

    /**
     * Remembers the current transform as the one {@link #applyChange(double[])} starts from.
     * {@link #startDrag} does the same.
     */
    public void startChange() {
        System.arraycopy(state, 0, start, 0, 6);
    }

    /**
     * Sets the transform to the one remembered by {@link #startChange()}, changed at its own origin O by a change
     * read from {@link #getDragChange()}: O ← O + t, U ← A·U, V ← A·V. The same change applied to several gizmos
     * translates them by the same vector, and rotates, scales and shears them the same way along the image axes.
     * @param change {a00, a01, a10, a11, tx, ty}
     */
    public void applyChange(double[] change) {
        state[0] = start[0] + change[4];
        state[1] = start[1] + change[5];
        for (int i = 2; i < 6; i += 2) {
            state[i] = change[0] * start[i] + change[1] * start[i + 1];
            state[i + 1] = change[2] * start[i] + change[3] * start[i + 1];
        }
    }

    /**
     * Sets the axis starting at index i of the state to (ax, ay), or to its projection on the start axis if constrained
     */
    private void setAxis(int i, double ax, double ay, boolean constrained) {
        if (constrained) {
            double sx = start[i], sy = start[i + 1];
            double norm = sx * sx + sy * sy;
            if (norm == 0) return;
            double k = (ax * sx + ay * sy) / norm;
            ax = k * sx;
            ay = k * sy;
        }
        state[i] = ax;
        state[i + 1] = ay;
    }

}
