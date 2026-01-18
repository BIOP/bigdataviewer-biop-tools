# Demo Documentation System

This document explains how to create self-documenting demos that generate markdown documentation with screenshots.

## Overview

The demo documentation system allows you to annotate Java demo files with special markers that are parsed to generate markdown documentation automatically. Screenshots are captured at key steps during demo execution.

Since demos typically use **SciJava Commands** (the same commands accessible via the Fiji menu), the generated documentation includes:
- **Groovy code** showing how to script each step
- **GUI instructions** showing the menu path and parameters for interactive use

## How It Works

1. **Annotate your demo** with `@doc-step` and `@doc-command` markers in comments
2. **Add screenshot calls** using `DemoHelper.shot("prefix_stepname")`
3. **Run the demo** to capture screenshots to `documentation/resources/`
4. **Run the generator** to produce markdown in `documentation/demos/`

## Marker Syntax

### Step Markers

Use structured comments to mark documentation steps:

```java
// @doc-step: Title of the step
// Description of what this step does. Can span
// multiple lines until the next @doc marker or code.

// Your code for this step...
DemoHelper.shot("DemoName_01_stepname");  // Capture screenshot
```

### Command Markers

When a step uses a SciJava Command, add the `@doc-command` marker to include GUI and Groovy equivalents:

```java
// @doc-step: Open the LLS7 Dataset
// Load the downloaded CZI file using the LLS7 opener command.
// This performs live deskewing of the lattice light sheet data.
// @doc-command: ch.epfl.biop.scijava.command.spimdata.LLS7OpenDatasetCommand

ij.command().run(LLS7OpenDatasetCommand.class, true,
        "czi_file", fileCZI,
        "legacy_xy_mode", false).get();

DemoHelper.shot("DemoLabkit_02_dataset_loaded");
```

The generator will automatically:
1. Extract the menu path from the command's `@Plugin` annotation
2. List the command parameters with their descriptions
3. Generate equivalent Groovy scripting code

### Rules

- `@doc-step:` must be at the start of a comment line (after `//`)
- The text after `:` becomes the step title
- Subsequent comment lines (until code or another marker) become the description
- `@doc-command:` specifies the fully qualified class name of the command
- Step descriptions support multiple lines

## Generated Output for Commands

When a step includes `@doc-command`, the generator produces documentation like this:

---

### Step 2: Open the LLS7 Dataset

Load the downloaded CZI file using the LLS7 opener command.
This performs live deskewing of the lattice light sheet data.

#### Using the GUI

**Menu:** `Plugins > BigDataViewer-Playground > BDVDataset > Create BDV Dataset [Zeiss LLS7]`

| Parameter | Description |
|-----------|-------------|
| CZI LLS7 File | The CZI file from a Zeiss LLS7 acquisition to open |
| Use Legacy XY Mode | When checked, uses legacy XY orientation for compatibility with older datasets |

#### Using Groovy Script

```groovy
#@ CommandService cmd

cmd.run("ch.epfl.biop.scijava.command.spimdata.LLS7OpenDatasetCommand", true,
    "czi_file", new File("/path/to/your/file.czi"),
    "legacy_xy_mode", false
).get()
```

![Dataset loaded](../resources/DemoLabkit_02_dataset_loaded_ImageJ.png)

---

### Screenshot Naming Convention

Use this pattern for screenshot prefixes:
```
DemoName_XX_stepname
```

Where:
- `DemoName` matches the demo class name
- `XX` is a two-digit step number (01, 02, etc.)
- `stepname` is a short identifier for the step

Example:
```java
DemoHelper.shot("DemoLabkitIntegration_02_labkit_open");
```

This creates files like:
```
documentation/resources/DemoLabkitIntegration_02_labkit_open_ImageJ.png
documentation/resources/DemoLabkitIntegration_02_labkit_open_Labkit.png
```

## Adding Documentation to a Demo

### Step 1: Add the Javadoc Header

Ensure your demo class has a Javadoc comment describing what the demo does:

```java
/**
 * Demo showing how to use Labkit with SourceAndConverter from BigDataViewer-Playground.
 * <p>
 * This demo loads an LLS7 dataset and opens it in Labkit for segmentation.
 * </p>
 */
public class DemoLabkitIntegration {
```

### Step 2: Add Step Markers

Add `@doc-step` markers before each significant section:

```java
// @doc-step: Initialize ImageJ
// Start the ImageJ application context and show the user interface.
final ImageJ ij = new ImageJ();
ij.ui().showUI();
DemoHelper.shot("DemoName_01_init");
```

### Step 3: Add Screenshot Calls

After visual changes (windows appearing, data loading), add:

