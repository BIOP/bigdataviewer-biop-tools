package bdv.util.projector.alpha;

import bdv.viewer.render.AccumulateProjectorFactory;
import org.scijava.plugin.Plugin;
import sc.fiji.persist.IClassRuntimeAdapter;

/**
 * For serialization of {@link LayerAlphaProjectorFactory} objects
 * Runtime adapter for {@link AccumulateProjectorFactory}
 *
 * Used for the serialization of {@link sc.fiji.bdvpg.bdv.supplier.alpha.AlphaSerializableBdvOptions}
 *
 */

@Plugin(type = IClassRuntimeAdapter.class)
public class LayerAlphaIProjectorFactoryAdapter implements IClassRuntimeAdapter<AccumulateProjectorFactory, LayerAlphaIProjectorFactory> {
    @Override
    public Class<? extends AccumulateProjectorFactory> getBaseClass() {
        return AccumulateProjectorFactory.class;
    }

    @Override
    public Class<? extends LayerAlphaIProjectorFactory> getRunTimeClass() {
        return LayerAlphaIProjectorFactory.class;
    }

    public boolean useCustomAdapter() {
        return false;
    }
}
