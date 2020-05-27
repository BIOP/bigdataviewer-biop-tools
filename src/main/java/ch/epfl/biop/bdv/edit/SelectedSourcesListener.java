package ch.epfl.biop.bdv.edit;

import bdv.viewer.SourceAndConverter;

import java.util.Collection;

public interface SelectedSourcesListener {

    void updateSelectedSources(Collection<SourceAndConverter<?>> selectedSources);

}
