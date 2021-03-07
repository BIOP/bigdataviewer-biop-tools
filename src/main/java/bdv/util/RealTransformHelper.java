package bdv.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.opencsv.CSVReader;
import jitk.spline.ThinPlateR2LogRSplineKernelTransform;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.*;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import org.scijava.Context;
import org.scijava.InstantiableException;
import sc.fiji.bdvpg.services.serializers.AffineTransform3DAdapter;
import sc.fiji.bdvpg.services.serializers.RuntimeTypeAdapterFactory;
import sc.fiji.bdvpg.services.serializers.plugins.BdvPlaygroundObjectAdapterService;
import sc.fiji.bdvpg.services.serializers.plugins.IClassRuntimeAdapter;
import sc.fiji.bdvpg.services.serializers.plugins.ThinPlateSplineTransformAdapter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class RealTransformHelper {

    public static String BigWarpFileFromRealTransform(RealTransform rt) {
        try {
            File file = File.createTempFile("temp", null);
            System.out.println(file.getAbsolutePath());
            file.deleteOnExit();

            if (rt instanceof Wrapped2DTransformAs3D) {
                rt = ((Wrapped2DTransformAs3D)rt).transform;
            }

            if (rt instanceof WrappedIterativeInvertibleRealTransform) {
                rt = ((WrappedIterativeInvertibleRealTransform)rt).getTransform();
            }

            if (rt instanceof BoundedRealTransform) {
                rt = ((BoundedRealTransform)rt).getTransform();

                if (rt instanceof Wrapped2DTransformAs3D) {
                    rt = ((Wrapped2DTransformAs3D)rt).transform;
                }

                if (rt instanceof WrappedIterativeInvertibleRealTransform) {
                    rt = ((WrappedIterativeInvertibleRealTransform)rt).getTransform();
                }
            }

            if (!(rt instanceof ThinplateSplineTransform)) {
                System.err.println("Cannot edit the transform : it's not of class thinplatesplinetransform");
            }

            ThinplateSplineTransform tst = (ThinplateSplineTransform) rt;

            ThinPlateR2LogRSplineKernelTransform kernel = ThinPlateSplineTransformAdapter.getKernel(tst);

            double[][] srcPts = ThinPlateSplineTransformAdapter.getSrcPts(kernel);
            double[][] tgtPts = ThinPlateSplineTransformAdapter.getTgtPts(kernel);

            int nbLandmarks = kernel.getNumLandmarks();
            int nbDimensions = kernel.getNumDims();

            String toFile = "";

            for (int i = 0;i<nbLandmarks;i++) {
                toFile+="\"Pt-"+i+"\",\"true\"";
                for (int d = 0; d<nbDimensions; d++) {
                    toFile+=",\""+tgtPts[d][i]+"\"";
                }
                for (int d = 0; d<nbDimensions; d++) {
                    toFile+=",\""+srcPts[d][i]+"\"";
                }
                toFile+="\n";
            }

            FileWriter writer = new FileWriter(file);
            writer.write(toFile);
            writer.flush();
            writer.close();

            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Uses scijava extensibility mechanism to serialize potentially any sort
     * of RealTransform object
     * @param rt
     * @return
     */
    public static String serialize(RealTransform rt, Context context) {
        return getRealTransformAdapter(context).toJson(rt);
    }

    /**
     * Uses scijava extensibility mechanism to deserialize potentially any sort
     * of RealTransform object
     * @param jsonString
     * @return
     */
    public static RealTransform deserialize(String jsonString, Context context) {
        return getRealTransformAdapter(context).fromJson(jsonString, RealTransform.class);
    }

    public static Gson getRealTransformAdapter(Context context) {

        GsonBuilder gsonbuider = new GsonBuilder()
                .setPrettyPrinting();

        registerTransformAdapters(gsonbuider, context);

        return gsonbuider.create();
    }

    public static Consumer<String> log = (str) -> System.out.println(str);

    // ------------------------------------------------ Serialization / Deserialization
    /*
        Register the RealTransformAdapters from Bdv-playground
     */ // TODO : use this function instead of the one in imagetoatlas package
    public static void registerTransformAdapters(final GsonBuilder gsonbuilder, Context scijavaCtx) {
        // AffineTransform3D serialization
        //gsonbuilder.registerTypeAdapter(AffineTransform3D.class, new AffineTransform3DAdapter());

        Map<Class<?>, List<Class<?>>> runTimeAdapters = new HashMap<>();
        scijavaCtx.getService(BdvPlaygroundObjectAdapterService.class)
                .getAdapters(IClassRuntimeAdapter.class)
                .forEach(pi -> {
                            try {
                                IClassRuntimeAdapter adapter = pi.createInstance();
                                if (runTimeAdapters.containsKey(adapter.getBaseClass())) {
                                    runTimeAdapters.get(adapter.getBaseClass()).add(adapter.getRunTimeClass());
                                } else {
                                    List<Class<?>> subClasses = new ArrayList<>();
                                    subClasses.add(adapter.getRunTimeClass());
                                    runTimeAdapters.put(adapter.getBaseClass(), subClasses);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                );

        scijavaCtx.getService(BdvPlaygroundObjectAdapterService.class)
                .getAdapters(IClassRuntimeAdapter.class)
                .forEach(pi -> {
                    try {
                        IClassRuntimeAdapter adapter = pi.createInstance();
                        if (adapter.getBaseClass().equals(RealTransform.class)) {
                            gsonbuilder.registerTypeHierarchyAdapter(adapter.getRunTimeClass(), adapter);
                        }
                    } catch (InstantiableException e) {
                        e.printStackTrace();
                    }
                });


        log.accept("IRunTimeClassAdapters : ");
        runTimeAdapters.keySet().forEach(baseClass -> {
            if (baseClass.equals(RealTransform.class)) {
                log.accept("\t " + baseClass);
                RuntimeTypeAdapterFactory factory = RuntimeTypeAdapterFactory.of(baseClass);
                runTimeAdapters.get(baseClass).forEach(subClass -> {
                    factory.registerSubtype(subClass);
                    log.accept("\t \t " + subClass);
                });
                gsonbuilder.registerTypeAdapterFactory(factory);
            }
        });
    }


}
