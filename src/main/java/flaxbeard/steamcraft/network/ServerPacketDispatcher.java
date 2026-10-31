package flaxbeard.steamcraft.network;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Forge 1.7.10 invokes SimpleNetworkWrapper handlers on the network thread. */
public final class ServerPacketDispatcher {
    public static final ServerPacketDispatcher INSTANCE = new ServerPacketDispatcher();
    private static final int MAX_PENDING_PER_PLAYER = 32;
    private final Map<EntityPlayerMP, List<Runnable>> pending = new LinkedHashMap<>();

    private ServerPacketDispatcher() {}

    public synchronized void enqueue(EntityPlayerMP player, Runnable action) {
        List<Runnable> actions = pending.get(player);
        if (actions == null) {
            actions = new ArrayList<>();
            pending.put(player, actions);
        }
        if (actions.size() < MAX_PENDING_PER_PLAYER) {
            int dimension = player.dimension;
            actions.add(() -> {
                World world = player.worldObj;
                if (!player.isDead && player.dimension == dimension && world != null
                    && !world.isRemote && world.playerEntities.contains(player)) {
                    action.run();
                }
            });
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Map<EntityPlayerMP, List<Runnable>> batch;
        synchronized (this) {
            batch = new LinkedHashMap<>(pending);
            pending.clear();
        }
        for (List<Runnable> actions : batch.values()) {
            for (Runnable action : actions) {
                action.run();
            }
        }
    }

    public synchronized void clear() {
        pending.clear();
    }

    static boolean canAccess(EntityPlayerMP player, int x, int y, int z, double reach) {
        World world = player.worldObj;
        return world != null && !world.isRemote && !player.isDead && y >= 0 && y < 256
            && player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) <= reach * reach
            && world.blockExists(x, y, z);
    }

    static boolean canEdit(EntityPlayerMP player, int x, int y, int z, int side) {
        return canAccess(player, x, y, z, player.theItemInWorldManager.getBlockReachDistance() + 1.0D)
            && player.worldObj.canMineBlock(player, x, y, z)
            && player.canPlayerEdit(x, y, z, side, player.getHeldItem());
    }
}
