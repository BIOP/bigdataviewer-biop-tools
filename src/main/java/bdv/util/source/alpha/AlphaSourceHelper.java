package bdv.util.source.alpha;

import bdv.img.WarpedSource;
import bdv.tools.transformation.TransformedSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.type.numeric.real.FloatType;
import sc.fiji.bdvpg.services.ISourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.util.List;
import java.util.Optional;

/**
 * Helper function which creates or retrieves {@link IAlphaSource} linked to potentially
 * any {@link Source}. This helper uses the weak keys cache of
 * bigdataviewer playground's {@link sc.fiji.bdvpg.scijava.services.SourceAndConverterService}
 * to avoid re-creating multiple alpha sources.
 *
 * {@link WarpedSource} as well as TransformedSource {@link TransformedSource} are supported
 *
 * @author Nicolas Chiaruttini, EPFL, 2021
 */

public class AlphaSourceHelper {

    final public static String ALPHA_SOURCE_KEY = "ALPHA_SOURCE";

    public static synchronized SourceAndConverter<FloatType> getOrBuildAlphaSource(Source source) {

        if (source instanceof IAlphaSource) {
            System.err.println("Warning : you can't make an alpha source out of an alpha source "+source.getName());
            return null;
        }
        ISourceAndConverterService sacService = SourceAndConverterServices.getSourceAndConverterService();

        List<SourceAndConverter<?>> sacList = sacService.getSourceAndConvertersFromSource(source);

        Optional<SourceAndConverter<?>> source_already_associated_with_alpha = sacList.stream().filter(src -> getExistingAlphaSource(src)!=null).findFirst();

        // Deal done
        if (source_already_associated_with_alpha.isPresent()) {
            //Alpha source already computed, returning it
            return getExistingAlphaSource(source_already_associated_with_alpha.get());
        }

        // Builds new alpha source, one way only for now
        IAlphaSource alpha;
        if (source instanceof WarpedSource) {
            //Warped alpha
            alpha = new AlphaSourceWarped(source, 1f);
        } else if (source instanceof TransformedSource) {
            //System.out.println("Transformed alpha");
            //System.out.println("The transformed source is "+source.getName());
            //System.out.println("The wrapped transformed source is "+((TransformedSource<?>) source).getWrappedSource().getName());

            IAlphaSource iniAlpha = (IAlphaSource) getOrBuildAlphaSource(((TransformedSource<?>) source).getWrappedSource()).getSpimSource();
            alpha = new AlphaSourceTransformed(iniAlpha, (TransformedSource<?>) source);
        } else {
            alpha = new AlphaSourceRAI(source, 1f);
        }
        SourceAndConverter<FloatType> alpha_sac = new SourceAndConverter<>(alpha, new AlphaConverter());

        sacList.forEach(compatibleSac -> {
            SourceAndConverterServices.getSourceAndConverterService().setMetadata(compatibleSac, ALPHA_SOURCE_KEY, alpha_sac);
        });

        return alpha_sac;
    }

    public static synchronized void setAlphaSource(SourceAndConverter source, IAlphaSource alphaSource) {
        SourceAndConverterServices.getSourceAndConverterService().setMetadata(source, ALPHA_SOURCE_KEY, new SourceAndConverter<>(alphaSource, new AlphaConverter()));
    }

    public static synchronized void setAlphaSource(SourceAndConverter source, SourceAndConverter alphaSource) {
        SourceAndConverterServices.getSourceAndConverterService().setMetadata(source, ALPHA_SOURCE_KEY, alphaSource);
    }

    // synchronized recursive calls are legit in Java
    public static synchronized SourceAndConverter<FloatType> getOrBuildAlphaSource(SourceAndConverter sac) {
        return getOrBuildAlphaSource(sac.getSpimSource());
    }

    static SourceAndConverter<FloatType> getExistingAlphaSource(SourceAndConverter sac) {
        ISourceAndConverterService sacService = SourceAndConverterServices.getSourceAndConverterService();
        if (sacService.containsMetadata(sac, ALPHA_SOURCE_KEY)) {
            return (SourceAndConverter<FloatType>) sacService.getMetadata(sac, ALPHA_SOURCE_KEY);
        }
        return null;
    }
}
