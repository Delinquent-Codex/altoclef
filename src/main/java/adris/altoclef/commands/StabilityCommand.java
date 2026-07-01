package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;

public class StabilityCommand extends Command {
    public StabilityCommand() {
        super("stability", "Show bounded task, survival, path, and recovery diagnostics");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        for (String line : mod.getStabilityDiagnostics().snapshot().conciseLines()) {
            mod.log(line);
        }
        finish();
    }
}
