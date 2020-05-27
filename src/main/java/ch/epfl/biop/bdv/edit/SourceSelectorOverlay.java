package ch.epfl.biop.bdv.edit;

import bdv.tools.boundingbox.TransformedBox;
import bdv.ui.SourcesTransferable;
import bdv.util.BdvOverlay;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerStateChange;
import bdv.viewer.ViewerStateChangeListener;
import bdv.tools.boundingbox.RenderBoxHelper;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.util.Behaviours;
import sc.fiji.bdvpg.scijava.services.ui.swingdnd.BdvTransferHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.util.*;
import java.util.List;

import static bdv.viewer.ViewerStateChange.NUM_SOURCES_CHANGED;
import static bdv.viewer.ViewerStateChange.VISIBILITY_CHANGED;

/**
 * Works with {}
 */

public class SourceSelectorOverlay extends BdvOverlay implements ViewerStateChangeListener {

    final ViewerPanel viewer;

    boolean isCurrentlySelecting = false;

    int xCurrentSelectStart, yCurrentSelectStart, xCurrentSelectEnd, yCurrentSelectEnd;

    Set<SourceAndConverter<?>> selectedSources = new HashSet<>();

    private int canvasWidth;

    private int canvasHeight;

    private List<SourceBoxOverlay> sourcesBoxOverlay = new ArrayList<>();

    Map<String, OverlayStyle> styles = new HashMap<>();

    List<SelectedSourcesListener> selectedSourceListeners = new ArrayList<>();

    public SourceSelectorOverlay(ViewerPanel viewer) {
        this.viewer = viewer;
        viewer.state().changeListeners().add(this);
        updateSourceList();
        styles.put("DEFAULT", new DefaultOverlayStyle());
        styles.put("SELECTED", new SelectedOverlayStyle());

        if (viewer.getTransferHandler() instanceof BdvTransferHandler) {
            // Custom Drag support
            BdvTransferHandler handler = (BdvTransferHandler) viewer.getTransferHandler();
            handler.setTransferableFunction(c -> new SourcesTransferable(this.getSelectedSources()));

        }
    }

    public void addSelectionBehaviours(Behaviours behaviours) {
        behaviours.behaviour( new RectangleSelectSourcesBehaviour( "SELECT" ), "select-set-sources", new String[] { "button1" });
        behaviours.behaviour( new RectangleSelectSourcesBehaviour( "ADD" ), "select-add-sources", new String[] { "shift button1" });
        behaviours.behaviour( new RectangleSelectSourcesBehaviour( "REMOVE" ), "select-remove-sources", new String[] { "ctrl button1" });

        if (viewer.getTransferHandler() instanceof BdvTransferHandler) {
            behaviours.behaviour(new DragNDSourcesBehaviour(), "drag-selected-sources", new String[]{"alt button1"});
        }
    }

    public Set<SourceAndConverter<?>> getSelectedSources() {
        synchronized (selectedSources) {
            HashSet<SourceAndConverter<?>> copySelectedSources = new HashSet<>();
            copySelectedSources.addAll(selectedSources);
            return copySelectedSources;
        }
    }

    public Map<String, OverlayStyle> getStyles() {
        return styles;
    }

    public synchronized void startCurrentSelection(int x, int y) {
        xCurrentSelectStart = x;
        yCurrentSelectStart = y;
    }

    public synchronized void updateCurrentSelection(int xCurrent, int yCurrent) {
        xCurrentSelectEnd = xCurrent;
        yCurrentSelectEnd = yCurrent;
        isCurrentlySelecting = true;
    }

    public synchronized void endCurrentSelection(int x, int y, String mode) {
        xCurrentSelectEnd = x;
        yCurrentSelectEnd = y;
        isCurrentlySelecting = false;
        // Selection is done : but we need to access the trigger keys to understand what's happening
        Set<SourceAndConverter<?>> currentSelection = this.getLastSelectedSources();
        switch(mode) {
            case "SELECT" :
                selectedSources.clear();
                selectedSources.addAll(currentSelection);
            break;
            case "ADD" :
                selectedSources.addAll(currentSelection);
            break;
            case "REMOVE" :
                selectedSources.removeAll(currentSelection);
            break;
        }
        // Necessary when double clicking without mouse movement
        viewer.requestRepaint();

        if (currentSelection.size()>0) {
            selectedSourceListeners.forEach(listener -> listener.updateSelectedSources(getSelectedSources()));
        }

    }

