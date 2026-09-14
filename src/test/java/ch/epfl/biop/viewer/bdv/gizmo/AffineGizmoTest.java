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

import ch.epfl.biop.viewer.bdv.gizmo.AffineGizmo.Handle;
import net.imglib2.realtransform.AffineTransform3D;
import org.junit.Assert;
import org.junit.Test;

public class AffineGizmoTest {

    static final double CX = 1.5, CY = -0.5, L = 2.0;

    /** An affine with rotation, shear, translation and non trivial z coefficients */
    static AffineTransform3D initial() {
        AffineTransform3D m = new AffineTransform3D();
        m.set(1.1, 0.3, 0.05, 4.0,
              -0.2, 0.9, 0.02, -3.0,
              0.01, 0.03, 1.0, 0.5);
        return m;
    }

    @Test
    public void unchangedWithoutDrag() {
        assertEquals(initial(), new AffineGizmo(initial(), CX, CY, L).getTransform());
    }

    @Test
    public void handlesSitOnTheAxes() {
        AffineGizmo gizmo = new AffineGizmo(initial(), CX, CY, L);
        double[] o = apply(initial(), CX, CY);
        Assert.assertArrayEquals(o, gizmo.position(Handle.CENTER), 1e-12);
        Assert.assertArrayEquals(new double[]{o[0] + 1.1 * L, o[1] - 0.2 * L}, gizmo.position(Handle.X_AXIS), 1e-12);
        Assert.assertArrayEquals(new double[]{o[0] + 0.3 * L, o[1] + 0.9 * L}, gizmo.position(Handle.Y_AXIS), 1e-12);
        Assert.assertArrayEquals(new double[]{o[0] + 1.4 * L, o[1] + 0.7 * L}, gizmo.position(Handle.SIMILARITY), 1e-12);
    }

    @Test
    public void centerTranslates() {
        // Grabbed away from the handle center: the handle keeps its offset to the mouse
        AffineTransform3D expected = initial().preConcatenate(translation(1, 2));
        assertEquals(expected, drag(Handle.CENTER, 1, 2, 0.1, false).getTransform());
        assertEquals(initial().preConcatenate(translation(1, 0)), drag(Handle.CENTER, 1, 0.4, 0.1, true).getTransform());
        assertEquals(initial().preConcatenate(translation(0, -2)), drag(Handle.CENTER, 0.3, -2, 0.1, true).getTransform());
    }

    @Test
    public void xAxisSetsTheFirstColumn() {
        AffineGizmo gizmo = new AffineGizmo(initial(), CX, CY, L);
        double[] o = gizmo.position(Handle.CENTER);
        gizmo.startDrag(Handle.X_AXIS, o[0] + 2 * L, o[1] + L);
        gizmo.drag(o[0] + 2 * L, o[1] + L, false);
        // The mouse was on the target, not on the handle: the offset is kept
        double[] x = gizmo.position(Handle.X_AXIS);
        Assert.assertArrayEquals(new double[]{o[0] + 1.1 * L, o[1] - 0.2 * L}, x, 1e-12);

        gizmo = new AffineGizmo(initial(), CX, CY, L);
        gizmo.startDrag(Handle.X_AXIS, x[0], x[1]);
        gizmo.drag(o[0] + 2 * L, o[1] + L, false);
        AffineTransform3D m = gizmo.getTransform();
        Assert.assertArrayEquals(new double[]{2, 1, 0.3, 0.9}, new double[]{m.get(0, 0), m.get(1, 0), m.get(0, 1), m.get(1, 1)}, 1e-12);
        Assert.assertArrayEquals("the anchor stays in place", o, apply(m, CX, CY), 1e-12);

        // Constrained: the axis keeps its direction, its length is the projection of the target
        gizmo.startDrag(Handle.X_AXIS, o[0] + 2 * L, o[1] + L);
        gizmo.drag(o[0] + 4 * L - L * 0.5, o[1] + 2 * L + L, true);
        m = gizmo.getTransform();
        // (3.5, 3) projected on (2, 1): k = (7 + 3) / 5 = 2
        Assert.assertArrayEquals(new double[]{4, 2}, new double[]{m.get(0, 0), m.get(1, 0)}, 1e-12);
    }

    @Test
    public void yAxisSetsTheSecondColumn() {
        AffineGizmo gizmo = new AffineGizmo(initial(), CX, CY, L);
        double[] o = gizmo.position(Handle.CENTER), y = gizmo.position(Handle.Y_AXIS);
        gizmo.startDrag(Handle.Y_AXIS, y[0], y[1]);
        gizmo.drag(o[0] - L, o[1] + 3 * L, false);
        AffineTransform3D m = gizmo.getTransform();
        Assert.assertArrayEquals(new double[]{1.1, -0.2, -1, 3}, new double[]{m.get(0, 0), m.get(1, 0), m.get(0, 1), m.get(1, 1)}, 1e-12);
        Assert.assertArrayEquals(o, apply(m, CX, CY), 1e-12);
    }

