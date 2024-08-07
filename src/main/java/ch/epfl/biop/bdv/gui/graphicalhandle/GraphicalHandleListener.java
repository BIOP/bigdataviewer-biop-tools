package ch.epfl.biop.bdv.gui.graphicalhandle;

public interface GraphicalHandleListener {

        void disabled(GraphicalHandle gh);

        void enabled(GraphicalHandle gh);

        void hover_in(GraphicalHandle gh);

        void hover_out(GraphicalHandle gh);

        //void clicked(GraphicalHandle gh);

        void created(GraphicalHandle gh);

        void removed(GraphicalHandle gh);

}