package flaxbeard.steamcraft.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Permission check for camouflage, rather than ordinary block placement. */
public final class CamouflageInteractionEvent extends PlayerInteractEvent {
    public CamouflageInteractionEvent(EntityPlayer player, int x, int y, int z) {
        super(player, Action.RIGHT_CLICK_BLOCK, x, y, z, 0, player.worldObj);
    }
}
