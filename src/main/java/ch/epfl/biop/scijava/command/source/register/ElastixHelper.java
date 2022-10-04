package ch.epfl.biop.scijava.command.source.register;

import ch.epfl.biop.wrappers.elastix.Elastix;
import ch.epfl.biop.wrappers.ij2command.BiopWrappersSet;
import ch.epfl.biop.wrappers.transformix.Transformix;
import org.scijava.Context;
import org.scijava.command.CommandService;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class ElastixHelper {

    public static boolean checkOrSetLocal(Context ctx) {

        while (!new File(Elastix.exePath).exists() || !new File(Transformix.exePath).exists()) {
            try {
                ctx.getService(CommandService.class).run(BiopWrappersSet.class, true).get();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            } catch (ExecutionException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }
}
