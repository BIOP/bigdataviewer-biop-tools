package ch.epfl.biop.wizard;

import ij.IJ;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandInfo;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;

import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

// WIP TODO or to DISCARD
public class Wizard {

    final Map<Integer, CommandInfo> steps;

    final Map<Vertex, Vertex> edges;
    final Context ctx;

    public static WizardBuilder builder(Context ctx) {
        return new WizardBuilder(ctx);
    }

    public Wizard(Context ctx, Map<Integer, CommandInfo> steps, Map<Vertex, Vertex> pipes) {
        this.steps = steps;
        this.edges = pipes;
        this.ctx = ctx;
    }

    public static class WizardBuilder {
        Map<Integer, CommandInfo> steps = new HashMap<>();
        int stepCounter = 0;
        Map<Vertex, Vertex> edges = new HashMap<>();
        final Context ctx;

        public WizardBuilder(Context ctx) {
            this.ctx = ctx;
        }

        public WizardBuilder nextCommand(CommandInfo commandType) {
            steps.put(stepCounter, commandType);
            stepCounter++;
            return this;
        }

        public WizardBuilder nextCommand(Class<? extends Command> commandType) {
            CommandInfo ci = ctx.getService(CommandService.class).getCommand(commandType);
            System.out.println(ci);
            steps.put(stepCounter, ci);
            stepCounter++;
            return this;
        }

        public WizardBuilder connect(Integer stepOut, String parameterNameOut, Integer stepIn, String parameterNameIn) {
            Vertex eout = new Vertex(stepOut, parameterNameOut);
            Vertex ein = new Vertex(stepIn, parameterNameIn);
            edges.put(ein, eout); // Better in this direction
            return this;
        }

        public Wizard build() {
            return new Wizard(ctx, steps, edges);
        }

    }

    public void run() {
        CommandService cs = ctx.getService(CommandService.class);

        boolean wizardDone = false;
        boolean wizardSuccess = false;

        int indexStep = 0;

        Map<Vertex, Object> storedOutputs = new HashMap<>();

        while (!wizardDone) {
            CommandInfo commandType = steps.get(indexStep);
            try {

                Map<String, Object> kvArgs = new HashMap<>();
                int iStep = indexStep;
                // Get inputs from previous output steps ?
                List<Vertex> inputs = edges.keySet().stream().filter(e -> e.wizardStep == iStep).collect(Collectors.toList());

                for (Vertex input : inputs) {
                    if (!edges.containsKey(input)) {
                        IJ.log("Input "+input+" not found!");
                    }
                    if (!storedOutputs.containsKey(edges.get(input))) {
                        IJ.log("Corresponding output to input "+input+" not found!");
                    }
                    kvArgs.put(input.parameterName, storedOutputs.get(edges.get(input)));
                }

                Future<CommandModule> futureModule = cs.run(commandType.getPluginClass(), true, kvArgs);
                CommandModule cm = futureModule.get();

                // Store Required Outputs
                List<Vertex> outputs = edges.values().stream().filter(e -> e.wizardStep == iStep).collect(Collectors.toList());

                for (Vertex output : outputs) {
                    storedOutputs.put(output, cm.getOutput(output.parameterName));
                }

                indexStep++;
                if (indexStep == steps.size()) {
                    wizardDone = true;
                    wizardSuccess = true;
                }
            } catch (Exception e) {
                IJ.error("There was a problem with the current step, would like to retry ?");
                e.printStackTrace();
                // TODO : retry
                wizardDone = true;
                wizardSuccess = false;
            }
        }
    }

    public static class Vertex {
        public Vertex(Integer iStep, String parameterName) {
            this.parameterName = parameterName;
            this.wizardStep = iStep;
        }
        final int wizardStep;
        final String parameterName;

        public String toString() {
            return "[Wizard step:"+wizardStep+"; '"+parameterName+"']";
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;
            if (!(o instanceof Vertex))
                return false;
            Vertex other = (Vertex)o;
            return (this.wizardStep == other.wizardStep) && (this.parameterName.equals(other.parameterName) );
        }

        @Override
        public final int hashCode() {
            int result = 17;
            if (parameterName != null) {
                result = 31 * result + parameterName.hashCode() + wizardStep;
            }
            return result;
        }

    }

}
