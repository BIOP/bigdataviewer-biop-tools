package bdv.util.projector.alpha;

import bdv.viewer.render.AccumulateProjectorFactory;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * Projector which can be used by {@link bdv.BigDataViewer} in order to handle sources transparency and alpha blending
 *
 * For this projector to work as expected, each source should be associated to an alpha source also present in the projector
 *
 * Currently, this repository (bigdataviewer-playground-display) provides a mechanism to semi-conveniently use this projector.
 * Briefly, if a BigDataViewer window is created via the use of {@link sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier},
 * listeners are created which:
 * * synchronizes the display of alpha sources each time a new source is displayed in bdv
 * * in fact, this synchronization mechanism CREATES the alpha source when needed
 * * a caching mechanism using weak keys in {@link sc.fiji.bdvpg.services.SourceAndConverterServices} allows to reuse
 * alpha sources when needed ( in a different window for instance )
 *
 * Alpha sources {@link bdv.util.source.alpha.IAlphaSource} and {@link bdv.util.source.alpha.AlphaSource} are using
 * {@link FloatType} because these are 32 bits, which allows to trick the projector into fitting a float value into
 * the space of an ARGB int value ( see {@link bdv.util.source.alpha.AlphaConverter} and accumulate method in here
 * which uses `Float.intBitsToFloat(access_alpha.get().get());`
 *
 * In terms of performance, this projector goes around 2.6 x slower than the default projector : 30% for the projector
 * overhead + factor 2 because the number of sources are multiplied by 2.
 *
 * @author Nicolas Chiaruttini, EPFL, 2021
 */

public interface ILayerAlphaProjectorFactory extends AccumulateProjectorFactory<ARGBType> {

    /**
     * Changes sources metadata
     * @param sourcesMeta object which links its source to its layer
     */
    void setSourcesMeta(SourcesMetadata sourcesMeta);

    /**
     * Changes layer metadata
     * @param layerMeta object which links each layer to its alpha value
     */
    void setLayerMeta(LayerMetadata layerMeta);

}
