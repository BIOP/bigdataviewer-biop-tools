package ch.epfl.biop.wrappers.elastix;

import org.apposed.appose.Service;
import org.junit.Assume;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Smoke test for the itk-elastix Appose environment.
 *
 * <p>Builds the pixi environment from scratch and runs one tiny synthetic registration, which is
 * enough to catch the whole class of "no wheel for this platform" failures — the reason this test
 * exists is that itk-elastix ships platform-specific wheels whose tags change between releases,
 * so an environment that resolves on Linux can be unsatisfiable on macOS.</p>
 *
 * <p>Skipped by default, because building the environment downloads several hundred megabytes.
 * Enable with {@code mvn test -Dtest=ApposeElastixEnvTest -Delastix.env.test=true}.</p>
 */
public class ApposeElastixEnvTest {

    /**
     * Registers a 20x20 square against a copy of itself translated by {@link #SHIFT} pixels,
     * and reports the recovered translation plus the resolved package versions.
     */
    private static final int SHIFT = 5;

    private static final String SCRIPT =
            "import numpy as np\n"
          + "import itk\n"
          + "from importlib.metadata import version\n"
          + "\n"
          + "task.update('itk ' + itk.__version__ + ', itk-elastix ' + version('itk-elastix'))\n"
          + "fixed = np.zeros((64, 64), dtype=np.float32)\n"
          + "fixed[20:40, 20:40] = 1.0\n"
          + "moving = np.roll(fixed, " + SHIFT + ", axis=1)\n"
          + "\n"
          + "params = itk.ParameterObject.New()\n"
          + "params.AddParameterMap(params.GetDefaultParameterMap('translation'))\n"
          + "\n"
          + "task.update('registering...')\n"
          + "_, transform = itk.elastix_registration_method(\n"
          + "    itk.image_from_array(fixed), itk.image_from_array(moving),\n"
          + "    parameter_object=params, log_to_console=False)\n"
          + "\n"
          + "task.outputs['itk_elastix_version'] = version('itk-elastix')\n"
          + "task.outputs['itk_version'] = itk.__version__\n"
          + "task.outputs['tx'] = float(transform.GetParameterMap(0)['TransformParameters'][0])\n";

    @Test
    public void buildsEnvironmentAndRegisters() throws Exception {
        Assume.assumeTrue("set -Delastix.env.test=true to run this test",
                Boolean.getBoolean("elastix.env.test"));

        Service worker = ApposeElastixTask.borrowWorker();
        try {
            Service.Task task = worker.task(SCRIPT, Collections.emptyMap());
            task.listen(evt -> System.out.println("[itk-elastix] " + evt.message));
            task.waitFor();

            System.out.println("itk        : " + task.outputs.get("itk_version"));
            System.out.println("itk-elastix: " + task.outputs.get("itk_elastix_version"));

            assertEquals("installed itk-elastix differs from the pinned version",
                    ApposeElastixTask.ITK_ELASTIX_VERSION,
                    task.outputs.get("itk_elastix_version"));

            double tx = ((Number) task.outputs.get("tx")).doubleValue();
            System.out.println("recovered translation: " + tx + " (expected +/-" + SHIFT + ")");
            assertTrue("elastix did not recover the " + SHIFT + " px shift, got " + tx,
                    Math.abs(Math.abs(tx) - SHIFT) < 1.5);

            ApposeElastixTask.returnWorker(worker);
            worker = null;
        } finally {
            if (worker != null) ApposeElastixTask.discardWorker(worker);
        }
    }
}