    @Test
    public void similarityRotatesAndScalesBothAxes() {
        AffineGizmo gizmo = new AffineGizmo(initial(), CX, CY, L);
        double[] o = gizmo.position(Handle.CENTER), s = gizmo.position(Handle.SIMILARITY);
        double dx = s[0] - o[0], dy = s[1] - o[1];
        double angle = 0.7, scale = 1.8;
        double c = Math.cos(angle), sn = Math.sin(angle);
        // Rotation about the gizmo center and scaling, applied after the initial transform
        AffineTransform3D similarity = translation(-o[0], -o[1]);
        AffineTransform3D rs = new AffineTransform3D();
        rs.set(scale * c, -scale * sn, 0, 0, scale * sn, scale * c, 0, 0, 0, 0, 1, 0);
        similarity.preConcatenate(rs).preConcatenate(translation(o[0], o[1]));

        gizmo.startDrag(Handle.SIMILARITY, s[0], s[1]);
        gizmo.drag(o[0] + scale * (c * dx - sn * dy), o[1] + scale * (sn * dx + c * dy), false);
        AffineTransform3D m = gizmo.getTransform();
        assertInPlaneEquals(initial().preConcatenate(similarity), m);

        // Constrained: rotation only
        gizmo = new AffineGizmo(initial(), CX, CY, L);
        gizmo.startDrag(Handle.SIMILARITY, s[0], s[1]);
        gizmo.drag(o[0] + scale * (c * dx - sn * dy), o[1] + scale * (sn * dx + c * dy), true);
        m = gizmo.getTransform();
        rs.set(c, -sn, 0, 0, sn, c, 0, 0, 0, 0, 1, 0);
        AffineTransform3D expected = initial().preConcatenate(translation(-o[0], -o[1]).preConcatenate(rs).preConcatenate(translation(o[0], o[1])));
        assertInPlaneEquals(expected, m);
    }

    @Test
    public void resetRestoresTheInitialTransform() {
        AffineGizmo gizmo = drag(Handle.SIMILARITY, 1, 2, 0, false);
        gizmo.reset();
        assertEquals(initial(), gizmo.getTransform());
        gizmo.drag(5, 5, false); // the drag ended with the reset
        assertEquals(initial(), gizmo.getTransform());
    }

    @Test
    public void dragWithoutStartDoesNothing() {
        AffineGizmo gizmo = new AffineGizmo(initial(), CX, CY, L);
        gizmo.drag(10, 10, false);
        assertEquals(initial(), gizmo.getTransform());
        gizmo.startDrag(Handle.CENTER, 0, 0);
        gizmo.endDrag();
        gizmo.drag(10, 10, false);
        assertEquals(initial(), gizmo.getTransform());
    }

    @Test
    public void dragChangeReproducesTheDrag() {
        for (Handle handle : Handle.values()) {
            for (boolean constrained : new boolean[]{false, true}) {
                AffineGizmo gizmo = drag(handle, 0.7, -1.3, 0.1, constrained);
                AffineGizmo copy = new AffineGizmo(initial(), CX, CY, L);
                copy.startChange();
                copy.applyChange(gizmo.getDragChange());
                assertEquals(gizmo.getTransform(), copy.getTransform());
            }
        }
    }

    @Test
    public void linkedChangeAtAnotherOrigin() {
        // A second gizmo with another transform, anchor and handle length
        AffineTransform3D other = new AffineTransform3D();
        other.set(0.8, -0.5, 0, 10.0,
                  0.4, 1.3, 0, 2.0,
                  0, 0, 1, 0);
        AffineGizmo linked = new AffineGizmo(other, -7, 3, 5.0);
        double[] origin = linked.position(Handle.CENTER);
        linked.startChange();

        // Rotation by 0.6 and scaling by 1.5 around the dragged gizmo center
        AffineGizmo gizmo = new AffineGizmo(initial(), CX, CY, L);
        double[] o = gizmo.position(Handle.CENTER), s = gizmo.position(Handle.SIMILARITY);
        double c = 1.5 * Math.cos(0.6), sn = 1.5 * Math.sin(0.6);
        gizmo.startDrag(Handle.SIMILARITY, s[0], s[1]);
        gizmo.drag(o[0] + c * (s[0] - o[0]) - sn * (s[1] - o[1]), o[1] + sn * (s[0] - o[0]) + c * (s[1] - o[1]), false);
        linked.applyChange(gizmo.getDragChange());
        gizmo.endDrag();

        // The linked gizmo turned and scaled the same way, around its own center
        AffineTransform3D rs = new AffineTransform3D();
        rs.set(c, -sn, 0, 0, sn, c, 0, 0, 0, 0, 1, 0);
        AffineTransform3D expected = other.copy().preConcatenate(translation(-origin[0], -origin[1]))
                .preConcatenate(rs).preConcatenate(translation(origin[0], origin[1]));
        assertEquals(expected, linked.getTransform());

        // Then a translation of (2, -1), which applies on top of the previous change
        double[] center = gizmo.position(Handle.CENTER);
        gizmo.startDrag(Handle.CENTER, center[0], center[1]);
        gizmo.drag(center[0] + 2, center[1] - 1, false);
        linked.startChange();
        linked.applyChange(gizmo.getDragChange());
        assertEquals(expected.preConcatenate(translation(2, -1)), linked.getTransform());
        Assert.assertTrue(linked.isChanged());
    }

