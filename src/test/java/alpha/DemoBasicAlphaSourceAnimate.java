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

/**
 * Need to understand / solve no accumulator when single source first ...
 */

public class DemoBasicAlphaSourceAnimate {

    static BdvHandle bdvh;

    public static void main(final String... args) {
        BdvOptions options = BdvOptions.options();
        //options = options.accumulateProjectorFactory(new DefaultProjectorFactory());
        options = options.accumulateProjectorFactory(new BasicAlphaProjectorFactory());

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

        sd = BdvSampleDatasets.getTestSpimData();

        BdvSampleDatasets.shiftSpimData((SpimDataMinimal) sd,20,0);

        stackSources = BdvFunctions.show(sd, options.addTo(bdvh));
        stackSources.get(0).setDisplayRange(0,255);

        AlphaSource alpha_anim = new AlphaSourceRAI(stackSources.get(0).getSources().get(0).getSpimSource(), 0.5f);

        alpha_sac = new SourceAndConverter<>(alpha_anim, mc);

        bdvh.getViewerPanel().state().addSource(alpha_sac); // No converter setup
        bdvh.getViewerPanel().state().setSourceActive(alpha_sac, true);

        Thread animate_alpha = new Thread(() -> {
            int i=0;
            while (true) {
                i++;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                float alpha_value = (float) ((1+Math.cos(i/5.0))/2.0);
                //alpha_anim.setAlpha(alpha_value);
                bdvh.getViewerPanel().requestRepaint();
                //System.out.println("alpha = "+alpha_value);
            }
        });

        animate_alpha.start();

    }
}
