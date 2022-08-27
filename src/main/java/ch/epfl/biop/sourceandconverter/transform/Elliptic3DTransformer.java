package ch.epfl.biop.sourceandconverter.transform;

import bdv.img.WarpedSource;
import bdv.util.Elliptical3DTransform;
import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.BoundingBoxEstimation;
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
        WarpedSource ws = new WarpedSource(in.getSpimSource(), "Transform_"+e3Dt.getName()+"_"+in.getSpimSource().getName());
        ws.setBoundingBoxEstimator(new BoundingBoxEstimation(BoundingBoxEstimation.Method.CORNERS));
        ws.updateTransform(e3Dt);
        ws.setIsTransformed(true);

        if (in.asVolatile()!=null) {
            WarpedSource vws = new WarpedSource(in.asVolatile().getSpimSource(), "Transform_"+e3Dt.getName()+"_"+in.asVolatile().getSpimSource().getName());//f.apply(in.asVolatile().getSpimSource());
            vws.setBoundingBoxEstimator(new BoundingBoxEstimation(BoundingBoxEstimation.Method.CORNERS));
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
