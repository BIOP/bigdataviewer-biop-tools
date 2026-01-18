/*-
 * #%L
 * Demo Documentation Generator for BigDataViewer-Playground - BIOP - EPFL
 * %%
 * Copyright (C) 2024 - 2025 EPFL
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Generates markdown documentation from annotated demo Java files.
 * <p>
 * Parses {@code @doc-step} and {@code @doc-command} markers to create
 * structured documentation with code examples, GUI instructions, and screenshots.
 * </p>
 *
 * <p>Usage: Run with the demo class name as an argument:</p>
 * <pre>
 * DemoDocumentationGenerator.main(new String[]{"DemoLabkitIntegration"});
 * </pre>
 */
public class DemoDocumentationGenerator {

    private static final String SOURCE_DIR = "src/test/java";
    private static final String RESOURCES_DIR = "documentation/resources";
    private static final String OUTPUT_DIR = "documentation/demos";

    /**
     * Main entry point.
     *
     * @param args demo class name (e.g., "DemoLabkitIntegration")
     */
    public static void main(String[] args) throws Exception {
        /*if (args.length < 1) {
            System.err.println("Usage: DemoDocumentationGenerator <DemoClassName>");
            System.err.println("Example: DemoDocumentationGenerator DemoLabkitIntegration");
            System.exit(1);
        }*/

        String demoClassName = "DemoLabKitIntegration";//args[0];
        DemoDocumentationGenerator generator = new DemoDocumentationGenerator();
        generator.generate(demoClassName);
    }

    /**
     * Generates documentation for a demo class.
     *
     * @param demoClassName the simple name of the demo class
     */
    public void generate(String demoClassName) throws Exception {
        Path sourceFile = Paths.get(SOURCE_DIR, demoClassName + ".java");
        if (!Files.exists(sourceFile)) {
            throw new FileNotFoundException("Source file not found: " + sourceFile);
        }

        System.out.println("Parsing: " + sourceFile);
        String sourceCode = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8);

        // Parse the source file
        String classJavadoc = extractClassJavadoc(sourceCode);
        List<DocStep> steps = parseDocSteps(sourceCode);

        System.out.println("Found " + steps.size() + " documentation steps");

        // Find screenshots
        Path resourcesPath = Paths.get(RESOURCES_DIR);
        Map<Integer, List<Path>> screenshots = findScreenshots(resourcesPath, demoClassName);
        System.out.println("Found screenshots for " + screenshots.size() + " step(s)");

        // Generate markdown
        String markdown = generateMarkdown(demoClassName, classJavadoc, steps, screenshots);

        // Write output
        Path outputDir = Paths.get(OUTPUT_DIR);
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve(demoClassName + ".md");
        Files.write(outputFile, markdown.getBytes(StandardCharsets.UTF_8));

