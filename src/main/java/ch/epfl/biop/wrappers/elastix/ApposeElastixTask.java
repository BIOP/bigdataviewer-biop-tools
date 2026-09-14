/*-
 * #%L
 * BigDataViewer tools and plugins for Fiji: fusion, deconvolution, registration and data processing.
 * %%
 * Copyright (C) 2018 - 2026 EPFL BIOP and University of Geneva
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package ch.epfl.biop.wrappers.elastix;

import org.apposed.appose.Appose;
import org.apposed.appose.BuildException;
import org.apposed.appose.Environment;
import org.apposed.appose.Service;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Appose-based implementation of {@link ElastixTask} using itk-elastix.
 *
 * <p>Drop-in replacement for {@link DefaultElastixTask}. Instead of shelling out to the
 * elastix CLI executable, this runs registration in a Python process via Appose using
 * the {@code itk-elastix} library. No external elastix binary or PATH configuration needed.</p>
 *
 * <p>The output contract is identical to the CLI version: after {@link #run}, the output
 * folder contains {@code TransformParameters.0.txt}, {@code TransformParameters.1.txt}, etc.</p>
 *
 * <p>Supports multi-channel registration: when multiple fixed/moving images are provided,
 * they are passed to itk-elastix via {@code ElastixRegistrationMethod.AddFixedImage(...)}.</p>
 *
 * <p>Registrations are dispatched onto a pool of warm, single-threaded worker processes
 * ({@link #borrowWorker()} / {@link #returnWorker(Service)}). Each worker imports {@code itk}
 * once and processes registrations one at a time, so the import cost is amortized while every
 * concurrent job keeps the isolation of its own process. Parallelism comes from running
 * {@link #POOL_SIZE} workers, each single-threaded, rather than many threads per process.</p>
 *
 * <p>The first time the itk-elastix environment is built (a one-off pixi download that can take
 * minutes), a small modal progress dialog is shown — but only when a display is available, so
 * headless execution is never blocked. See {@link #buildEnvironment()}.</p>
 */
public class ApposeElastixTask implements ElastixTask {

    /**
     * Number of concurrent warm worker processes. Defaults to {@link Runtime#availableProcessors()};
     * override with {@code -Delastix.appose.workers=N} (e.g. the physical-core count on
     * hyperthreaded machines).
     */
    public static volatile int POOL_SIZE = Integer.getInteger(
            "elastix.appose.workers",
            Math.max(1, Runtime.getRuntime().availableProcessors()));

    @Override
    public void run(ElastixTaskSettings settings) throws Exception {

        // --- Resolve all supplier values on the Java side ---
        List<String> fixedImagePaths = new ArrayList<>();
        for (Supplier<String> s : settings.fixedImagePathSuppliers) {
            fixedImagePaths.add(s.get().replace("\\", "/"));
        }

        List<String> movingImagePaths = new ArrayList<>();
        for (Supplier<String> s : settings.movingImagePathSuppliers) {
            movingImagePaths.add(s.get().replace("\\", "/"));
        }

        List<String> parameterFiles = new ArrayList<>();
        for (Supplier<String> s : settings.transformationParameterPathSupplier) {
            parameterFiles.add(s.get().replace("\\", "/"));
        }

        String outputFolder = settings.outputFolderSupplier.get().replace("\\", "/");

        String initialTransformFile = settings.initialTransformFilePath != null
                ? settings.initialTransformFilePath.replace("\\", "/")
                : null;

        int nThreads = settings.nThreads;

        // --- Run registration in Python ---
        final Map<String, Object> inputs = new HashMap<>();
        inputs.put("fixed_image_paths", fixedImagePaths);
        inputs.put("moving_image_paths", movingImagePaths);
        inputs.put("parameter_files", parameterFiles);
        inputs.put("output_folder", outputFolder);
        inputs.put("initial_transform_file", initialTransformFile);
        inputs.put("n_threads", nThreads);

        // Dispatch onto a warm, isolated, single-threaded worker process. Each concurrent run()
        // borrows its own process, so registrations never contend in shared Python/ITK state.
        Service worker = borrowWorker();
        boolean reusable = false;
        try {
            final Service.Task task = worker.task(getScript(), inputs);
            if (settings.verbose) {
                task.listen(evt -> System.out.println("[itk-elastix] " + evt.message));
            }
            task.waitFor(); // auto-starts; throws TaskException if the worker reports failure
            reusable = true;
        } finally {
            // Keep a healthy worker warm for reuse; discard (and let the pool respawn) one
            // that errored, since its interpreter state may be compromised.
            if (reusable) returnWorker(worker);
            else discardWorker(worker);
        }
    }

