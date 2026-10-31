package flaxbeard.steamcraft.api.tile;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/** Keeps save notifications from loading neighboring chunks. */
public class LoadedTileEntity extends TileEntity {
    @Override
    public void markDirty() {
        if (worldObj == null || !worldObj.blockExists(xCoord, yCoord, zCoord)) return;
        blockMetadata = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
        worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
        if (getBlockType() == Blocks.air) return;
        // Vanilla dirty notifications read neighboring blocks and can reload a detached chunk.
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            int x = xCoord + side.offsetX, y = yCoord + side.offsetY, z = zCoord + side.offsetZ;
            if (!worldObj.blockExists(x, y, z)) continue;
            Block neighbor = worldObj.getBlock(x, y, z);
            neighbor.onNeighborChange(worldObj, x, y, z, xCoord, yCoord, zCoord);
            if (neighbor.isNormalCube(worldObj, x, y, z)) {
                x += side.offsetX;
                y += side.offsetY;
                z += side.offsetZ;
                if (!worldObj.blockExists(x, y, z)) continue;
                Block indirect = worldObj.getBlock(x, y, z);
                if (indirect.getWeakChanges(worldObj, x, y, z)) {
                    indirect.onNeighborChange(worldObj, x, y, z, xCoord, yCoord, zCoord);
                }
            }
        }
    }

}
