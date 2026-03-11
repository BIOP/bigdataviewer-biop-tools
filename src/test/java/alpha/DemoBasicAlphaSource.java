package alpha;

import bdv.util.*;
import bdv.util.source.alpha.AlphaConverter;
import bdv.util.source.alpha.AlphaSource;
import bdv.util.source.alpha.AlphaSourceRAI;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.type.numeric.real.FloatType;

import java.util.List;

/**
 * Need to understand / solve no accumularor with single source first ...
 */

public class DemoBasicAlphaSource {

    public static void main(final String... args) {
        BdvOptions options = BdvOptions.options();
        //options = options.accumulateProjectorFactory(new DefaultProjectorFactory());
        options = options.accumulateProjectorFactory(new BasicAlphaProjectorFactory());

        AbstractSpimData sd = BdvSampleDatasets.getTestSpimData();

        List<BdvStackSource<?>> stackSources = BdvFunctions.show(sd, options);
        stackSources.get(0).setDisplayRange(0,255);

        BdvHandle bdvh = stackSources.get(0).getBdvHandle();

        Source<FloatType> alpha = new AlphaSourceRAI(stackSources.get(0).getSources().get(0).getSpimSource(), 0.5f);


        SourceAndConverter<FloatType> alpha_source =
                new SourceAndConverter<>(alpha, new AlphaConverter());

        bdvh.getViewerPanel().state().addSource(alpha_source); // No converter setup
        bdvh.getViewerPanel().state().setSourceActive(alpha_source, true);

    }
}
