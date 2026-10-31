package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.api.ISteamTransporter;
import flaxbeard.steamcraft.api.tile.LoadedTileEntity;
import flaxbeard.steamcraft.misc.SteamAttachment;

public class TileEntitySteamGauge extends LoadedTileEntity {
    private int lastCompOutput = 0;

    @Override
    public void updateEntity() {
        if (worldObj != null && !worldObj.isRemote && !isInvalid() && worldObj.blockExists(xCoord, yCoord, zCoord)) {
            int compOutput = getComparatorOutput();
            if (compOutput != lastCompOutput) {
                lastCompOutput = compOutput;
                markDirty();
            }
        }
    }

    public float getPressure() {
        if (worldObj == null || isInvalid() || !worldObj.blockExists(xCoord, yCoord, zCoord)) return 0;
        ISteamTransporter source = SteamAttachment.getSource(worldObj, xCoord, yCoord, zCoord,
            worldObj.getBlockMetadata(xCoord, yCoord, zCoord));
        if (source instanceof TileEntitySteamCharger) return ((TileEntitySteamCharger) source).getSteamInItem();
        return source == null ? 0 : source.getPressure();
    }

    public int getComparatorOutput() {
        return Math.max(0, Math.min(15, (int) (15D * getPressure())));
    }
}
