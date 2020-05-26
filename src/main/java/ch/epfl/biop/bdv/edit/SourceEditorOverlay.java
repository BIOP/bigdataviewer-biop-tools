package ch.epfl.biop.bdv.edit;

import bdv.tools.boundingbox.TransformedBox;
import bdv.util.BdvHandle;
import bdv.util.BdvOverlay;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerStateChange;
import bdv.viewer.ViewerStateChangeListener;
import bdv.tools.boundingbox.RenderBoxHelper;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static bdv.viewer.ViewerStateChange.NUM_SOURCES_CHANGED;
import static bdv.viewer.ViewerStateChange.VISIBILITY_CHANGED;

public class SourceEditorOverlay extends BdvOverlay implements ViewerStateChangeListener {

    final BdvHandle bdvh;

    public SourceEditorOverlay(BdvHandle bdvh) {
        this.bdvh = bdvh;
        bdvh.getViewerPanel().state().changeListeners().add(this);
        for (SourceAndConverter sac : bdvh.getViewerPanel().state().getVisibleSources()) {
            sourcesBoxOverlay.add(new SourceBoxOverlay(sac, "DEFAULT") );
        }

        style.put("DEFAULT", new OverlayStyle());
        style.put("SELECTED", new SelectedOverlayStyle());
    }

    private int canvasWidth;

    private int canvasHeight;

    private List<SourceBoxOverlay> sourcesBoxOverlay = new ArrayList<>();

    @Override
    public synchronized void draw(Graphics2D g) {

        for (SourceBoxOverlay source : sourcesBoxOverlay) {
            source.drawBoxOverlay(g);
        }
    }

    @Override
    public void setCanvasSize( final int width, final int height )
    {
        this.canvasWidth = width;
        this.canvasHeight = height;
    }

    private synchronized void updateSourceList() {
        //System.err.println("Number of sources changed!");
        //throw new UnsupportedOperationException();

        sourcesBoxOverlay.clear();
        for (SourceAndConverter sac : bdvh.getViewerPanel().state().getVisibleSources()) {
            if (sac.getSpimSource().getSource(0,0)!=null) { // TODO : fix hack to avoid dirty overlay filter
                sourcesBoxOverlay.add(new SourceBoxOverlay(sac, "DEFAULT"));
            }
        }
    }

    @Override
    public void viewerStateChanged(ViewerStateChange change) {
        if (change.equals(NUM_SOURCES_CHANGED)||change.equals(VISIBILITY_CHANGED)) {
            updateSourceList();
        }
    }

    class SourceBoxOverlay implements TransformedBox {

        final SourceAndConverter<?> sac;

        final RenderBoxHelper rbh;

        String styleOverlay;

        boolean showGizmo = false; // TODO Blender style Gizmo

        public SourceBoxOverlay(SourceAndConverter sac, String styleOverlay) {
            this.sac = sac;
            rbh = new RenderBoxHelper();
            this.styleOverlay = styleOverlay;
        }

        public void setStyleOverlay(String styleOverlay) {
            this.styleOverlay = styleOverlay;
        }

        public void drawBoxOverlay(Graphics2D graphics) {

            OverlayStyle os = style.get(styleOverlay);


            final GeneralPath front = new GeneralPath();
            final GeneralPath back = new GeneralPath();
            final GeneralPath intersection = new GeneralPath();

            final RealInterval interval = getInterval();
            final double ox = canvasWidth / 2;
            final double oy = canvasHeight / 2;
            AffineTransform3D viewerTransform = new AffineTransform3D();
            bdvh.getViewerPanel().state().getViewerTransform(viewerTransform);
            AffineTransform3D transform = new AffineTransform3D();
            synchronized ( viewerTransform )
            {
                getTransform( transform );
                transform.preConcatenate( viewerTransform );
            }
            //rbh.setPerspectiveProjection( perspective > 0 );
            //rbh.setDepth( perspective * sourceSize );
            rbh.setOrigin( ox, oy );
            rbh.setScale( 1 );
            rbh.renderBox( interval, transform, front, back, intersection );

            graphics.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );

            //if ( displayMode.get() == FULL )
            {
                graphics.setStroke( os.normalStroke );
                graphics.setPaint( os.backColor );
                graphics.draw( back );
            }

            //if ( fillIntersection )
            {
                graphics.setPaint( os.intersectionFillColor );
                graphics.fill( intersection );
            }

            graphics.setPaint( os.intersectionColor );
            graphics.setStroke( os.intersectionStroke );
            graphics.draw( intersection );


            /*if ( displayMode.get() == FULL )

            boolean showCornerHandles = true;
            {

                graphics.setStroke( normalStroke );
                graphics.setPaint( frontColor );
                graphics.draw( front );

                if ( showCornerHandles )
                {
                    final int id = 0;//getHighlightedCornerIndex();
                    if ( id >= 0 )
                    {
                        final double[] p = rbh.projectedCorners[ id ];
                        final Ellipse2D cornerHandle = new Ellipse2D.Double(
                                p[ 0 ] - HANDLE_RADIUS,
                                p[ 1 ] - HANDLE_RADIUS,
                                2 * HANDLE_RADIUS, 2 * HANDLE_RADIUS );
                        final double z = renderBoxHelper.corners[ cornerId ][ 2 ];
                        final Color cornerColor = ( z > 0 ) ? backColor : frontColor;

                        graphics.setColor( cornerColor );
                        graphics.fill( cornerHandle );
                        graphics.setColor( cornerColor.darker().darker() );
                        graphics.draw( cornerHandle );
                    }
                }
            }*/
        }

        @Override
        public RealInterval getInterval() {
            long[] dims = new long[3];
            sac.getSpimSource().getSource(bdvh.getViewerPanel().state().getCurrentTimepoint(),0).dimensions(dims);
            return new FinalRealInterval(new double[]{-0.5,-0.5,-0.5}, new double[]{dims[0]-0.5, dims[1]-0.5, dims[2]-0.5});
        }

        @Override
        public void getTransform(AffineTransform3D transform) {
            sac.getSpimSource().getSourceTransform(bdvh.getViewerPanel().state().getCurrentTimepoint(),0,transform);
        }
    }


    Map<String, OverlayStyle> style = new HashMap<>();

    class OverlayStyle {
        Color backColor = new Color( 0x00994499 );

        Color frontColor = Color.GREEN;

        Color intersectionFillColor = new Color( 0x88994499, true );

        Stroke normalStroke = new BasicStroke();

        Stroke intersectionStroke = new BasicStroke( 1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 10f, 10f }, 0f );

        Color intersectionColor = Color.WHITE.darker();
    }

    class SelectedOverlayStyle extends OverlayStyle {
        Color backColor = new Color(0xF7BF18);

        Color frontColor = Color.GREEN;

        Color intersectionFillColor = new Color(0xE5B66A00, true );

        Stroke normalStroke = new BasicStroke();

        Stroke intersectionStroke = new BasicStroke( 1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 10f, 10f }, 0f );

        Color intersectionColor = Color.WHITE.darker();
    }



}
