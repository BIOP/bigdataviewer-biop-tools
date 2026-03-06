
package sc.fiji.bdvpg.bdv.supplier.grid;

import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.viewer.bdv.supplier.DefaultBdvSupplier;
import sc.fiji.bdvpg.viewer.bdv.supplier.IBdvSupplier;
import sc.fiji.persist.IClassRuntimeAdapter;

/**
 * For serialization of {@link DefaultBdvSupplier} objects
 */

@Plugin(type = IClassRuntimeAdapter.class)
public class GridBdvSupplierAdapter implements
	IClassRuntimeAdapter<IBdvSupplier, GridBdvSupplier>
{

	@Override
	public Class<? extends IBdvSupplier> getBaseClass() {
		return IBdvSupplier.class;
	}

	@Override
	public Class<? extends GridBdvSupplier> getRunTimeClass() {
		return GridBdvSupplier.class;
	}

	public boolean useCustomAdapter() {
		return false;
	}
}
