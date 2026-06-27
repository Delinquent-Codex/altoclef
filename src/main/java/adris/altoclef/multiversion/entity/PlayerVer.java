package adris.altoclef.multiversion.entity;

import adris.altoclef.multiversion.Pattern;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class PlayerVer {


    public static void sendChatMessage(LocalPlayer player,String content) {
        player.connection.sendChat(content);
    }

    public static void sendChatCommand(LocalPlayer player,String content) {
        player.connection.sendCommand(content);
    }

    @Pattern
    private static ItemStack getCursorStack(Player player) {
        return player.containerMenu.getCarried();
    }

    @Pattern
    private static Container getInventory(Player player) {
        return player.getInventory();
    }

    public static boolean inPowderedSnow(Player player) {
        return player.isInPowderSnow;
    }



}
