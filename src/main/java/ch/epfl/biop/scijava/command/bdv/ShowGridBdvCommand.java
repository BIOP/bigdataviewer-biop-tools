package ch.epfl.biop.scijava.command.bdv;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.opener.OpenerHelper;
import ch.epfl.biop.bdv.select.SourceSelectorBehaviour;
import ch.epfl.biop.bdv.select.ToggleListener;
import ch.epfl.biop.scijava.command.source.ExportToMultipleImagePlusCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileARGBType;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.cache.CacheService;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import sc.fiji.bdvpg.viewers.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.viewers.behaviour.EditorBehaviourUnInstaller;
import sc.fiji.bdvpg.viewers.behaviour.SourceContextMenuClickBehaviour;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.command.source.display.SourceBrightnessAdjustCommand;
import sc.fiji.bdvpg.scijava.services.SourceService;
import sc.fiji.bdvpg.services.SourceServices;
import sc.fiji.bdvpg.source.SourceHelper;
import sc.fiji.bdvpg.source.transform.SourceAffineTransformer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static sc.fiji.bdvpg.scijava.services.SourceService.getCommandName;
import static sc.fiji.bdvpg.services.ISourceService.SPIM_DATA_INFO;
import static sc.fiji.bdvpg.viewers.ViewerOrthoSyncStarter.MatrixApproxEquals;

/**
 * Command which display sources on a grid in BigDataViewer
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Display>BDV - Make Grid Bdv",
        description = "Creates a new BigDataViewer window configured for grid display")
public class ShowGridBdvCommand implements BdvPlaygroundActionCommand {

    @Parameter(type = ItemIO.OUTPUT,
            label = "Grid BDV Window",
            description = "The created BigDataViewer window with grid configuration")
    BdvHandle bdvh;

    @Override
    public void run() {
        bdvh = new GridBdvSupplier().get();
    }


}
