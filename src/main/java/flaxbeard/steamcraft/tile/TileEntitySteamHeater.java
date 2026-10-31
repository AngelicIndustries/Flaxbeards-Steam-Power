package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.Config;
import flaxbeard.steamcraft.api.ISteamTransporter;
import flaxbeard.steamcraft.api.IWrenchable;
import flaxbeard.steamcraft.api.steamnet.SteamNetwork;
import flaxbeard.steamcraft.api.tile.SteamTransporterTileEntity;

import net.minecraft.block.BlockFurnace;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;

public class TileEntitySteamHeater extends SteamTransporterTileEntity implements ISteamTransporter, IWrenchable {


    //When multiple heaters are used on a furnace, there is a single primary heater
    public boolean isPrimaryHeater;
    private boolean isInitialized = false;
    private boolean prevHadYuck = true;
    public int steamConsumption = Config.heaterConsumption;

    public TileEntitySteamHeater() {
        super(ForgeDirection.VALID_DIRECTIONS);
        this.addSidesToGaugeBlacklist(ForgeDirection.VALID_DIRECTIONS);
    }

    public static void replace(TileEntitySteamFurnace furnace) {
        replaceFurnace(furnace, new TileEntityFurnace());
    }

    static boolean hasLoadedNeighbors(TileEntity tile) {
        World world = tile.getWorldObj();
        if (world == null || !world.blockExists(tile.xCoord, tile.yCoord, tile.zCoord)) return false;
        // Vanilla tile replacement and dirty notifications can read two blocks away.
        for (ForgeDirection direction : new ForgeDirection[]{ForgeDirection.NORTH, ForgeDirection.SOUTH,
            ForgeDirection.WEST, ForgeDirection.EAST}) {
            if (!world.blockExists(tile.xCoord + direction.offsetX * 2, tile.yCoord,
                tile.zCoord + direction.offsetZ * 2)) return false;
        }
        return true;
    }

    private static TileEntityFurnace replaceFurnace(TileEntityFurnace old, TileEntityFurnace replacement) {
        if (old == null || old.isInvalid() || old.getWorldObj() == null || old.getWorldObj().isRemote
            || !hasLoadedNeighbors(old)) return old;
        World world = old.getWorldObj();
        if (world.getTileEntity(old.xCoord, old.yCoord, old.zCoord) != old) return old;
        for (int slot = 0; slot < old.getSizeInventory(); slot++) {
            ItemStack stack = old.getStackInSlot(slot);
            replacement.setInventorySlotContents(slot, stack == null ? null : stack.copy());
        }
        replacement.furnaceBurnTime = old.furnaceBurnTime;
        replacement.currentItemBurnTime = old.currentItemBurnTime;
        replacement.furnaceCookTime = old.furnaceCookTime;
        if (old.hasCustomInventoryName()) replacement.func_145951_a(old.getInventoryName());
        replacement.setWorldObj(world);
        replacement.xCoord = old.xCoord;
        replacement.yCoord = old.yCoord;
        replacement.zCoord = old.zCoord;
        world.setTileEntity(old.xCoord, old.yCoord, old.zCoord, replacement);
        replacement.markDirty();
        world.markBlockForUpdate(old.xCoord, old.yCoord, old.zCoord);
        return replacement;
    }

    private void updateConnections() {
        ArrayList<ForgeDirection> directions = new ArrayList<>();
        ForgeDirection front = myDir();
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (direction != front) directions.add(direction);
        }
        setDistributionDirections(directions.toArray(new ForgeDirection[0]));
        isInitialized = true;
    }

    @Override
    public void readFromNBT(NBTTagCompound par1NBTTagCompound) {
        super.readFromNBT(par1NBTTagCompound);
        this.prevHadYuck = par1NBTTagCompound.getBoolean("prevHadYuck");
    }

    @Override
    public void writeToNBT(NBTTagCompound par1NBTTagCompound) {
        super.writeToNBT(par1NBTTagCompound);
        par1NBTTagCompound.setBoolean("prevHadYuck", prevHadYuck);
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || isInvalid() || !worldObj.blockExists(xCoord, yCoord, zCoord)) return;
        if (!isInitialized) updateConnections();
        super.updateEntity();
        if (worldObj.isRemote) return;
        isPrimaryHeater = false;
        ForgeDirection front = myDir();
        if (front == ForgeDirection.UNKNOWN) return;
        int fx = xCoord + front.offsetX, fy = yCoord + front.offsetY, fz = zCoord + front.offsetZ;
        TileEntity tile = SteamNetwork.getLoadedTile(worldObj, fx, fy, fz);
        if (!(tile instanceof TileEntityFurnace) || tile.isInvalid() || !hasLoadedNeighbors(tile)) return;
        TileEntityFurnace furnace = (TileEntityFurnace) tile;
        ArrayList<TileEntitySteamHeater> heaters = new ArrayList<>();
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            int x = fx + direction.offsetX, y = fy + direction.offsetY, z = fz + direction.offsetZ;
            TileEntity neighbor = SteamNetwork.getLoadedTile(worldObj, x, y, z);
            if (!(neighbor instanceof TileEntitySteamHeater) || neighbor.isInvalid()) continue;
            TileEntitySteamHeater heater = (TileEntitySteamHeater) neighbor;
            if (heater.getSteamShare() >= steamConsumption && heater.myDir() == direction.getOpposite()) {
                heaters.add(heater);
                if (heaters.size() > 4) heaters.remove(0);
            }
        }
        isPrimaryHeater = !heaters.isEmpty() && heaters.get(heaters.size() - 1) == this;
        if (!isPrimaryHeater) return;
        if (furnace.getClass() == TileEntityFurnace.class) {
            furnace = replaceFurnace(furnace, new TileEntitySteamFurnace());
        }
        if ((furnace.furnaceBurnTime == 1 || furnace.furnaceBurnTime == 0)
            && getSteamShare() >= steamConsumption && canSmelt(furnace)) {
            if (furnace.furnaceBurnTime == 0) {
                BlockFurnace.updateFurnaceBlockState(true, worldObj, fx, fy, fz);
            }
            for (TileEntitySteamHeater heater : heaters) heater.decrSteam(steamConsumption);
            furnace.furnaceBurnTime += 3;
            if (heaters.size() > 1 && furnace.furnaceCookTime > 0) {
                furnace.furnaceCookTime = Math.min(furnace.furnaceCookTime + 2 * heaters.size() - 1, 199);
            }
            furnace.markDirty();
            worldObj.markBlockForUpdate(fx, fy, fz);
        }
    }

    public boolean canSmelt(TileEntityFurnace furnace) {
        return TileEntitySteamFurnace.canSmelt(furnace);
    }

    public ForgeDirection myDir() {
        return ForgeDirection.getOrientation(this.getBlockMetadata());
    }

    @Override
    public boolean onWrench(ItemStack stack, EntityPlayer player, World world,
                            int x, int y, int z, int side, float xO, float yO, float zO) {
        if (worldObj == null || worldObj.isRemote || isInvalid() || !worldObj.blockExists(xCoord, yCoord, zCoord)) return false;
        if (getNetwork() != null) getNetwork().split(this, true);
        updateConnections();
        SteamNetwork.newOrJoin(this);
        markDirty();
        return true;
    }

}
