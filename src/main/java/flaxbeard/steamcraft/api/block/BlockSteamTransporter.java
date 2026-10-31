package flaxbeard.steamcraft.api.block;

import flaxbeard.steamcraft.api.ISteamTransporter;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

public abstract class BlockSteamTransporter extends BlockContainer {
    public BlockSteamTransporter(Material material) {
        super(material);
    }

    public void onBlockPreDestroy(World world, int x, int y, int z, int meta) {
        if (!world.isRemote && world.getTileEntity(x, y, z) instanceof ISteamTransporter) {
            ISteamTransporter te = (ISteamTransporter) world.getTileEntity(x, y, z);
            if (te.getNetwork() != null) {
                te.getNetwork().split(te, true);
            }
            // A broken block vents its share; it must not rejoin before World removes the tile.
            te.updateSteam(0);
        }
    }
}