    /**
     * Worker initialization script, run once per worker process before any task.
     * Fixes ITK threading state at import time (the only point where it is honored): the {@code Pool}
     * threader and a single ITK thread per process. Parallelism comes from running many workers.
     */
    private static String getInitScript() {
        return "import os\n"
                // Must be set before 'import itk' so the threader choice is in effect when the
                // ITK thread pool is constructed lazily on first registration.
                + "os.environ['ITK_GLOBAL_DEFAULT_THREADER'] = 'Pool'\n"
                + "import itk\n"
                // Single-threaded ITK per process: parallelism is across worker processes.
                + "itk.MultiThreaderBase.SetGlobalDefaultNumberOfThreads(1)\n";
    }

    private static String getScript() {
        return "import shutil\n"
                + "import tempfile\n"
                + "import os\n"
                + "import itk\n"
                + "\n"
                + "task.update(f'Loading {len(fixed_image_paths)} fixed and {len(moving_image_paths)} moving image(s)...')\n"
                + "fixed_images = [itk.imread(p, itk.F) for p in fixed_image_paths]\n"
                + "moving_images = [itk.imread(p, itk.F) for p in moving_image_paths]\n"
                + "multi_channel = len(fixed_images) > 1\n"
                + "\n"
                // Run each registration stage separately, using a temp subdirectory per stage
                // so itk-elastix doesn't overwrite previous results.
                + "prev_transform_path = initial_transform_file\n"
                + "\n"
                + "for stage_idx, pf in enumerate(parameter_files):\n"
                + "    task.update(f'Stage {stage_idx}: loading parameters from {os.path.basename(pf)}...')\n"
                + "    param_obj = itk.ParameterObject.New()\n"
                + "    param_obj.ReadParameterFile(pf)\n"
                + "\n"
                // Thread count per job. Default is 1 (throughput model: 1 thread/job, parallelism
                // across worker processes). A caller may request more via ElastixTaskSettings.nThreads.
                // NB: we deliberately do NOT set pm['NumberOfThreads'] — elastix silently ignores
                // NumberOfThreads as a parameter-map key. The thread count is set on the registration
                // object below via SetNumberOfThreads(...).
                + "    effective_threads = n_threads if n_threads > 0 else 1\n"
                + "\n"
                + "    stage_dir = tempfile.mkdtemp(prefix=f'elastix_stage{stage_idx}_')\n"
                + "    task.update(f'Stage {stage_idx}: running registration ({len(fixed_images)} channel(s))...')\n"
                + "\n"
                // Use the object API for all cases (single- and multi-channel alike).
                + "    ImageType = type(fixed_images[0])\n"
                + "    erm = itk.ElastixRegistrationMethod[ImageType, ImageType].New()\n"
                + "    if multi_channel:\n"
                + "        erm.SetFixedImage(fixed_images[0])\n"
                + "        for fimg in fixed_images[1:]:\n"
                + "            erm.AddFixedImage(fimg)\n"
                + "        erm.SetMovingImage(moving_images[0])\n"
                + "        for mimg in moving_images[1:]:\n"
                + "            erm.AddMovingImage(mimg)\n"
                + "    else:\n"
                + "        erm.SetFixedImage(fixed_images[0])\n"
                + "        erm.SetMovingImage(moving_images[0])\n"
                + "    erm.SetParameterObject(param_obj)\n"
                + "    erm.SetNumberOfThreads(effective_threads)\n"
                + "    erm.SetOutputDirectory(stage_dir)\n"
                + "    erm.SetLogToConsole(False)\n"
                + "    if prev_transform_path is not None:\n"
                + "        erm.SetInitialTransformParameterFileName(prev_transform_path)\n"
                + "    erm.UpdateLargestPossibleRegion()\n"
                + "\n"
                // Move TransformParameters.0.txt from stage temp dir to final location
                + "    src = os.path.join(stage_dir, 'TransformParameters.0.txt')\n"
                + "    dst = os.path.join(output_folder, f'TransformParameters.{stage_idx}.txt')\n"
                + "    shutil.move(src, dst)\n"
                + "\n"
                // Post-process the transform file to fix compatibility with CLI transformix:
                // 1. Fix InitialTransformParametersFileName path (itk-elastix wrote temp dir path)
                // 2. Normalize itk-elastix pixel-type names to CLI elastix names
                + "    with open(dst, 'r') as f:\n"
                + "        content = f.read()\n"
                + "\n"
                + "    if stage_idx > 0 and prev_transform_path is not None:\n"
                + "        prev_final = os.path.join(output_folder, f'TransformParameters.{stage_idx - 1}.txt')\n"
                + "        content = content.replace(prev_transform_path.replace(os.sep, '/'), prev_final)\n"
                + "        content = content.replace(prev_transform_path.replace('/', os.sep), prev_final)\n"
                + "        content = content.replace(prev_transform_path, prev_final)\n"
                + "\n"
                + "    content = content.replace('\"float32\"', '\"float\"')\n"
                + "    content = content.replace('\"int16\"', '\"short\"')\n"
                + "    content = content.replace('\"uint16\"', '\"unsigned short\"')\n"
                + "    content = content.replace('\"uint8\"', '\"unsigned char\"')\n"
                + "    content = content.replace('\"int8\"', '\"char\"')\n"
                + "\n"
                + "    with open(dst, 'w') as f:\n"
                + "        f.write(content)\n"
                + "\n"
                + "    prev_transform_path = dst\n"
                + "    shutil.rmtree(stage_dir, ignore_errors=True)\n"
                + "    task.update(f'Stage {stage_idx}: wrote {os.path.basename(dst)}')\n"
                + "\n"
                + "task.update('done.')\n";
    }

