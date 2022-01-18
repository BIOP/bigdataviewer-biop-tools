package bdv.util.source.alpha;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import net.imglib2.*;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

/**
 * Alpha Source for a source which is backed by a RAI and an AffineTransform
 *
 * Typically, any source which is not Warped
 *
 */

public class AlphaSourceRAI extends AlphaSource {

    public AlphaSourceRAI(Source<?> origin) {
        super(origin);
    }

    public AlphaSourceRAI(Source<?> origin, float alpha) {
        super(origin, alpha);
    }

    @Override
    public RandomAccessibleInterval<FloatType> getSource(int t, int level) {
        final float finalAlpha = alpha;

        final RandomAccessible< FloatType > randomAccessible =
                new FunctionRandomAccessible<>( 3, () -> (loc, out) -> out.setReal( finalAlpha ), FloatType::new );

        return Views.interval(randomAccessible, origin.getSource(t, level));
    }

    @Override
    public RealRandomAccessible<FloatType> getInterpolatedSource(int t, int level, Interpolation method) {
        ExtendedRandomAccessibleInterval<FloatType, RandomAccessibleInterval< FloatType >>
                eView = Views.extendZero(getSource( t, level ));
        RealRandomAccessible< FloatType > realRandomAccessible = Views.interpolate( eView, interpolators.get(Interpolation.NEARESTNEIGHBOR) );
        return realRandomAccessible;
    }

    @Override
    public boolean intersectBox(AffineTransform3D affineTransform, Interval cell, int timepoint) {
        // Let's try a simplebox computation and see if there are intersections.
        Box3D box_cell = new Box3D(affineTransform, cell);
        AffineTransform3D affineTransform3D = new AffineTransform3D();
        getSourceTransform(timepoint,0,affineTransform3D);
        Box3D box_this = new Box3D(affineTransform3D, this.getSource(timepoint,0));
        return box_this.intersects(box_cell);
    }

    public static class Box3D {

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        public Box3D(AffineTransform3D affineTransform, Interval cell) {
            // pXYZ
            RealPoint p000 = new RealPoint(cell.min(0)-1, cell.min(1)-1, cell.min(2)-1);
            RealPoint p001 = new RealPoint(cell.min(0)-1, cell.min(1)-1, cell.max(2)+1);
            RealPoint p010 = new RealPoint(cell.min(0)-1, cell.max(1)+1, cell.min(2)-1);
            RealPoint p011 = new RealPoint(cell.min(0)-1, cell.max(1)+1, cell.max(2)+1);
            RealPoint p100 = new RealPoint(cell.max(0)+1, cell.min(1)-1, cell.min(2)-1);
            RealPoint p101 = new RealPoint(cell.max(0)+1, cell.min(1)-1, cell.max(2)+1);
            RealPoint p110 = new RealPoint(cell.max(0)+1, cell.max(1)+1, cell.min(2)-1);
            RealPoint p111 = new RealPoint(cell.max(0)+1, cell.max(1)+1, cell.max(2)+1);

            affineTransform.apply(p000,p000);
            affineTransform.apply(p001,p001);
            affineTransform.apply(p010,p010);
            affineTransform.apply(p011,p011);
            affineTransform.apply(p100,p100);
            affineTransform.apply(p101,p101);
            affineTransform.apply(p110,p110);
            affineTransform.apply(p111,p111);

            updatePoint(p000);
            updatePoint(p001);
            updatePoint(p010);
            updatePoint(p011);
            updatePoint(p100);
            updatePoint(p101);
            updatePoint(p110);
            updatePoint(p111);
        }

        private void updatePoint(RealPoint pt) {
            double px = pt.getDoublePosition(0);
            double py = pt.getDoublePosition(1);
            double pz = pt.getDoublePosition(2);
            if (px<minX) minX = px;
            if (px>maxX) maxX = px;
            if (py<minY) minY = py;
            if (py>maxY) maxY = py;
            if (pz<minZ) minZ = pz;
            if (pz>maxZ) maxZ = pz;
        }

        public boolean intersects(Box3D other) {
            //System.out.println(other);
            //System.out.println(this);
            if (other.maxX<minX) return false;
            if (other.minX>maxX) return false;
            if (other.maxY<minY) return false;
            if (other.minY>maxY) return false;
            if (other.maxZ<minZ) return false;
            if (other.minZ>maxZ) return false;
            return true;
        }

        public String toString() {
            return "("+minX+":"+maxX+")"+"("+minY+":"+maxY+")"+"("+minZ+":"+maxZ+")";
        }
    }

}