```java
DemoHelper.shot("DemoName_XX_stepname");
```

The `shot()` method:
- Waits 4 seconds (default) for rendering to complete
- Captures all visible JFrames
- Saves PNGs to `documentation/resources/`

## Running the Demo

Execute your demo's `main()` method. The demo will run and capture screenshots at each `DemoHelper.shot()` call.

```bash
# From your IDE, run the demo class
# Or use Maven:
mvn exec:java -Dexec.mainClass="DemoLabkitIntegration"
```

## Generating Documentation

After running the demo (and capturing screenshots), run the generator:

```bash
mvn exec:java -Dexec.mainClass="DemoDocumentationGenerator" -Dexec.args="DemoLabkitIntegration"
```

Or run `DemoDocumentationGenerator.main()` from your IDE with the demo class name as an argument.

### Generator Output

The generator creates:
- `documentation/demos/DemoName.md` - The markdown documentation

The markdown includes:
- Title and description from class Javadoc
- Each step with its title, description, code snippet, and screenshots
- A prerequisites section
- Links to screenshot images

## Directory Structure

```
bigdataviewer-biop-tools/
├── documentation/
│   ├── demos/           # Generated markdown files
│   │   └── DemoLabkitIntegration.md
│   └── resources/       # Screenshots captured during demo runs
│       ├── DemoLabkitIntegration_01_init_ImageJ.png
│       └── DemoLabkitIntegration_02_labkit_open_Labkit.png
├── src/test/java/
│   ├── DemoLabkitIntegration.java    # Annotated demo
│   ├── DemoDocumentationGenerator.java  # Generator
│   └── DemoHelper.java               # Screenshot utility
└── DEMO_DOCUMENTATION.md             # This file
```

## Example: Annotated Demo

```java
/**
 * Demo showing feature X with command integration.
 */
public class DemoFeatureX {

    public static void main(String[] args) throws Exception {
        // @doc-step: Initialize ImageJ
        // Create and display the ImageJ application.
        // This is required before running any commands.
        ImageJ ij = new ImageJ();
        ij.ui().showUI();
        DemoHelper.shot("DemoFeatureX_01_init");

        // @doc-step: Download Sample Data
        // Download a sample dataset from Zenodo.
        // This step is only needed for the demo - in practice,
        // you would use your own data files.
        File data = DatasetHelper.getDataset("https://zenodo.org/...");

        // @doc-step: Open the Dataset
        // Load the dataset using the LLS7 opener command.
        // @doc-command: ch.epfl.biop.scijava.command.spimdata.LLS7OpenDatasetCommand
        ij.command().run(LLS7OpenDatasetCommand.class, true,
                "czi_file", data,
                "legacy_xy_mode", false).get();
        DemoHelper.shot("DemoFeatureX_02_loaded");

        // @doc-step: Display in BigDataViewer
        // Show the loaded sources in a BDV window.
        // @doc-command: sc.fiji.bdvpg.scijava.command.bdv.BdvSourcesShowCommand
        ij.command().run(BdvSourcesShowCommand.class, true,
                "sources", sources).get();
        DemoHelper.shot("DemoFeatureX_03_displayed");
    }
}
```

### Generated Output

For the "Open the Dataset" step above, the generator produces:

**Using the GUI:**
Navigate to `Plugins > BigDataViewer-Playground > BDVDataset > Create BDV Dataset [Zeiss LLS7]`
and fill in the parameters in the dialog.

**Using Groovy Script:**
```groovy
#@ CommandService cmd

cmd.run("ch.epfl.biop.scijava.command.spimdata.LLS7OpenDatasetCommand", true,
    "czi_file", new File("/path/to/file.czi"),
    "legacy_xy_mode", false
).get()
```

## Tips

1. **Wait times**: If windows aren't fully rendered, increase wait time:
   ```java
   DemoHelper.shot("prefix", 6000);  // Wait 6 seconds
   ```

2. **Step numbering**: Use consistent two-digit numbering (01, 02, ...) to ensure correct ordering.

3. **Descriptive names**: Use meaningful step names for better screenshot filenames.

4. **One visual change per step**: Capture after each significant UI change for best documentation.

5. **Keep descriptions concise**: The description should explain what the step accomplishes, not repeat the code.

## Troubleshooting

### Screenshots are blank or incomplete
- Increase the wait time in `DemoHelper.shot()`
- Ensure the windows are fully visible on screen

### Generator can't find screenshots
- Verify the prefix pattern matches between code and files
- Check that screenshots are in `documentation/resources/`

### Missing steps in output
- Ensure `@doc-step:` is spelled correctly with the colon
- Check that the marker is at the start of a comment line
