package alpha;

import bdv.spimdata.SpimDataMinimal;
import bdv.util.*;
import bdv.util.source.alpha.AlphaConverter;
import bdv.util.source.alpha.AlphaSource;
import bdv.util.source.alpha.AlphaSourceRAI;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.type.numeric.real.FloatType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DemoAlphaSource {
    static BdvHandle bdvh;

    static Map<SourceAndConverter, SourceAndConverter> sourceToAlpha = new ConcurrentHashMap<>();

    public static void main(final String... args) {
        BdvOptions options = BdvOptions.options();
        //options = options.accumulateProjectorFactory(new DefaultProjectorFactory());
        options = options.accumulateProjectorFactory(new AlphaProjectorFactory(new AlphaProjectorFactory.SourcesMetadata() {
            @Override
            public boolean isAlphaSource(SourceAndConverter sac) {
                if (sourceToAlpha.containsValue(sac)) return true;
                return false;
            }

            @Override
            public boolean hasAlphaSource(SourceAndConverter sac) {
                if (sourceToAlpha.containsKey(sac)) {
                    return sourceToAlpha.get(sac) != null;
                }
                return false;
            }

            @Override
            public SourceAndConverter getAlphaSource(SourceAndConverter sac) {
                if (sourceToAlpha.containsKey(sac)) {
                    return sourceToAlpha.get(sac);
                }
                return null;
            }
        }));

        AlphaConverter mc = new AlphaConverter();

        AbstractSpimData sd = BdvSampleDatasets.getTestSpimData();

        List<BdvStackSource<?>> stackSources = BdvFunctions.show(sd, options);
        stackSources.get(0).setDisplayRange(0,255);

        bdvh = stackSources.get(0).getBdvHandle();

        Source<FloatType> alpha = new AlphaSourceRAI(stackSources.get(0).getSources().get(0).getSpimSource(), 1f);

        SourceAndConverter<FloatType> alpha_sac =
                new SourceAndConverter<>(alpha, mc);

        bdvh.getViewerPanel().state().addSource(alpha_sac); // No converter setup
        bdvh.getViewerPanel().state().setSourceActive(alpha_sac, true);

        sourceToAlpha.put(bdvh.getViewerPanel().state().getSources().get(0), alpha_sac);

        sd = BdvSampleDatasets.getTestSpimData();

        BdvSampleDatasets.shiftSpimData((SpimDataMinimal) sd,20,0);

        stackSources = BdvFunctions.show(sd, options.addTo(bdvh));
        stackSources.get(0).setDisplayRange(0,255);

        AlphaSource alpha_anim = new AlphaSourceRAI(stackSources.get(0).getSources().get(0).getSpimSource(), 0.5f);

        alpha_sac = new SourceAndConverter<>(alpha_anim, mc);

        bdvh.getViewerPanel().state().addSource(alpha_sac); // No converter setup
        bdvh.getViewerPanel().state().setSourceActive(alpha_sac, true);

        sourceToAlpha.put(bdvh.getViewerPanel().state().getSources().get(2), alpha_sac);

    }
}