        System.out.println("Generated: " + outputFile);
    }

    /**
     * Extracts the class-level Javadoc comment.
     */
    private String extractClassJavadoc(String source) {
        // Match Javadoc before 'public class'
        Pattern pattern = Pattern.compile("/\\*\\*([^*]|\\*(?!/))*\\*/\\s*public\\s+class", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            String javadoc = matcher.group();
            // Remove the 'public class' part
            javadoc = javadoc.replaceAll("public\\s+class.*$", "");
            // Clean up Javadoc formatting
            return cleanJavadoc(javadoc);
        }
        return "";
    }

    /**
     * Cleans Javadoc formatting.
     */
    private String cleanJavadoc(String javadoc) {
        // Remove /** and */
        javadoc = javadoc.replaceAll("^/\\*\\*", "").replaceAll("\\*/\\s*$", "");
        // Remove leading * on each line
        javadoc = javadoc.replaceAll("(?m)^\\s*\\*\\s?", "");
        // Remove HTML tags like <p>
        javadoc = javadoc.replaceAll("</?p>", "\n");
        // Trim
        return javadoc.trim();
    }

    /**
     * Parses all @doc-step markers from the source.
     */
    private List<DocStep> parseDocSteps(String source) {
        List<DocStep> steps = new ArrayList<>();
        String[] lines = source.split("\n");

        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();

            if (line.startsWith("// @doc-step:")) {
                DocStep step = new DocStep();
                step.title = line.substring("// @doc-step:".length()).trim();

                // Collect description lines and markers
                StringBuilder description = new StringBuilder();
                StringBuilder manualInstructions = new StringBuilder();
                boolean inManualBlock = false;
                i++;
                while (i < lines.length) {
                    String descLine = lines[i].trim();
                    if (descLine.startsWith("// @doc-command:")) {
                        step.commandClass = descLine.substring("// @doc-command:".length()).trim();
                        i++;
                    } else if (descLine.startsWith("// @doc-manual:")) {
                        step.isManual = true;
                        String manualText = descLine.substring("// @doc-manual:".length()).trim();
                        if (!manualText.isEmpty()) {
                            manualInstructions.append(manualText).append("\n");
                        }
                        inManualBlock = true;
                        i++;
                    } else if (descLine.startsWith("//") && !descLine.startsWith("// @doc-")) {
                        String text = descLine.substring(2).trim();
                        if (inManualBlock) {
                            manualInstructions.append(text).append("\n");
                        } else {
                            description.append(text).append("\n");
                        }
                        i++;
                    } else {
                        break;
                    }
                }
                step.description = description.toString().trim();
                step.manualInstructions = manualInstructions.toString().trim();

                // Collect code until next @doc-step or end of method
                StringBuilder code = new StringBuilder();
                int braceDepth = 0;
                boolean inCode = true;
                int codeStartLine = i;

                while (i < lines.length && inCode) {
                    String codeLine = lines[i];
                    String trimmedLine = codeLine.trim();

                    // Stop at next @doc-step
                    if (trimmedLine.startsWith("// @doc-step:")) {
                        break;
                    }

                    // Track braces to find end of method
                    for (char c : codeLine.toCharArray()) {
                        if (c == '{') braceDepth++;
                        if (c == '}') braceDepth--;
                    }

                    // Stop at closing brace of method (simplified detection)
                    if (braceDepth < 0) {
                        break;
                    }

                    code.append(codeLine).append("\n");
                    i++;
                }

                step.codeSnippet = cleanCodeSnippet(code.toString());
                steps.add(step);
            } else {
                i++;
            }
        }

        return steps;
    }

    /**
     * Cleans up a code snippet for display.
     */
    private String cleanCodeSnippet(String code) {
        // Remove empty lines at start and end
        code = code.replaceAll("^\\s*\n", "").replaceAll("\n\\s*$", "");

        // Find minimum indentation
        String[] lines = code.split("\n");
        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                int indent = 0;
                for (char c : line.toCharArray()) {
                    if (c == ' ') indent++;
                    else if (c == '\t') indent += 4;
                    else break;
                }
                minIndent = Math.min(minIndent, indent);
            }
        }

        // Remove common indentation
        if (minIndent > 0 && minIndent < Integer.MAX_VALUE) {
            StringBuilder result = new StringBuilder();
            for (String line : lines) {
                if (line.length() > minIndent) {
                    result.append(line.substring(Math.min(minIndent, line.length()))).append("\n");
                } else {
                    result.append(line.trim()).append("\n");
                }
            }
            code = result.toString();
        }

        return code.trim();
    }

    /**
     * Finds screenshots matching the demo prefix, grouped by step number.
     */
    private Map<Integer, List<Path>> findScreenshots(Path resourcesDir, String demoName) throws IOException {
        Map<Integer, List<Path>> screenshots = new TreeMap<>();

        if (!Files.exists(resourcesDir)) {
            return screenshots;
        }

        // Pattern matches: DemoName_XX_anything.png where XX is the step number
        Pattern pattern = Pattern.compile(Pattern.quote(demoName) + "_(\\d+)_.*\\.png", Pattern.CASE_INSENSITIVE);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(resourcesDir, "*.png")) {
            for (Path file : stream) {
                String filename = file.getFileName().toString();
                Matcher matcher = pattern.matcher(filename);
                if (matcher.matches()) {
                    int stepNumber = Integer.parseInt(matcher.group(1));
                    screenshots.computeIfAbsent(stepNumber, k -> new ArrayList<>()).add(file);
                }
            }
        }

        return screenshots;
    }

    /**
     * Generates markdown documentation.
     */
    private String generateMarkdown(String demoName, String classJavadoc,
                                     List<DocStep> steps, Map<Integer, List<Path>> screenshots) {
        StringBuilder md = new StringBuilder();

        // Title
        md.append("# ").append(humanize(demoName)).append("\n\n");

        // Description from Javadoc
        if (!classJavadoc.isEmpty()) {
            md.append(classJavadoc).append("\n\n");
        }

        // Prerequisites
        md.append("## Prerequisites\n\n");
        md.append("- Fiji with BigDataViewer-Playground installed\n");
        md.append("- Internet connection (for downloading sample data)\n\n");
        md.append("- Zeiss Quick Start CZI reader enabled (for proper CZI file handling)\n\n");

        // Steps
        int stepNumber = 1;
        for (DocStep step : steps) {
            md.append("## Step ").append(stepNumber).append(": ").append(step.title);
            if (step.isManual) {
                md.append(" *(Manual)*");
            }
            md.append("\n\n");

            if (!step.description.isEmpty()) {
                md.append(step.description).append("\n\n");
            }

            // If this is a manual step, add instructions callout
            if (step.isManual && step.manualInstructions != null && !step.manualInstructions.isEmpty()) {
                md.append("> **Manual Action Required**\n");
                md.append(">\n");
                for (String line : step.manualInstructions.split("\n")) {
                    md.append("> ").append(line).append("\n");
                }
                md.append("\n");
            }

            // If this step has a command, add GUI and Groovy sections
            if (step.commandClass != null && !step.commandClass.isEmpty()) {
                CommandInfo cmdInfo = getCommandInfo(step.commandClass);
                if (cmdInfo != null) {
                    md.append("### Using the GUI\n\n");
                    md.append("**Menu:** `").append(cmdInfo.menuPath.replace(">", " > ")).append("`\n\n");

                    if (!cmdInfo.parameters.isEmpty()) {
                        md.append("| Parameter | Description |\n");
                        md.append("|-----------|-------------|\n");
                        for (ParamInfo param : cmdInfo.parameters) {
                            md.append("| ").append(param.label).append(" | ")
                              .append(param.description).append(" |\n");
                        }
                        md.append("\n");
                    }

                    md.append("### Using Groovy Script\n\n");
                    md.append("```groovy\n");
                    md.append("#@ CommandService cmd\n\n");
                    md.append("cmd.run(\"").append(step.commandClass).append("\", true");
                    for (ParamInfo param : cmdInfo.parameters) {
                        if (!param.isService) {
                            md.append(",\n    \"").append(param.name).append("\", ")
                              .append(param.exampleValue);
                        }
                    }
                    md.append("\n).get()\n");
                    md.append("```\n\n");
                }
            }

            // Code snippet
            if (!step.codeSnippet.isEmpty()) {
                md.append("### Java Code\n\n");
                md.append("```java\n");
                md.append(step.codeSnippet);
                md.append("\n```\n\n");
            }

            // Screenshots for this step
            if (screenshots.containsKey(stepNumber)) {
                for (Path screenshot : screenshots.get(stepNumber)) {
                    String filename = screenshot.getFileName().toString();
                    // Extract window title from filename (remove prefix and extension)
                    String windowTitle = extractWindowTitle(filename, demoName, stepNumber);
                    md.append("![").append(windowTitle).append("](../resources/").append(filename).append(")\n\n");
                }
            }

            stepNumber++;
        }

        return md.toString();
    }

    /**
     * Extracts the window title from a screenshot filename.
     * Format: DemoName_XX_stepname_WindowTitle.png -> WindowTitle (with underscores replaced by spaces)
     */
    private String extractWindowTitle(String filename, String demoName, int stepNumber) {
        // Remove extension
        String name = filename.replace(".png", "");
        // Pattern to remove: DemoName_XX_stepname_
        // We find the last segment after the step number pattern
        Pattern pattern = Pattern.compile(Pattern.quote(demoName) + "_" + String.format("%02d", stepNumber) + "_[^_]+_(.+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(name);
        if (matcher.matches()) {
            return matcher.group(1).replace("_", " ");
        }
        // Fallback: just remove extension and return
        return name.replace("_", " ");
    }

    /**
     * Converts a class name to human-readable form.
     */
    private String humanize(String className) {
        // Remove "Demo" prefix if present
        if (className.startsWith("Demo")) {
            className = className.substring(4);
        }
        // Add spaces before capital letters
        return className.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    /**
     * Gets command information via reflection.
     */
    private CommandInfo getCommandInfo(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            CommandInfo info = new CommandInfo();

            // Get menu path from @Plugin annotation
            Plugin pluginAnnotation = clazz.getAnnotation(Plugin.class);
            if (pluginAnnotation != null) {
                info.menuPath = pluginAnnotation.menuPath();
                info.description = pluginAnnotation.description();
            }

            // Get parameters from @Parameter annotations
            for (Field field : clazz.getDeclaredFields()) {
                Parameter paramAnnotation = field.getAnnotation(Parameter.class);
                if (paramAnnotation != null) {
                    ParamInfo param = new ParamInfo();
                    param.name = field.getName();
                    param.label = paramAnnotation.label().isEmpty() ? field.getName() : paramAnnotation.label();
                    param.description = paramAnnotation.description();
                    param.type = field.getType();
                    param.isService = isServiceType(field.getType());
                    param.exampleValue = getExampleValue(field.getType(), param.name);
                    info.parameters.add(param);
                }
            }

            return info;
        } catch (ClassNotFoundException e) {
            System.err.println("Warning: Could not load command class: " + className);
            return null;
        }
    }

    /**
     * Checks if a type is a SciJava service (should be excluded from script examples).
     */
    private boolean isServiceType(Class<?> type) {
        String typeName = type.getName();
        return typeName.contains("Service") ||
               typeName.contains("Context") ||
               typeName.equals("org.scijava.Context");
    }

    /**
     * Gets an example value for a parameter type.
     */
    private String getExampleValue(Class<?> type, String name) {
        if (type == File.class) {
            return "new File(\"/path/to/your/file\")";
        } else if (type == String.class) {
            return "\"value\"";
        } else if (type == boolean.class || type == Boolean.class) {
            return "false";
        } else if (type == int.class || type == Integer.class) {
            return "0";
        } else if (type == double.class || type == Double.class) {
            return "0.0";
        } else if (type == long.class || type == Long.class) {
            return "0L";
        } else {
            return "/* " + type.getSimpleName() + " */";
        }
    }

    // Inner classes for data structures

    private static class DocStep {
        String title;
        String description;
        String commandClass;
        String codeSnippet;
        boolean isManual = false;
        String manualInstructions;
    }

    private static class CommandInfo {
        String menuPath = "";
        String description = "";
        List<ParamInfo> parameters = new ArrayList<>();
    }

    private static class ParamInfo {
        String name;
        String label;
        String description;
        Class<?> type;
        boolean isService;
        String exampleValue;
    }
}