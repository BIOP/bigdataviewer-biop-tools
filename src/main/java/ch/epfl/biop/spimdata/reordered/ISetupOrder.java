package ch.epfl.biop.spimdata.reordered;

import mpicbg.spim.data.sequence.ViewId;

/**
 * See {@link ReorderedImageLoader} for explanation about this interface
 */
public interface ISetupOrder {
    ReorderedImageLoader.SpimDataViewId getOriginalLocation(ViewId viewId);
    void initialize();
}