    // -----------------------------------------------------------------------------------------
    // Warm worker pool
    //
    // A bounded pool (up to POOL_SIZE) of persistent, single-threaded Appose worker processes.
    // Each run() borrows one exclusively, submits one registration, and returns it warm — so the
    // 'import itk' cost is paid once per worker rather than once per registration, while every
    // concurrent job still gets the isolation of its own OS process.
    // -----------------------------------------------------------------------------------------

    private static final BlockingDeque<Service> WORKER_POOL = new LinkedBlockingDeque<>();
    private static final AtomicInteger WORKERS_CREATED = new AtomicInteger(0);

    static {
        // Best-effort cleanup of warm worker processes on JVM shutdown.
        Runtime.getRuntime().addShutdownHook(new Thread(ApposeElastixTask::shutdownWorkers,
                "appose-elastix-pool-shutdown"));
    }

    /**
     * Borrows an idle warm worker, creating a new one if the pool has not reached {@link #POOL_SIZE},
     * otherwise blocking until one is returned.
     */
    public static Service borrowWorker() throws Exception {
        while (true) {
            Service idle = WORKER_POOL.pollFirst();
            if (idle != null) return idle;

            int created = WORKERS_CREATED.get();
            if (created < POOL_SIZE) {
                if (WORKERS_CREATED.compareAndSet(created, created + 1)) {
                    try {
                        return newWarmWorker();
                    } catch (Exception e) {
                        WORKERS_CREATED.decrementAndGet();
                        throw e;
                    }
                }
                // CAS lost to another thread — retry.
            } else {
                // At capacity: wait for a worker to be returned. Poll (rather than block forever)
                // so that if a worker was discarded after an error we loop back and respawn one.
                Service waited = WORKER_POOL.pollFirst(5, TimeUnit.SECONDS);
                if (waited != null) return waited;
            }
        }
    }

    /** Returns a healthy worker to the pool for reuse. */
    public static void returnWorker(Service worker) {
        WORKER_POOL.offerFirst(worker);
    }

    /** Discards a worker that errored, freeing a pool slot so a fresh one can be created. */
    public static void discardWorker(Service worker) {
        WORKERS_CREATED.decrementAndGet();
        try {
            worker.close();
        } catch (Exception ignored) {
            // best effort
        }
    }

    /** Creates and starts a new warm worker process, importing itk and fixing thread state once. */
    private static Service newWarmWorker() throws Exception {
        Service worker = getElastixApposeService().init(getInitScript());
        worker.start(); // launch the process now so 'import itk' happens up front, not on first job
        return worker;
    }

