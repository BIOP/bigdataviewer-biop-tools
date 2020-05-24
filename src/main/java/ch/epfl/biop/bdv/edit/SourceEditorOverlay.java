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
import java.util.List;

import static bdv.viewer.ViewerStateChange.NUM_SOURCES_CHANGED;

public class SourceEditorOverlay extends BdvOverlay implements ViewerStateChangeListener {

    final BdvHandle bdvh;

    public SourceEditorOverlay(BdvHandle bdvh) {
        this.bdvh = bdvh;
        bdvh.getViewerPanel().state().changeListeners().add(this);
        for (SourceAndConverter sac : bdvh.getViewerPanel().state().getSources()) {
            selectedSources.add(new SelectedSource(sac));
        }
    }

    private int canvasWidth;

    private int canvasHeight;

    private List<SelectedSource> selectedSources = new ArrayList<>();

    @Override
    public synchronized void draw(Graphics2D g) {
        for (SelectedSource source : selectedSources) {
            //System.out.println(source.sac.getSpimSource().getName());
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
        System.err.println("Number of sources changed!");
        // TODO : remove the sources which are selected which could have been removed
        //throw new UnsupportedOperationException();

        selectedSources.clear();
        for (SourceAndConverter sac : bdvh.getViewerPanel().state().getSources()) {
            if (sac.getSpimSource().getSource(0,0)!=null) { // TODO : fix hack to avoid dirty overlay filter
                selectedSources.add(new SelectedSource(sac));
            }
        }
    }

    @Override
    public void viewerStateChanged(ViewerStateChange change) {
        if (change.equals(NUM_SOURCES_CHANGED)) {
            updateSourceList();
        }
    }


    private final Color backColor = new Color( 0x00994499 );

    private final Color frontColor = Color.GREEN;

    private final Stroke normalStroke = new BasicStroke();

    private final Stroke intersectionStroke = new BasicStroke( 1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 10f, 10f }, 0f );

    private final Color intersectionColor = Color.WHITE.darker();

    private Color intersectionFillColor = new Color( 0x88994499, true );

    class SelectedSource implements TransformedBox {
        final SourceAndConverter<?> sac;
        final RenderBoxHelper rbh;

        public SelectedSource(SourceAndConverter sac) {
            this.sac = sac;
            rbh = new RenderBoxHelper();
        }

        public void drawBoxOverlay(Graphics g) {
            final Graphics2D graphics = ( Graphics2D ) g;

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
                graphics.setStroke( normalStroke );
                graphics.setPaint( backColor );
                graphics.draw( back );
            }

            //if ( fillIntersection )
            {
                graphics.setPaint( intersectionFillColor );
                graphics.fill( intersection );
            }

            graphics.setPaint( intersectionColor );
            graphics.setStroke( intersectionStroke );
            graphics.draw( intersection );

            /*
            if ( displayMode.get() == FULL )
            {
                graphics.setStroke( normalStroke );
                graphics.setPaint( frontColor );
                graphics.draw( front );

                if ( showCornerHandles )
                {
                    final int id = getHighlightedCornerIndex();
                    if ( id >= 0 )
                    {
                        final double[] p = renderBoxHelper.projectedCorners[ id ];
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
            return new FinalRealInterval(new double[]{0,0,0}, new double[]{dims[0], dims[1], dims[2]});
        }

        @Override
        public void getTransform(AffineTransform3D transform) {
            sac.getSpimSource().getSourceTransform(bdvh.getViewerPanel().state().getCurrentTimepoint(),0,transform);
        }
    }
}
