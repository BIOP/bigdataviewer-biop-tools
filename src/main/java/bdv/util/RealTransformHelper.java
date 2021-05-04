package bdv.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jitk.spline.ThinPlateR2LogRSplineKernelTransform;
import net.imglib2.realtransform.*;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import org.scijava.Context;
import org.scijava.InstantiableException;
import sc.fiji.persist.DefaultScijavaAdapterService;
import sc.fiji.persist.IClassAdapter;
import sc.fiji.persist.IClassRuntimeAdapter;
import sc.fiji.persist.RuntimeTypeAdapterFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class RealTransformHelper {

    public static Consumer<String> log = System.out::println;

    public static String BigWarpFileFromRealTransform(RealTransform rt) {
        try {
            File file = File.createTempFile("temp", null);
            System.out.println(file.getAbsolutePath());
            file.deleteOnExit();

            if (rt instanceof Wrapped2DTransformAs3D) {
                rt = ((Wrapped2DTransformAs3D)rt).transform;
            }

            if (rt instanceof WrappedIterativeInvertibleRealTransform) {
                rt = ((WrappedIterativeInvertibleRealTransform<?>)rt).getTransform();
            }

            if (rt instanceof BoundedRealTransform) {
                rt = ((BoundedRealTransform)rt).getTransform();

                if (rt instanceof Wrapped2DTransformAs3D) {
                    rt = ((Wrapped2DTransformAs3D)rt).transform;
                }

                if (rt instanceof WrappedIterativeInvertibleRealTransform) {
                    rt = ((WrappedIterativeInvertibleRealTransform<?>)rt).getTransform();
                }
            }

            if (!(rt instanceof ThinplateSplineTransform)) {
                System.err.println("Cannot edit the transform : it's not of class thinplatesplinetransform");
                return null;
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
     * @param rt real transform
     * @return serialized string from the real transform
     */
    public static String serialize(RealTransform rt, Context context) {
        return getRealTransformAdapter(context).toJson(rt);
    }

    /**
     * Uses scijava extensibility mechanism to deserialize potentially any sort
     * of RealTransform object
     * @param jsonString result of a realtransform serialization
     * @param context scijava context - necessary to find all serializers
     * @return the realtransform deserialized from this string
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

    public static void registerTransformAdapters(final GsonBuilder gsonbuilder, Context scijavaCtx) {
        log.accept("IClassAdapters : ");
        scijavaCtx.getService(DefaultScijavaAdapterService.class)
                .getAdapters(IClassAdapter.class)
                .forEach(pi -> {
                    try {
                        IClassAdapter<?> adapter = pi.createInstance();
                        if (RealTransform.class.isAssignableFrom(adapter.getAdapterClass())) {
                            log.accept("\t "+adapter.getAdapterClass());
                            gsonbuilder.registerTypeAdapter(adapter.getAdapterClass(), adapter);
                        }
                    } catch (InstantiableException e) {
                        e.printStackTrace();
                    }
                });

        Map<Class<?>, List<Class<?>>> runTimeAdapters = new HashMap<>();
        scijavaCtx.getService(DefaultScijavaAdapterService.class)
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

        scijavaCtx.getService(DefaultScijavaAdapterService.class)
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
