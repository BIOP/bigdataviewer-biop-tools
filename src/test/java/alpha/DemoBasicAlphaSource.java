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
        //Source<VolatileFloatType> alpha_volatile = new VolatileBdvSource<>(alpha, new VolatileFloatType(), new SharedQueue(2));

        //SourceAndConverter<VolatileFloatType> volatile_alpha_sac =
        //        new SourceAndConverter<>(alpha_volatile, new VolatileMaskConverter());

        //SourceAndConverter<FloatType> alpha_sac =
        //        new SourceAndConverter<>(alpha, new MaskConverter(), volatile_alpha_sac);

        SourceAndConverter<FloatType> alpha_sac =
                new SourceAndConverter<>(alpha, new AlphaConverter());

        bdvh.getViewerPanel().state().addSource(alpha_sac); // No converter setup
        bdvh.getViewerPanel().state().setSourceActive(alpha_sac, true);

       /* sd = sc.fiji.bdvpg.projectors.test.BdvSampleDatasets.getTestSpimData();

        stackSources = BdvFunctions.show(sd, options.addTo(bdvh));
        stackSources.get(0).setDisplayRange(0,255);*/

        //alpha_sac = new SourceAndConverter<>(alpha, new MaskConverter());
        //bdvh.getViewerPanel().state().addSource(alpha_sac); // No converter setup

        //BdvFunctions.show(alpha);//, options.addTo(bdvh));

    }
}