    public Rectangle getCurrentSelectionRectangle() {
        int x0, y0, w, h;
        if (xCurrentSelectStart>xCurrentSelectEnd) {
            x0 = xCurrentSelectEnd;
            w = xCurrentSelectStart-xCurrentSelectEnd;
        } else {
            x0 = xCurrentSelectStart;
            w = xCurrentSelectEnd-xCurrentSelectStart;
        }
        if (yCurrentSelectStart>yCurrentSelectEnd) {
            y0 = yCurrentSelectEnd;
            h = yCurrentSelectStart-yCurrentSelectEnd;
        } else {
            y0 = yCurrentSelectStart;
            h = yCurrentSelectEnd-yCurrentSelectStart;
        }
        // Hack : allows selection on double or single click
        if (w==0) w = 1;
        if (h==0) h = 1;
        return new Rectangle(x0, y0, w, h);
    }

    private Set<SourceAndConverter<?>> getLastSelectedSources() {
        Set<SourceAndConverter<?>> lastSelected = new HashSet<>();

        final RenderBoxHelper rbh = new RenderBoxHelper();

        // We need to find whether a rectangle in real space intersects a box in 3d -> Makes use of the work previously done in RenderBoxHelper
        for (SourceBoxOverlay sbo : sourcesBoxOverlay) {
          //
            AffineTransform3D viewerTransform = new AffineTransform3D();
            viewer.state().getViewerTransform(viewerTransform);
            AffineTransform3D transform = new AffineTransform3D();
            synchronized ( viewerTransform )
            {
                sbo.getTransform( transform );
                transform.preConcatenate( viewerTransform );
            }
            final double ox = canvasWidth / 2;
            final double oy = canvasHeight / 2;

            rbh.setOrigin( ox, oy );
            rbh.setScale( 1 );

            final GeneralPath front = new GeneralPath();
            final GeneralPath back = new GeneralPath();
            final GeneralPath intersection = new GeneralPath();

            rbh.renderBox( sbo.getInterval(), transform, front, back, intersection );

            Rectangle r = getCurrentSelectionRectangle();

            if (intersection.intersects(r)||intersection.contains(r)) {
                lastSelected.add(sbo.sac);
            }
        }
        return lastSelected;
    }

    @Override
    public synchronized void draw(Graphics2D g) {
        for (SourceBoxOverlay source : sourcesBoxOverlay) {
            source.drawBoxOverlay(g);
        }

        if (isCurrentlySelecting) {
            g.setStroke( styles.get("SELECTED").getNormalStroke() );
            g.setPaint( styles.get("SELECTED").getBackColor() );
            g.draw(getCurrentSelectionRectangle());
        }

    }

    @Override
    public void setCanvasSize( final int width, final int height ) {
        this.canvasWidth = width;
        this.canvasHeight = height;
    }

    private synchronized void updateSourceList() {
        sourcesBoxOverlay.clear();
        for (SourceAndConverter sac : viewer.state().getVisibleSources()) {
            if (sac.getSpimSource().getSource(0,0)!=null) { // TODO : fix hack to avoid dirty overlay filter
                sourcesBoxOverlay.add(new SourceBoxOverlay(sac));
            }
        }
        // Removes potentially selected source which has been removed from bdv
        Set<SourceAndConverter> leftOvers = new HashSet<>();
        leftOvers.addAll(selectedSources);
        leftOvers.removeAll(viewer.state().getVisibleSources());
        selectedSources.removeAll(leftOvers);
        if (leftOvers.size()>0) {
            selectedSourceListeners.forEach(listener -> listener.updateSelectedSources(getSelectedSources()));
        }
    }

    @Override
    public void viewerStateChanged(ViewerStateChange change) {
        if (change.equals(NUM_SOURCES_CHANGED)||change.equals(VISIBILITY_CHANGED)) {
            updateSourceList();
        }
    }

    public void addSelectedSourcesListener(SelectedSourcesListener selectedSourcesListener) {
        selectedSourceListeners.add(selectedSourcesListener);
    }

    public void removeSelectedSourcesListener(SelectedSourcesListener selectedSourcesListener) {
        selectedSourceListeners.remove(selectedSourcesListener);
    }



    class SourceBoxOverlay implements TransformedBox {

        final SourceAndConverter<?> sac;

        final RenderBoxHelper rbh;

        public SourceBoxOverlay(SourceAndConverter sac) {
            this.sac = sac;
            rbh = new RenderBoxHelper();
        }

