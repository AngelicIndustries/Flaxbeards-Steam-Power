package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.api.SteamcraftRegistry;
import net.minecraft.block.BlockFurnace;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntity;
import flaxbeard.steamcraft.api.steamnet.SteamNetwork;
import net.minecraftforge.common.util.ForgeDirection;
import org.apache.commons.lang3.tuple.MutablePair;

public class TileEntitySteamFurnace extends TileEntityFurnace {
    //private ItemStack[] furnaceItemStacks = new ItemStack[3];

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || isInvalid() || !TileEntitySteamHeater.hasLoadedNeighbors(this)) return;
        if (worldObj.getTileEntity(xCoord, yCoord, zCoord) != this) return;
        int numHeaters = 0;
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity neighbor = SteamNetwork.getLoadedTile(worldObj, xCoord + direction.offsetX,
                yCoord + direction.offsetY, zCoord + direction.offsetZ);
            if (neighbor instanceof TileEntitySteamHeater && !neighbor.isInvalid()) {
                TileEntitySteamHeater heater = (TileEntitySteamHeater) neighbor;
                if (heater.getSteamShare() > 2 && heater.myDir() == direction.getOpposite()) numHeaters++;
            }
        }
        if (numHeaters == 0) {
            TileEntitySteamHeater.replace(this);
            return;
        }

        int previousBurn = furnaceBurnTime;
        int previousCook = furnaceCookTime;
        boolean flag = this.furnaceBurnTime > 0;
        boolean flag1 = false;

        if (this.furnaceBurnTime > 0) {
            --this.furnaceBurnTime;
        }

        if (!this.worldObj.isRemote) {
            if (this.furnaceBurnTime == 0 && this.canSmelt()) {
                this.currentItemBurnTime = this.furnaceBurnTime = getItemBurnTime(this.getStackInSlot(1));

                if (this.furnaceBurnTime > 0) {
                    flag1 = true;

                    if (this.getStackInSlot(1) != null) {
                        ItemStack copy = this.getStackInSlot(1).copy();
                        copy.stackSize--;
                        this.setInventorySlotContents(1, copy);

                        if (this.getStackInSlot(1).stackSize == 0) {
                            setInventorySlotContents(1, getStackInSlot(1).getItem().getContainerItem(getStackInSlot(1)));
                        }
                    }
                }
            }

            if (this.isBurning() && this.canSmelt()) {
                ++this.furnaceCookTime;

                if (this.furnaceCookTime == 200) {
                    this.furnaceCookTime = 0;
                    this.smeltItem();
                    flag1 = true;
                }
            } else {
                this.furnaceCookTime = 0;
            }

            if (flag != this.furnaceBurnTime > 0) {
                flag1 = true;
                BlockFurnace.updateFurnaceBlockState(this.furnaceBurnTime > 0, this.worldObj, this.xCoord, this.yCoord, this.zCoord);
            }
        }

        if (flag1 || previousBurn != furnaceBurnTime || previousCook != furnaceCookTime) {
            this.markDirty();
        }
    }

    static ItemStack getSteamingResult(ItemStack input) {
        if (input == null || input.getItem() == null || input.stackSize <= 0) return null;
        ItemStack cooked = FurnaceRecipes.smelting().getSmeltingResult(input);
        if (cooked == null || cooked.getItem() == null || cooked.stackSize <= 0) return null;
        MutablePair<Item, Integer> conversion = SteamcraftRegistry.steamingRecipes.get(
            MutablePair.of(cooked.getItem(), cooked.getItemDamage()));
        if (conversion == null) conversion = SteamcraftRegistry.steamingRecipes.get(MutablePair.of(cooked.getItem(), -1));
        if (conversion == null) return cooked.copy();
        if (conversion.left == null) return null;
        return new ItemStack(conversion.left, 1, conversion.right == -1 ? 0 : conversion.right);
    }

    static boolean canSmelt(TileEntityFurnace furnace) {
        if (furnace == null) return false;
        ItemStack result = getSteamingResult(furnace.getStackInSlot(0));
        if (result == null) return false;
        ItemStack output = furnace.getStackInSlot(2);
        int limit = Math.min(furnace.getInventoryStackLimit(), result.getMaxStackSize());
        if (output == null || output.stackSize <= 0) return result.stackSize <= limit;
        return output.isItemEqual(result) && ItemStack.areItemStackTagsEqual(output, result)
            && result.stackSize <= limit - output.stackSize;
    }

    public boolean canSmelt() {
        return canSmelt(this);
    }

    @Override
    public void smeltItem() {
        if (worldObj == null || worldObj.isRemote || isInvalid() || !worldObj.blockExists(xCoord, yCoord, zCoord)
            || worldObj.getTileEntity(xCoord, yCoord, zCoord) != this || !canSmelt()) return;
        ItemStack result = getSteamingResult(getStackInSlot(0));
        ItemStack output = getStackInSlot(2);
        if (output == null || output.stackSize <= 0) {
            setInventorySlotContents(2, result);
        } else {
            ItemStack combined = output.copy();
            combined.stackSize += result.stackSize;
            setInventorySlotContents(2, combined);
        }
        ItemStack input = getStackInSlot(0).copy();
        input.stackSize--;
        setInventorySlotContents(0, input.stackSize > 0 ? input : null);
        markDirty();
    }
}
