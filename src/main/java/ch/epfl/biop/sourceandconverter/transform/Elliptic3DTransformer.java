package ch.epfl.biop.sourceandconverter.transform;

import bdv.img.WarpedSource;
import bdv.util.Elliptical3DTransform;
import bdv.viewer.SourceAndConverter;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.util.function.Function;

public class Elliptic3DTransformer implements Runnable, Function<SourceAndConverter, SourceAndConverter> {

    SourceAndConverter sourceIn;
    Elliptical3DTransform e3Dt;
    SourceAndConverter sourceOut;

    public Elliptic3DTransformer(SourceAndConverter src, Elliptical3DTransform e3Dt) {
        this.sourceIn = src;
        this.e3Dt = e3Dt;
    }

    @Override
    public void run() {
        sourceOut = apply(sourceIn);
    }

    public SourceAndConverter getSourceOut() {
        return sourceOut;
    }

    public SourceAndConverter apply(SourceAndConverter in) {
        WarpedSource ws = new WarpedSource(in.getSpimSource(), "Ellipitic3DTransformed_"+in.getSpimSource().getName());
        ws.updateTransform(e3Dt);
        ws.setIsTransformed(true);

        if (in.asVolatile()!=null) {
            WarpedSource vws = new WarpedSource(in.asVolatile().getSpimSource(), "Ellipitic3DTransformed_"+in.asVolatile().getSpimSource().getName());//f.apply(in.asVolatile().getSpimSource());
            vws.updateTransform(e3Dt);
            vws.setIsTransformed(true);

            SourceAndConverter vout = new SourceAndConverter<>(vws, in.asVolatile().getConverter());

            SourceAndConverter out = new SourceAndConverter(ws, in.getConverter(), vout);

            e3Dt.updateNotifiers.add(() -> {
                ws.updateTransform(e3Dt);
                vws.updateTransform(e3Dt);
                SourceAndConverterServices
                        .getBdvDisplayService()
                        .getDisplaysOf(out).forEach(bdvHandle -> bdvHandle.getViewerPanel().requestRepaint());
            }); // TODO avoid memory leak...

            SourceAndConverterServices.getSourceAndConverterService().register(out);
            return out;
        } else {

            SourceAndConverter out = new SourceAndConverter(ws, in.getConverter());

            e3Dt.updateNotifiers.add(() -> {
                ws.updateTransform(e3Dt);
                SourceAndConverterServices
                        .getBdvDisplayService()
                        .getDisplaysOf(out).forEach(bdvHandle -> bdvHandle.getViewerPanel().requestRepaint());
            }); // TODO avoid memory leak...

            SourceAndConverterServices.getSourceAndConverterService().register(out);
            return out;
        }

    }

}
