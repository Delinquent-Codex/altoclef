package adris.altoclef.commandsystem;

import adris.altoclef.commandsystem.args.Arg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.commandsystem.exception.RuntimeCommandException;
import java.util.ArrayList;
import java.util.List;

public class ArgParser {

    private final Arg<?>[] args;
    private int argCounter;
    private StringReader reader;

    public ArgParser(Arg<?>... args) {
        this.args = args;
        argCounter = 0;
    }

    public void loadArgs(String line, boolean removeFirst) throws CommandException {
        reader = new StringReader(line);
        // Discard the first element since, well, it will always be the name of the command.
        if (removeFirst && reader.hasNext()) {
            reader.next();
        }

        argCounter = 0;
    }

    // Get the next argument.
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) throws CommandException {
        if (argCounter >= args.length) {
            throw new RuntimeCommandException("You tried grabbing more arguments than you had... Bad move.");
        }

        if (type.isAssignableFrom(args[argCounter].getType())) {
            Arg<T> arg = (Arg<T>) args[argCounter];

            if (!reader.hasNext()) {
                if (args[argCounter].hasDefault) {
                    return arg.defaultValue;
                } else {
                    throw new RuntimeCommandException("Command not finished, expected "+arg.getTypeName());
                }
            }

            T result = arg.parseArg(reader);
            argCounter++;

            return result;
        }

        throw new RuntimeCommandException("Not the same type! ("+args[argCounter].getType() + " VS "+type+")");
    }

    public <T> List<T> getList(Class<T> elementType) throws CommandException {
        List<?> values = get(List.class);
        if (values == null) {
            return null;
        }

        List<T> result = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!elementType.isInstance(value)) {
                throw new RuntimeCommandException("Expected a list of " + elementType.getSimpleName()
                        + " but found " + value.getClass().getSimpleName());
            }
            result.add(elementType.cast(value));
        }
        return result;
    }

    public Arg<?>[] getArgs() {
        return args;
    }

}
