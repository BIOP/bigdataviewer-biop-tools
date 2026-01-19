/*-
 * #%L
 * BigDataViewer-Playground
 * %%
 * Copyright (C) 2019 - 2026 Nicolas Chiaruttini, EPFL - Robert Haase, MPI CBG - Christian Tischer, EMBL
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

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import com.formdev.flatlaf.FlatDarkLaf;
import net.imagej.ImageJ;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class DemoHelper {

    // ==================== SETUP METHODS ====================

    /**
     * Expands the SourceAndConverter tree view to a specified depth.
     * Call this at the beginning of demos to make screenshots more informative.
     *
     * @param ij the ImageJ instance
     * @param depth the depth to expand (typically 3)
     */
    public static void expandTreeView(ImageJ ij, int depth) {
        SourceAndConverterService sacService = ij.get(SourceAndConverterService.class);
        if (sacService != null && sacService.getUI() != null) {
            sacService.getUI().expandToDepth(depth);
        }
    }

    /**
     * Expands the SourceAndConverter tree view to depth 3 (default).
     *
     * @param ij the ImageJ instance
     */
    public static void expandTreeView(ImageJ ij) {
        expandTreeView(ij, 3);
    }

    // ==================== SEMI-INTERACTIVE METHODS ====================

    /**
     * Pauses the demo for manual user action, then captures screenshots.
     * Shows a dialog with instructions and waits for the user to click "Done".
     * After the user completes the action and clicks the button, screenshots are captured.
     *
     * <p>Example usage:</p>
     * <pre>
     * DemoHelper.pauseForUserAction("DemoLabkit_03_segmentation",
     *     "Please perform the following steps in Labkit:\n" +
     *     "1. Draw scribbles on the background (label 'background')\n" +
     *     "2. Draw scribbles on the cells (label 'foreground')\n" +
     *     "3. Click 'Train Classifier' to see the segmentation");
     *
     * // With window filtering:
     * DemoHelper.pauseForUserAction("DemoLabkit_03_segmentation",
     *     "Instructions...",
     *     "Labkit");  // Only capture Labkit windows
     * </pre>
     *
     * @param prefix prefix for screenshot filenames
     * @param instructions multi-line instructions for the user
     * @param titleFilters optional filters - only windows whose title contains one of these strings will be captured
     */
    public static void pauseForUserAction(String prefix, String instructions, String... titleFilters) {
        showInstructionDialog("Manual Step Required", instructions, "Done - Capture Screenshots");
        System.out.println("[Demo] Capturing screenshots...");
        shot(prefix, titleFilters);
    }

    /**
     * Pauses the demo for manual user action with custom wait time before screenshot.
     *
     * @param prefix prefix for screenshot filenames
     * @param instructions multi-line instructions for the user
     * @param waitMs milliseconds to wait after user clicks Done before capturing
     * @param titleFilters optional filters - only windows whose title contains one of these strings will be captured
     */
    public static void pauseForUserActionWithWait(String prefix, String instructions, long waitMs, String... titleFilters) {
        showInstructionDialog("Manual Step Required", instructions, "Done - Capture Screenshots");
        System.out.println("[Demo] Capturing screenshots...");
        shotWithWait(prefix, waitMs, titleFilters);
    }

    /**
     * Pauses the demo to display information without capturing screenshots.
     * Useful for explaining what will happen next.
     *
     * @param message the message to display
     */
    public static void pause(String message) {
        showInstructionDialog("Demo Paused", message, "Continue");
    }

    /**
     * Shows a non-blocking instruction dialog and waits for the user to click the button.
     *
     * @param title the dialog title
     * @param instructions the instructions to display
     * @param buttonText the text for the action button
     */
    private static void showInstructionDialog(String title, String instructions, String buttonText) {
        final CountDownLatch latch = new CountDownLatch(1);

        SwingUtilities.invokeLater(() -> {
            JFrame dialog = new JFrame(title);
            dialog.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            dialog.setAlwaysOnTop(true);

            JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
            contentPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

            // Title label
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
            contentPanel.add(titleLabel, BorderLayout.NORTH);

            // Instructions text area
            JTextArea textArea = new JTextArea(instructions);
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setBackground(contentPanel.getBackground());
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            textArea.setBorder(new EmptyBorder(10, 0, 10, 0));

            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setBorder(null);
            scrollPane.setPreferredSize(new Dimension(450, 200));
            contentPanel.add(scrollPane, BorderLayout.CENTER);

            // Button panel
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JButton doneButton = new JButton(buttonText);
            doneButton.setFont(doneButton.getFont().deriveFont(Font.BOLD, 14f));
            doneButton.setPreferredSize(new Dimension(220, 35));
            doneButton.addActionListener(e -> {
                dialog.dispose();
                latch.countDown();
            });
            buttonPanel.add(doneButton);
            contentPanel.add(buttonPanel, BorderLayout.SOUTH);

            dialog.setContentPane(contentPanel);
            dialog.pack();
            dialog.setLocationRelativeTo(null); // Center on screen
            dialog.setVisible(true);

            // Request focus on the button
            doneButton.requestFocusInWindow();
        });

        // Wait for the user to click the button
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void closeFijiAndBdvs(ImageJ ij) {
        try {

            // Closes bdv windows
            SourceAndConverterBdvDisplayService sac_display_service =
                    ij.context().getService(SourceAndConverterBdvDisplayService.class);
            sac_display_service.getDisplays().forEach(BdvHandle::close);

            // Clears all sources
            SourceAndConverterService sac_service =
                    ij.context().getService(SourceAndConverterService.class);
            sac_service.remove(sac_service.getSourceAndConverters().toArray(new SourceAndConverter[0]));

            // Closes ij context
            ij.context().close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void startFiji(ImageJ ij) {
        try {
            SwingUtilities.invokeAndWait(() -> ij.ui().showUI());
            try {
                // Increase default font size
                UIManager.put("defaultFont", new Font("SansSerif", Font.PLAIN, 16));
                FlatDarkLaf.setup();
            } catch (Exception e) {
                System.err.println("Failed to set FlatLaf look and feel: " + e.getMessage());
            }

            updateAllFramesLookAndFeel();

        } catch (InterruptedException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    /**
     * Updates all existing Swing frames/windows to use the current Look and Feel.
     * This is necessary when the L&F is changed after some windows were already created.
     */
    private static void updateAllFramesLookAndFeel() {
        SwingUtilities.invokeLater(() -> {
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
                window.pack();
            }
        });
    }

    /** Default output directory for screenshots */
    public static final File DEFAULT_OUTPUT_DIR = new File("documentation/resources");

    /** Default wait time in milliseconds before capturing */
    public static final long DEFAULT_WAIT_MS = 4000;

    // ==================== ONE-LINER METHODS ====================

    /**
     * Captures all visible windows with a prefix.
     * Waits for rendering, captures JFrames, and prints results.
     * <p>
     * Example usage:
     * <pre>
     * DemoHelper.shot("MyDemo_01_step");                           // All windows
     * DemoHelper.shot("MyDemo_01_step", "BDV", "Labkit");          // Only BDV and Labkit windows
     * DemoHelper.shot("MyDemo_01_step", "ImageJ");                 // Only ImageJ window
     * </pre>
     *
     * @param prefix prefix for screenshot filenames (e.g., demo class name)
     * @param titleFilters optional filters - only windows whose title contains one of these strings will be captured.
     *                     If empty, all visible windows are captured.
     */
    public static void shot(String prefix, String... titleFilters) {
        shot(DEFAULT_OUTPUT_DIR, prefix, DEFAULT_WAIT_MS, titleFilters);
    }

    /**
     * Captures windows with a prefix and custom wait time.
     * <p>
     * Example usage: {@code DemoHelper.shot("MyDemo", 2000, "BDV");}
     *
     * @param prefix prefix for screenshot filenames
     * @param waitMs milliseconds to wait before capturing
     * @param titleFilters optional filters - only windows whose title contains one of these strings will be captured
     */
    public static void shotWithWait(String prefix, long waitMs, String... titleFilters) {
        shot(DEFAULT_OUTPUT_DIR, prefix, waitMs, titleFilters);
    }

    /**
     * Captures windows to a custom directory with filtering.
     *
     * @param outputDir directory to save screenshots
     * @param prefix prefix for screenshot filenames
     * @param waitMs milliseconds to wait before capturing
     * @param titleFilters optional filters - only windows whose title contains one of these strings will be captured
     */
    public static void shot(File outputDir, String prefix, long waitMs, String... titleFilters) {
        try {
            waitFor(waitMs);
            List<File> files = captureAllFramesOffscreen(outputDir, prefix, titleFilters);
            String filterInfo = (titleFilters.length > 0)
                    ? " (filtered by: " + String.join(", ", titleFilters) + ")"
                    : "";
            System.out.println("[Screenshot] Captured " + files.size() + " frame(s) with prefix '" + prefix + "'" + filterInfo + ":");
            for (File f : files) {
                System.out.println("  -> " + f.getPath());
            }
        } catch (Exception e) {
            System.err.println("[Screenshot] Failed to capture: " + e.getMessage());
        }
    }

    // ==================== DETAILED METHODS ====================

    /**
     * Captures a screenshot of a specific JFrame.
     *
     * @param frame the frame to capture
     * @param outputFile the file to save the screenshot to (PNG format)
     * @throws AWTException if the platform doesn't support screen capture
     * @throws IOException if the file cannot be written
     */
    public static void captureFrame(JFrame frame, File outputFile) throws AWTException, IOException {
        // Ensure parent directories exist
        outputFile.getParentFile().mkdirs();

        // Get the frame bounds on screen
        Rectangle bounds = frame.getBounds();

        // Use Robot to capture the screen region
        Robot robot = new Robot();
        BufferedImage screenshot = robot.createScreenCapture(bounds);

        // Write to file
        ImageIO.write(screenshot, "png", outputFile);
    }

    /**
     * Captures a screenshot of a specific JFrame using component painting.
     * This method doesn't require the window to be on top and works better
     * in headless/virtual display environments.
     *
     * @param frame the frame to capture
     * @param outputFile the file to save the screenshot to (PNG format)
     * @throws IOException if the file cannot be written
     */
    public static void captureFrameOffscreen(JFrame frame, File outputFile) throws IOException {
        // Ensure parent directories exist
        outputFile.getParentFile().mkdirs();

        // Create a buffered image with the frame's size
        int width = frame.getWidth();
        int height = frame.getHeight();

        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("Frame has invalid dimensions: " + width + "x" + height);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Paint the frame content
        frame.paint(g2d);
        g2d.dispose();

        // Write to file
        ImageIO.write(image, "png", outputFile);
    }

    /**
     * Gets all visible JFrames in the application.
     *
     * @return list of all visible JFrame instances
     */
    public static List<JFrame> getAllVisibleFrames() {
        return getFilteredVisibleFrames();
    }

    /**
     * Gets visible JFrames filtered by title.
     * If no filters are provided, returns all visible frames.
     *
     * @param titleFilters optional filters - only frames whose title contains one of these strings are returned
     * @return list of matching visible JFrame instances
     */
    public static List<JFrame> getFilteredVisibleFrames(String... titleFilters) {
        List<JFrame> frames = new ArrayList<>();
        for (Window window : Window.getWindows()) {
            if (window instanceof JFrame && window.isVisible()) {
                JFrame frame = (JFrame) window;
                if (matchesTitleFilter(frame.getTitle(), titleFilters)) {
                    frames.add(frame);
                }
            }
        }
        return frames;
    }

    /**
     * Checks if a window title matches any of the provided filters.
     *
     * @param title the window title to check
     * @param filters the filters to match against (case-insensitive, partial match)
     * @return true if filters is empty OR title contains any of the filter strings
     */
    private static boolean matchesTitleFilter(String title, String... filters) {
        if (filters == null || filters.length == 0) {
            return true; // No filters = match all
        }
        if (title == null) {
            return false;
        }
        String lowerTitle = title.toLowerCase();
        for (String filter : filters) {
            if (filter != null && lowerTitle.contains(filter.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Captures screenshots of visible JFrames and saves them to a directory.
     * Files are named based on the frame title or a generated index.
     *
     * @param outputDir the directory to save screenshots to
     * @param prefix optional prefix for filenames (can be null)
     * @param useRobot if true, uses Robot for screen capture; if false, uses offscreen painting
     * @param titleFilters optional filters - only windows whose title contains one of these strings will be captured
     * @return list of files that were created
     * @throws AWTException if Robot capture is used and platform doesn't support it
     * @throws IOException if files cannot be written
     */
    public static List<File> captureAllFrames(File outputDir, String prefix, boolean useRobot, String... titleFilters)
            throws AWTException, IOException {
        List<File> capturedFiles = new ArrayList<>();
        List<JFrame> frames = getFilteredVisibleFrames(titleFilters);

        int index = 0;
        for (JFrame frame : frames) {
            System.out.println(frame.getTitle());
            String filename = generateFilename(frame, prefix, index);
            File outputFile = new File(outputDir, filename);

            if (useRobot) {
                captureFrame(frame, outputFile);
            } else {
                captureFrameOffscreen(frame, outputFile);
            }

            capturedFiles.add(outputFile);
            index++;
        }

        return capturedFiles;
    }

    /**
     * Captures screenshots of visible JFrames using offscreen painting.
     * This is the preferred method for CI/automated environments.
     *
     * @param outputDir the directory to save screenshots to
     * @param prefix optional prefix for filenames (can be null)
     * @param titleFilters optional filters - only windows whose title contains one of these strings will be captured
     * @return list of files that were created
     * @throws IOException if files cannot be written
     */
    public static List<File> captureAllFramesOffscreen(File outputDir, String prefix, String... titleFilters) throws IOException {
        try {
            return captureAllFrames(outputDir, prefix, false, titleFilters);
        } catch (AWTException e) {
            // This shouldn't happen with offscreen capture, but just in case
            throw new IOException("Unexpected AWTException during offscreen capture", e);
        }
    }

    /**
     * Generates a sanitized filename for a frame screenshot.
     */
    private static String generateFilename(JFrame frame, String prefix, int index) {
        String title = frame.getTitle();
        String baseName;

        if (title != null && !title.trim().isEmpty()) {
            // Sanitize the title for use as filename
            baseName = title.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            // Remove consecutive underscores
            baseName = baseName.replaceAll("_+", "_");
            // Trim underscores from start/end
            baseName = baseName.replaceAll("^_|_$", "");
        } else {
            baseName = "frame_" + index;
        }

        if (prefix != null && !prefix.isEmpty()) {
            baseName = prefix + "_" + baseName;
        }

        return baseName + ".png";
    }

    /**
     * Waits for all pending Swing events to be processed.
     * Useful to ensure windows are fully rendered before capturing.
     */
    public static void waitForSwingToSettle() {
        try {
            // Process all pending Swing events
            SwingUtilities.invokeAndWait(() -> {});
            // Small additional delay to ensure rendering is complete
            Thread.sleep(500);
        } catch (Exception e) {
            // Ignore interruption
        }
    }

    /**
     * Waits for a specific duration (in milliseconds).
     * Useful when windows need time to fully initialize.
     *
     * @param millis time to wait in milliseconds
     */
    public static void waitFor(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
