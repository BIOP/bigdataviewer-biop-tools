package alpha;

import bdv.spimdata.SpimDataMinimal;
import bdv.util.*;
import bdv.util.source.alpha.AlphaSourceRAI;
import org.jetbrains.annotations.NotNull;
import bdv.util.projector.alpha.LayerAlphaProjectorFactory;
import bdv.util.source.alpha.AlphaConverter;
import bdv.util.source.alpha.AlphaSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FancyDemoLayerAlphaSource {

    static BdvHandle bdvh;

    static Map<SourceAndConverter, SourceAndConverter> sourceToAlpha = new ConcurrentHashMap<>();
    static Map<SourceAndConverter, DefaultLayer> sourceToLayer = new ConcurrentHashMap<>();

    public static class DefaultLayer implements LayerAlphaProjectorFactory.Layer {

        float alpha;
        int id;
        boolean skip;

        public DefaultLayer(float alpha, int id, boolean skip) {
            this.alpha = alpha;
            this.id = id;
            this.skip = skip;
        }

        public float getAlpha(){
            return alpha;
        }

        public void setAlpha(float alpha) {
            this.alpha = alpha;
        }

        public int getBlendingMode() {
            // 0 = SUM, 1 = AVERAGE TODO , currently only sum
            return 0;
        }

        public boolean skip() {
            return skip;
        }

        public int getId() {
            return id;
        }

        @Override
        public int compareTo(@NotNull LayerAlphaProjectorFactory.Layer o) {
            return Integer.compare(this.getId(), ((DefaultLayer)o).getId());
        }
    }

    public static boolean putAlphaSources = true;

    public static void main(final String... args) {

        List<DefaultLayer> layers = new ArrayList<>();
        layers.add(new DefaultLayer(1f, 0, false));
        layers.add(new DefaultLayer(0.4f, 1, false));
        layers.add(new DefaultLayer(0.8f, 2, false));

        BdvOptions options = BdvOptions.options();
        //options = options.accumulateProjectorFactory(new BlackProjectorFactory());
        options = options.accumulateProjectorFactory(new LayerAlphaProjectorFactory(new LayerAlphaProjectorFactory.SourcesMetadata() {
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
        },
            new LayerAlphaProjectorFactory.LayerMetadata() {
                @Override
                public LayerAlphaProjectorFactory.Layer getLayer(SourceAndConverter sac) {
                    if (sourceToLayer.containsKey(sac)) {
                        return sourceToLayer.get(sac);
                    } else {
                        return layers.get(0);
                    }
                }
            }));

        AbstractSpimData sd = BdvSampleDatasets.getTestSpimData();
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.scale(0.8);
        List<BdvStackSource<?>> stackSources = BdvFunctions.show(sd, options.sourceTransform(at3D));
        stackSources.get(0).setDisplayRange(0,2);

        bdvh = stackSources.get(0).getBdvHandle();

        bdvh.getViewerPanel().state().setViewerTransform(at3D);

        Source<FloatType> alpha = new AlphaSourceRAI(stackSources.get(0).getSources().get(0).getSpimSource(), 1f);
        SourceAndConverter<FloatType> alpha_sac =
                new SourceAndConverter<>(alpha, new AlphaConverter());

        if (putAlphaSources) {
            bdvh.getViewerPanel().state().addSource(alpha_sac); // No converter setup
            bdvh.getViewerPanel().state().setSourceActive(alpha_sac, true);
            sourceToAlpha.put(bdvh.getViewerPanel().state().getSources().get(0), alpha_sac);
            sourceToLayer.put(bdvh.getViewerPanel().state().getSources().get(0), layers.get(0));
        }

        for (int x=0;x<5;x++) {
            for (int y=0;y<5;y++) {
                appendTestSpimdata(bdvh, 200*x, 200*y, layers.get( (x+y) % 2 + 1));
            }
        }

        JPanel panel = new JPanel(new GridLayout(3,1));

        JSlider sliderLayer1 = new JSlider();
        sliderLayer1.setMinimum(0);
        sliderLayer1.setMaximum(255);
        panel.add(sliderLayer1);
        sliderLayer1.addChangeListener(l -> {
            int alphaValue = ((JSlider)l.getSource()).getValue();
            //System.out.println("Alpha Layer 1 = "+alphaValue);
            layers.get(1).setAlpha((float)(alphaValue/255.0));
            bdvh.getViewerPanel().requestRepaint();
        });

        JSlider sliderLayer2 = new JSlider();
        sliderLayer2.setMinimum(0);
        sliderLayer2.setMaximum(255);
        panel.add(sliderLayer2);
        sliderLayer2.addChangeListener(l -> {
            int alphaValue = ((JSlider)l.getSource()).getValue();
            //System.out.println("Alpha Layer 2 = "+alphaValue);
            layers.get(2).setAlpha((float)(alphaValue/255.0));
            bdvh.getViewerPanel().requestRepaint();
        });

        JButton swapLayers = new JButton("Swap layers");
        swapLayers.addActionListener(l -> {
            sourceToLayer.keySet().stream().forEach((sac) -> {
                int id = sourceToLayer.get(sac).getId();
                if ((id==1)||(id==2)) {
                    sourceToLayer.put(sac, layers.get(1 - (id - 1) + 1));
                }
            });
            bdvh.getViewerPanel().requestRepaint();
        });
        panel.add(swapLayers);

        bdvh.getCardPanel().addCard("Layer controls", panel, true);

        /*System.out.println(BdvProbeFPS.getStdMsPerFrame(bdvh)+" ms per frame");
        System.out.println(BdvProbeFPS.getStdMsPerFrame(bdvh)+" ms per frame");
        System.out.println(BdvProbeFPS.getStdMsPerFrame(bdvh)+" ms per frame");
        System.out.println(BdvProbeFPS.getStdMsPerFrame(bdvh)+" ms per frame");*/

    }

    private static void appendTestSpimdata(BdvHandle bdvh, float x, float y, DefaultLayer layer) {
        SpimDataMinimal sd = BdvSampleDatasets.getTestSpimData();

        BdvSampleDatasets.shiftSpimData( sd,(int)x,(int)y);

        List<BdvStackSource<?>> stackSources = BdvFunctions.show(sd, BdvOptions.options().addTo(bdvh));
        stackSources.get(0).setDisplayRange(0,255);

        int nSources = bdvh.getViewerPanel().state().getSources().size();

        AlphaSource alpha = new AlphaSourceRAI(bdvh.getViewerPanel().state().getSources().get(nSources-1).getSpimSource(), 1f);

        SourceAndConverter<FloatType> alpha_sac = new SourceAndConverter<>(alpha, new AlphaConverter());
        if (putAlphaSources) {
            bdvh.getViewerPanel().state().addSource(alpha_sac); // No converter setup
            bdvh.getViewerPanel().state().setSourceActive(alpha_sac, true);

            sourceToAlpha.put(bdvh.getViewerPanel().state().getSources().get(nSources - 1), alpha_sac);
            sourceToLayer.put(bdvh.getViewerPanel().state().getSources().get(nSources - 1), layer);
        }

    }
}
