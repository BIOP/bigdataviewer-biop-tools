package sc.fiji.bdvpg.bdv.supplier.alpha;

import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.supplier.DefaultBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.IBdvSupplier;
import sc.fiji.persist.IClassRuntimeAdapter;

/**
 * For serialization of {@link DefaultBdvSupplier} objects
 */

@Plugin(type = IClassRuntimeAdapter.class)
public class AlphaBdvSupplierAdapter implements IClassRuntimeAdapter<IBdvSupplier, AlphaBdvSupplier> {

    @Override
    public Class<? extends IBdvSupplier> getBaseClass() {
        return IBdvSupplier.class;
    }

    @Override
    public Class<? extends AlphaBdvSupplier> getRunTimeClass() {
        return AlphaBdvSupplier.class;
    }

    public boolean useCustomAdapter() {return false;}

}
