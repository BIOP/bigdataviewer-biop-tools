package ch.epfl.biop.bdv.gui.graphicalhandle;

import bdv.viewer.SynchronizedViewerState;
import net.imglib2.RealPoint;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

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

    public XYRectangleGraphicalHandle(Behaviours behaviours,
                                      TriggerBehaviourBindings bindings,
                                      String nameMap,
                                      final SynchronizedViewerState state,
                                      Supplier<RealPoint> globalCoord,
                                      Supplier<Double> sizeX,
                                      Supplier<Double> sizeY,
                                      Supplier<Integer[]> color) {
        super(null, behaviours, bindings, nameMap);
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

    public XYRectangleGraphicalHandle(final SynchronizedViewerState state,
                                      Supplier<RealPoint> globalCoord,
                                      Supplier<Double> sizeX,
                                      Supplier<Double> sizeY,
                                      Supplier<Integer[]> color) {
        this(null, null, null, state, globalCoord, sizeX, sizeY, color);
    }

    Integer r = 4;

    @Override
    protected void enabledDraw(Graphics2D g) {
        RealPoint pt = new RealPoint(3);
        RealPoint ori = globalCoord.get();
        state.getViewerTransform().apply(ori, pt);
        int[] centerPos = new int[]{(int) pt.getDoublePosition(0), (int) pt.getDoublePosition(1), 0};
        Integer[] c = color.get();
        g.setColor(new Color(c[0], c[1], c[2], c[3]));
        double displayR = r;
        if (this.mouseAbove) {
            displayR= displayR*1.6;
        }
        g.fillOval((int) (centerPos[0] - displayR), (int) (centerPos[1] - displayR), (int) (2*displayR), (int) (2*displayR));

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
        // No change
        enabledDraw(g);
    }

    @Override
    boolean isPresentAt(int x, int y) {
        int[] pos = getScreenCoordinates();
        double r = (double)(this.r);
        double d = (pos[0]-x)*(pos[0]-x)+(pos[1]-y)*(pos[1]-y);
        return d<(r*r);
    }

    @Override
    public int[] getScreenCoordinates() {
        RealPoint pt = new RealPoint(3);
        state.getViewerTransform().apply(globalCoord.get(), pt);
        return new int[]{(int) pt.getDoublePosition(0), (int) pt.getDoublePosition(1), 0};
    }

}