    private static void shutdownWorkers() {
        Service worker;
        while ((worker = WORKER_POOL.pollFirst()) != null) {
            try {
                worker.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    static volatile Environment CACHED_ENV = null;

    public synchronized static Service getElastixApposeService() throws BuildException {
        if (CACHED_ENV == null) CACHED_ENV = buildEnvironment();
        return CACHED_ENV.python();
    }

    /** Classpath manifest describing the environment, next to this class. */
    private static final String MANIFEST = "pixi.toml";

    /** Manifest used instead of {@link #MANIFEST} on Apple-silicon Macs older than macOS 15. */
    private static final String MANIFEST_LEGACY_MACOS = "pixi-legacy-macos.toml";

    /** Placeholder in the manifests, replaced with the conda subdir of the running machine. */
    private static final String PLATFORM_PLACEHOLDER = "@PLATFORM@";

    /** Matches the exact itk-elastix pin in a manifest, e.g. {@code itk-elastix = "==0.25.4"}. */
    private static final Pattern ITK_ELASTIX_PIN =
            Pattern.compile("^\\s*itk-elastix\\s*=\\s*\"==([^\"]+)\"", Pattern.MULTILINE);

    /** The pixi manifest for this machine: a resource, with its placeholder resolved. */
    private static final String PIXI_TOML = loadManifest();

    /**
     * The itk-elastix version installed into the pixi environment, read back from the manifest
     * that this machine actually uses — the manifests are where the pin lives.
     */
    public static final String ITK_ELASTIX_VERSION = parseItkElastixVersion(PIXI_TOML);

    /** True on macOS, whatever the architecture. */
    private static boolean isMacOS() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("mac");
    }

    /**
     * True on a Mac too old for the {@code macosx_15_0_arm64} wheels that itk-elastix has shipped
     * since 0.25.3, and which therefore needs {@link #MANIFEST_LEGACY_MACOS}.
     */
    private static boolean isLegacyMacOS() {
        return isMacOS() && macOSMajorVersion() < 15;
    }

    /** Major component of {@code os.version}; {@link Integer#MAX_VALUE} when unparseable. */
    private static int macOSMajorVersion() {
        String version = System.getProperty("os.version", "");
        int dot = version.indexOf('.');
        try {
            return Integer.parseInt(dot < 0 ? version : version.substring(0, dot));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE; // unknown format: assume a recent macOS
        }
    }

    /** Conda platform identifier ("subdir") of the running machine. */
    private static String condaSubdir() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
        if (isMacOS()) return arm64 ? "osx-arm64" : "osx-64";
        if (System.getProperty("os.name", "").toLowerCase().startsWith("windows")) return "win-64";
        return arm64 ? "linux-aarch64" : "linux-64";
    }

    /**
     * Reads the manifest for this machine off the classpath and resolves its placeholder.
     * <p>
     * Loaded relative to this class rather than from the classpath root, because other Appose-based
     * Fiji plugins ship a {@code /pixi.toml} of their own — imglib2-cellpose does — and inside Fiji
     * they all share one classpath. Set {@code -Delastix.pixi.manifest=/path/to/pixi.toml} to try
     * a different environment without rebuilding.
     * </p>
     */
    private static String loadManifest() {
        String override = System.getProperty("elastix.pixi.manifest");
        String resource = isLegacyMacOS() ? MANIFEST_LEGACY_MACOS : MANIFEST;
        try {
            String toml;
            if (override != null) toml = new String(Files.readAllBytes(Paths.get(override)), StandardCharsets.UTF_8);
            else try (InputStream in = ApposeElastixTask.class.getResourceAsStream(resource)) {
                if (in == null) throw new IllegalStateException("Missing classpath resource: "
                        + ApposeElastixTask.class.getPackage().getName().replace('.', '/') + "/" + resource);
                toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return toml.replace(PLATFORM_PLACEHOLDER, condaSubdir());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + (override != null ? override : resource), e);
        }
    }

    /** Extracts the pinned itk-elastix version from a manifest. */
    private static String parseItkElastixVersion(String toml) {
        Matcher matcher = ITK_ELASTIX_PIN.matcher(toml);
        if (!matcher.find()) throw new IllegalStateException(
                "No exact itk-elastix pin found in the pixi manifest");
        return matcher.group(1);
    }

    /** Builds (and caches) the itk-elastix pixi environment. */
    private static Environment doBuildEnvironment() throws BuildException {
        if ("osx-64".equals(condaSubdir())) {
            throw new BuildException("itk-elastix publishes no Intel-Mac (osx-64) wheel since "
                    + "0.21.0, so Intel Macs are not supported. On Apple silicon, this message "
                    + "means the JVM is an Intel one running under Rosetta: use an arm64 Fiji/JDK.");
        }
        return Appose
                .pixi()
                .content(PIXI_TOML)
                .env("NSLOTS", "1")
                // Pool beats Platform for small images; never TBB (defaults to ~1024 work units).
                // The init script also sets this before 'import itk' for belt-and-suspenders.
                .env("ITK_GLOBAL_DEFAULT_THREADER", "Pool")
                .name("itk-elastix-v"+ITK_ELASTIX_VERSION)
                .logDebug()
                .build();
    }

    // GUI / headless handling for the one-off environment build
    // ---------------------------------------------------------
    // The first build downloads and resolves the itk-elastix conda env, which can take minutes.
    // On a machine with a display we show a small modal progress dialog so the user knows the UI
    // hasn't frozen; in headless mode (or when called from the EDT) we simply build without any GUI.

    private static volatile boolean firstBuild = true;

    /**
     * Builds the environment, optionally wrapping the first build in a modal progress dialog.
     * The dialog is shown only on the first build, only when a display is available, and only
     * when not called from the Swing event dispatch thread (to avoid deadlocking the modal loop).
     */
    private static Environment buildEnvironment() throws BuildException {
        boolean showDialog = firstBuild
                && !GraphicsEnvironment.isHeadless()
                && !SwingUtilities.isEventDispatchThread();
        firstBuild = false;

        if (!showDialog) {
            return doBuildEnvironment();
        }
        return buildEnvironmentWithDialog();
    }

    /** Builds the environment on a background thread while a modal "please wait" dialog is shown. */
    private static Environment buildEnvironmentWithDialog() throws BuildException {
        final JDialog dialog = new JDialog((Frame) null, "Loading itk-elastix...", true);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        JLabel infoLabel = new JLabel("<html>"
                + "<h2>Loading Elastix (itk-elastix "+ITK_ELASTIX_VERSION+")</h2>"
                + "<p>Setting up the registration environment on first use.<br>"
                + "This one-off download may take a few minutes.</p>"
                + "</html>", SwingConstants.CENTER);
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(infoLabel);

        panel.add(Box.createVerticalStrut(15));

        // Use the shared spinning loading.gif for a consistent look with the DeepSlice dialog.
        // Fall back to an indeterminate progress bar if the resource is missing.
        URL loadingUrl = ApposeElastixTask.class.getClassLoader().getResource("graphics/loading.gif");
        if (loadingUrl != null) {
            ImageIcon loadingIcon = new ImageIcon(loadingUrl);
            loadingIcon.setImage(loadingIcon.getImage().getScaledInstance(64, 64, Image.SCALE_DEFAULT));
            JLabel waitLabel = new JLabel("Loading itk-elastix environment, please wait...",
                    loadingIcon, SwingConstants.CENTER);
            waitLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(waitLabel);
        } else {
            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(progressBar);
        }

        dialog.getContentPane().add(panel);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.pack();
        dialog.setLocationRelativeTo(null);

        final AtomicReference<Environment> envRef = new AtomicReference<>();
        final AtomicReference<Exception> errRef = new AtomicReference<>();

        // SwingWorker builds in the background; disposing the modal dialog from done()
        // unblocks setVisible(true) below.
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    envRef.set(doBuildEnvironment());
                } catch (Exception e) {
                    errRef.set(e);
                }
                return null;
            }

            @Override
            protected void done() {
                dialog.dispose();
            }
        }.execute();

        dialog.setVisible(true); // blocks the calling (non-EDT) thread until the build finishes

        Exception err = errRef.get();
        if (err != null) {
            if (err instanceof BuildException) throw (BuildException) err;
            throw new RuntimeException(err);
        }
        return envRef.get();
    }
}