        public void drawBoxOverlay(Graphics2D graphics) {
            OverlayStyle os;

            if (selectedSources.contains(sac)) {
                os = styles.get("SELECTED");
            } else {
                os = styles.get("DEFAULT");
            }
            final GeneralPath front = new GeneralPath();
            final GeneralPath back = new GeneralPath();
            final GeneralPath intersection = new GeneralPath();

            final RealInterval interval = getInterval();
            final double ox = canvasWidth / 2;
            final double oy = canvasHeight / 2;
            AffineTransform3D viewerTransform = new AffineTransform3D();
            viewer.state().getViewerTransform(viewerTransform);
            AffineTransform3D transform = new AffineTransform3D();
            synchronized ( viewerTransform )
            {
                getTransform( transform );
                transform.preConcatenate( viewerTransform );
            }
            rbh.setOrigin( ox, oy );
            rbh.setScale( 1 );
            rbh.renderBox( interval, transform, front, back, intersection );

            graphics.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );

            graphics.setStroke( os.getNormalStroke() );
            graphics.setPaint( os.getBackColor() );
            graphics.draw( back );

            graphics.setPaint( os.getIntersectionFillColor() );
            graphics.fill( intersection );

            graphics.setPaint( os.getIntersectionColor() );
            graphics.setStroke( os.getIntersectionStroke() );
            graphics.draw( intersection );
        }

        @Override
        public RealInterval getInterval() {
            long[] dims = new long[3];
            sac.getSpimSource().getSource(viewer.state().getCurrentTimepoint(),0).dimensions(dims);
            return new FinalRealInterval(new double[]{-0.5,-0.5,-0.5}, new double[]{dims[0]-0.5, dims[1]-0.5, dims[2]-0.5});
        }

        @Override
        public void getTransform(AffineTransform3D transform) {
            sac.getSpimSource().getSourceTransform(viewer.state().getCurrentTimepoint(),0,transform);
        }
    }

    /**
     * STYLES
     */

    public interface OverlayStyle {

        Color getBackColor();

        Color getFrontColor();

        Color getIntersectionColor();

        Color getIntersectionFillColor();

        Stroke getNormalStroke();

        Stroke getIntersectionStroke();


    }

    public class DefaultOverlayStyle implements SourceSelectorOverlay.OverlayStyle {
        Color backColor = new Color( 0x00994499 );

        Color frontColor = Color.GREEN;

        Color intersectionFillColor = new Color(0x32994499, true );

        Stroke normalStroke = new BasicStroke();

        Stroke intersectionStroke = new BasicStroke( 1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 10f, 10f }, 0f );

        Color intersectionColor = Color.WHITE.darker();

        public Color getBackColor() {
            return backColor;
        }

        public Color getFrontColor() {
            return frontColor;
        }

        @Override
        public Color getIntersectionColor() {
            return intersectionColor;
        }

        public Color getIntersectionFillColor() {
            return intersectionFillColor;
        }

        public Stroke getNormalStroke() {
            return normalStroke;
        }

        @Override
        public Stroke getIntersectionStroke() {
            return intersectionStroke;
        }
    }

    class SelectedOverlayStyle implements SourceSelectorOverlay.OverlayStyle {
        Color backColor = new Color(0xF7BF18);

        Color frontColor = Color.GREEN;

        Color intersectionFillColor = new Color(0x30B66A00, true );

        Stroke normalStroke = new BasicStroke();

        Stroke intersectionStroke = new BasicStroke( 1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 10f, 10f }, 0f );

        Color intersectionColor = Color.WHITE.darker();

        public Color getBackColor() {
            return backColor;
        }

        public Color getFrontColor() {
            return frontColor;
        }

        @Override
        public Color getIntersectionColor() {
            return intersectionColor;
        }

        public Color getIntersectionFillColor() {
            return intersectionFillColor;
        }

        public Stroke getNormalStroke() {
            return normalStroke;
        }

        @Override
        public Stroke getIntersectionStroke() {
            return intersectionStroke;
        }

    }

    public class RectangleSelectSourcesBehaviour implements DragBehaviour {

        final String mode;

        public RectangleSelectSourcesBehaviour(String mode) {
            this.mode = mode;
        }

        @Override
        public void init(int x, int y) {
            startCurrentSelection(x,y);
            viewer.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        }

        @Override
        public void drag(int x, int y) {
            updateCurrentSelection(x,y);
            viewer.paint(); // TODO : understand how to remove it from here
        }

        @Override
        public void end(int x, int y) {
            endCurrentSelection(x,y,mode);
            viewer.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
    }

    public class DragNDSourcesBehaviour implements DragBehaviour {

        @Override
        public void init(int x, int y) {
            viewer.getTransferHandler().exportAsDrag(viewer, new MouseEvent(viewer, 0, 0, 0, 100, 100, 1, false), TransferHandler.MOVE);
        }

        @Override
        public void drag(int x, int y) {

        }

        @Override
        public void end(int x, int y) {

        }
    }

}
