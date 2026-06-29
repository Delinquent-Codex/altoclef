package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.*;
import adris.altoclef.commandsystem.args.ItemTargetArg;
import adris.altoclef.commandsystem.args.ListArg;
import adris.altoclef.commandsystem.exception.BadCommandSyntaxException;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.commandsystem.exception.RuntimeCommandException;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemComponentHelper;
import adris.altoclef.util.helpers.ItemHelper;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.world.item.Item;

public class EquipCommand extends Command {

    public EquipCommand() {
        super("equip", "Equips items",
                new ListArg<>(new EquipmentItemArg("equipment"), "[equippable_items]")
                        .addAlias("leather", Arrays.stream(ItemHelper.LEATHER_ARMORS).map(ItemTarget::new).toList())
                        .addAlias("iron", Arrays.stream(ItemHelper.IRON_ARMORS).map(ItemTarget::new).toList())
                        .addAlias("gold", Arrays.stream(ItemHelper.GOLDEN_ARMORS).map(ItemTarget::new).toList())
                        .addAlias("diamond", Arrays.stream(ItemHelper.DIAMOND_ARMORS).map(ItemTarget::new).toList())
                        .addAlias("netherite", Arrays.stream(ItemHelper.NETHERITE_ARMORS).map(ItemTarget::new).toList())
        );
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        List<ItemTarget> items = parser.getList(ItemTarget.class);

        for (ItemTarget target : items) {
            for (Item item : target.getMatches()) {
                if (!ItemComponentHelper.isEquippable(item)) {
                    throw new RuntimeCommandException("'"+item.toString().toUpperCase() + "' cannot be equipped!");
                }
            }
        }

        mod.runUserTask(new EquipArmorTask(items.toArray(new ItemTarget[0])), this::finish);
    }


    // this is kinda meh way to do it
    private static class EquipmentItemArg extends ItemTargetArg {

        public EquipmentItemArg(String name) {
            super(name);
        }

        @Override
        protected StringParser<ItemTarget> getParser() {
            return this::parseLocal;
        }

        private ItemTarget parseLocal(StringReader reader) throws CommandException {
            ItemTarget parsed = super.getParser().parse(reader);
            if (Arrays.stream(parsed.getMatches()).noneMatch(ItemComponentHelper::isEquippable)) {
                throw new BadCommandSyntaxException("Item '" + parsed + "' is not equipment");
            }

            return parsed;
        }

        @Override
        public Stream<String> getSuggestions(StringReader reader) {
            return super.getSuggestions(reader).filter(suggestion ->
                    Arrays.stream(new ItemTarget(suggestion).getMatches()).anyMatch(ItemComponentHelper::isEquippable));
        }
    }


}
