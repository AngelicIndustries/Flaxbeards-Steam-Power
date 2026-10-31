package flaxbeard.steamcraft.misc;

import flaxbeard.steamcraft.api.ISteamTransporter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** Resolves the supporting machine without loading a chunk. */
public final class SteamAttachment {
    private SteamAttachment() {}

    public static boolean isHorizontal(int side) {
        return side >= 2 && side <= 5;
    }

    public static boolean isSupportLoaded(World world, int x, int y, int z, int side) {
        if (!isHorizontal(side)) return true;
        ForgeDirection direction = ForgeDirection.getOrientation(side);
        return world.blockExists(x - direction.offsetX, y, z - direction.offsetZ);
    }

    public static ISteamTransporter getSource(IBlockAccess world, int x, int y, int z, int side) {
        if (world == null || !isHorizontal(side)) return null;
        if (world instanceof World && !isSupportLoaded((World) world, x, y, z, side)) return null;
        ForgeDirection direction = ForgeDirection.getOrientation(side);
        TileEntity tile = world.getTileEntity(x - direction.offsetX, y, z - direction.offsetZ);
        if (!(tile instanceof ISteamTransporter) || tile.isInvalid()) return null;
        ISteamTransporter source = (ISteamTransporter) tile;
        return source.acceptsGauge(direction) ? source : null;
    }
}
