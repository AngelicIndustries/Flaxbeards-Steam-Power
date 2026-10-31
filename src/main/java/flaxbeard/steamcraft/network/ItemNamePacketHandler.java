package flaxbeard.steamcraft.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatAllowedCharacters;
import flaxbeard.steamcraft.gui.ContainerSteamAnvil;
import flaxbeard.steamcraft.tile.TileEntitySteamHammer;

public class ItemNamePacketHandler implements IMessageHandler<ItemNamePacket, IMessage> {
    @Override
    public IMessage onMessage(ItemNamePacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        ServerPacketDispatcher.INSTANCE.enqueue(player, () -> handle(message, player));
        return null;
    }

    static void handle(ItemNamePacket message, EntityPlayerMP player) {
        if (message.name == null || message.name.length() > 40
            || !message.name.equals(ChatAllowedCharacters.filerAllowedCharacters(message.name))
            || !(player.openContainer instanceof ContainerSteamAnvil)
            || !ServerPacketDispatcher.canAccess(player, message.x, message.y, message.z, 8.0D)) {
            return;
        }
        ContainerSteamAnvil anvil = (ContainerSteamAnvil) player.openContainer;
        TileEntity hammer = player.worldObj.getTileEntity(message.x, message.y, message.z);
        if (hammer instanceof TileEntitySteamHammer && anvil.hammer == hammer && anvil.canInteractWith(player)) {
            anvil.updateItemName(message.name);
            hammer.markDirty();
            player.worldObj.markBlockForUpdate(message.x, message.y, message.z);
        }
    }
}