    @Test
    public void axisChangeIsLinkedAlongTheImageAxes() {
        // Stretching the x axis of an identity by 2 stretches the rotated gizmo along the image x axis
        AffineGizmo gizmo = new AffineGizmo(new AffineTransform3D(), 0, 0, 1);
        AffineTransform3D rotated = new AffineTransform3D();
        rotated.rotate(2, Math.PI / 2);
        AffineGizmo linked = new AffineGizmo(rotated, 0, 0, 1);
        linked.startChange();
        gizmo.startDrag(Handle.X_AXIS, 1, 0);
        gizmo.drag(2, 0, false);
        Assert.assertArrayEquals(new double[]{2, 0, 0, 1, 0, 0}, gizmo.getDragChange(), 1e-12);
        linked.applyChange(gizmo.getDragChange());
        AffineTransform3D m = linked.getTransform();
        // U = (0, 1) stays, V = (-1, 0) becomes (-2, 0)
        Assert.assertArrayEquals(new double[]{0, -2, 1, 0}, new double[]{m.get(0, 0), m.get(0, 1), m.get(1, 0), m.get(1, 1)}, 1e-12);
    }

    @Test
    public void dragChangeWithoutDragOrFromCollinearAxes() {
        AffineGizmo gizmo = new AffineGizmo(initial(), CX, CY, L);
        Assert.assertNull(gizmo.getDragChange());
        Assert.assertFalse(gizmo.isChanged());

        // Invertible, but its xy block is singular
        AffineTransform3D flat = new AffineTransform3D();
        flat.set(1, 0, 0, 0,
                 0, 0, 1, 0,
                 0, 1, 0, 0);
        gizmo = new AffineGizmo(flat, 0, 0, 1);
        gizmo.startDrag(Handle.CENTER, 0, 0);
        gizmo.drag(3, 4, false);
        Assert.assertArrayEquals("translation only: defined", new double[]{1, 0, 0, 1, 3, 4}, gizmo.getDragChange(), 1e-12);
        gizmo.startDrag(Handle.Y_AXIS, 0, 0);
        gizmo.drag(0, 1, false);
        Assert.assertNull("axes changed from collinear axes: undefined", gizmo.getDragChange());
    }

    /** Drags a handle by (dx, dy), grabbing it at a distance grab from its center */
    static AffineGizmo drag(Handle handle, double dx, double dy, double grab, boolean constrained) {
        AffineGizmo gizmo = new AffineGizmo(initial(), CX, CY, L);
        double[] p = gizmo.position(handle);
        gizmo.startDrag(handle, p[0] + grab, p[1] - grab);
        gizmo.drag(p[0] + grab + dx, p[1] - grab + dy, constrained);
        return gizmo;
    }

    static AffineTransform3D translation(double x, double y) {
        AffineTransform3D t = new AffineTransform3D();
        t.translate(x, y, 0);
        return t;
    }

    static double[] apply(AffineTransform3D m, double x, double y) {
        double[] p = {x, y, 0};
        m.apply(p, p);
        return new double[]{p[0], p[1]};
    }

    static void assertEquals(AffineTransform3D expected, AffineTransform3D actual) {
        Assert.assertArrayEquals(expected.getRowPackedCopy(), actual.getRowPackedCopy(), 1e-12);
    }

    /** Compares the xy block and xy translation, and checks the z row and column are those of the initial transform */
    static void assertInPlaneEquals(AffineTransform3D expected, AffineTransform3D actual) {
        for (int col : new int[]{0, 1, 3}) {
            for (int row = 0; row < 2; row++) {
                Assert.assertEquals("m" + row + col, expected.get(row, col), actual.get(row, col), 1e-12);
            }
        }
        for (int i = 0; i < 4; i++) {
            Assert.assertEquals(initial().get(2, i), actual.get(2, i), 1e-12);
        }
        Assert.assertEquals(initial().get(0, 2), actual.get(0, 2), 1e-12);
        Assert.assertEquals(initial().get(1, 2), actual.get(1, 2), 1e-12);
    }

}
