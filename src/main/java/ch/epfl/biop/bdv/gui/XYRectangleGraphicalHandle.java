package ch.epfl.biop.bdv.gui;

import bdv.viewer.SynchronizedViewerState;
import net.imglib2.RealPoint;

import java.awt.*;
import java.util.function.Supplier;

public class XYRectangleGraphicalHandle extends GraphicalHandle{

    final SynchronizedViewerState state;
    final Supplier<RealPoint> globalCoord;
    final Supplier<Double> sizeX;
    final Supplier<Double> sizeY;
    final Supplier<Integer[]> color;

    RealPoint pta, ptb, ptc, ptd;

    Stroke normalStroke = new BasicStroke();

    public XYRectangleGraphicalHandle(final SynchronizedViewerState state,
                                      Supplier<RealPoint> globalCoord,
                                      Supplier<Double> sizeX,
                                      Supplier<Double> sizeY,
                                      Supplier<Integer[]> color) {
        super(null, null, null, null);
        this.state = state;
        this.globalCoord = globalCoord;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.color = color;
        pta = new RealPoint(3);
        ptb = new RealPoint(3);
        ptc = new RealPoint(3);
        ptd = new RealPoint(3);
    }

    @Override
    protected void enabledDraw(Graphics2D g) {
        Integer r = 4;
        RealPoint pt = new RealPoint(3);
        RealPoint ori = globalCoord.get();
        state.getViewerTransform().apply(ori, pt);
        int[] centerPos = new int[]{(int) pt.getDoublePosition(0), (int) pt.getDoublePosition(1), 0};
        Integer[] c = color.get();
        g.setColor(new Color(c[0], c[1], c[2], c[3]));
        g.fillOval(centerPos[0] - r, centerPos[1] - r, 2*r, 2*r);

        double sx = sizeX.get();
        double sy = sizeY.get();

        pta.setPosition(ori);
        pta.setPosition(pta.getDoublePosition(0)+sx / 2,0);
        pta.setPosition(pta.getDoublePosition(1)+sy / 2,1);

        ptb.setPosition(ori);
        ptb.setPosition(ptb.getDoublePosition(0)+sx / 2,0);
        ptb.setPosition(ptb.getDoublePosition(1)-sy / 2,1);

        ptc.setPosition(ori);
        ptc.setPosition(ptc.getDoublePosition(0)-sx / 2,0);
        ptc.setPosition(ptc.getDoublePosition(1)-sy / 2,1);

        ptd.setPosition(ori);
        ptd.setPosition(ptd.getDoublePosition(0)-sx / 2,0);
        ptd.setPosition(ptd.getDoublePosition(1)+sy / 2,1);

        state.getViewerTransform().apply(pta,pta);
        state.getViewerTransform().apply(ptb,ptb);
        state.getViewerTransform().apply(ptc,ptc);
        state.getViewerTransform().apply(ptd,ptd);

        g.setStroke(normalStroke);

        g.drawLine((int) pta.getDoublePosition(0),(int)  pta.getDoublePosition(1),
                   (int) ptb.getDoublePosition(0),(int)  ptb.getDoublePosition(1));
        g.drawLine((int) ptc.getDoublePosition(0),(int)  ptc.getDoublePosition(1),
                   (int) ptb.getDoublePosition(0),(int)  ptb.getDoublePosition(1));
        g.drawLine((int) ptc.getDoublePosition(0),(int)  ptc.getDoublePosition(1),
                   (int) ptd.getDoublePosition(0),(int)  ptd.getDoublePosition(1));
        g.drawLine((int) pta.getDoublePosition(0),(int)  pta.getDoublePosition(1),
                   (int) ptd.getDoublePosition(0),(int)  ptd.getDoublePosition(1));

    }

    @Override
    protected void disabledDraw(Graphics2D g) {
        // Don't draw
    }

    @Override
    boolean isPresentAt(int x, int y) {
        return false;
    }

    @Override
    public int[] getScreenCoordinates() {
        RealPoint pt = new RealPoint(3);
        state.getViewerTransform().apply(globalCoord.get(), pt);
        return new int[]{(int) pt.getDoublePosition(0), (int) pt.getDoublePosition(1), 0};
    }

}
