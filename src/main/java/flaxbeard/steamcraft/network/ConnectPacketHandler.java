package flaxbeard.steamcraft.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import flaxbeard.steamcraft.api.IPipeWrench;
import flaxbeard.steamcraft.tile.TileEntitySteamPipe;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import cpw.mods.fml.common.eventhandler.Event;

public class ConnectPacketHandler implements IMessageHandler<ConnectPacket, IMessage> {
    @Override
    public IMessage onMessage(ConnectPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        ServerPacketDispatcher.INSTANCE.enqueue(player, () -> handle(message, player));
        return null;
    }

    static void handle(ConnectPacket message, EntityPlayerMP player) {
        if (message.subHit < 0 || message.subHit >= 6 || player.isSneaking()
            || !ServerPacketDispatcher.canEdit(player, message.x, message.y, message.z, message.subHit)) {
            return;
        }
        ItemStack held = player.getHeldItem();
        if (held == null || held.stackSize <= 0 || !(held.getItem() instanceof IPipeWrench)
            || !((IPipeWrench) held.getItem()).canWrench(player, message.x, message.y, message.z)) {
            return;
        }
        TileEntity tile = player.worldObj.getTileEntity(message.x, message.y, message.z);
        if (tile instanceof TileEntitySteamPipe && ((TileEntitySteamPipe) tile).getNetwork() != null) {
            PlayerInteractEvent event = ForgeEventFactory.onPlayerInteract(player,
                PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, message.x, message.y, message.z,
                message.subHit, player.worldObj);
            if (event.isCanceled() || event.useBlock == Event.Result.DENY || event.useItem == Event.Result.DENY) {
                return;
            }
            ((TileEntitySteamPipe) tile).connectDisconnect(player.worldObj, message.x, message.y, message.z, message.subHit);
            tile.markDirty();
        }
    }
}
