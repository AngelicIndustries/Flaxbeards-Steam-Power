package flaxbeard.steamcraft.misc;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.fluids.IFluidTank;

public class FluidHelper {
	
	private static Fluid water = FluidRegistry.WATER;
	
	/**
	 * If this mod is used with other mods that use different types of water (fresh water, salt water etc),
	 * this can change which fluid FSB consider water thus increasing compatibility.
	 */
	public static void changeWaterFluid(Fluid newWater) {
		water = newWater;
	}
	
	public static boolean playerIsHoldingWaterContainer(EntityPlayer player) {
		ItemStack heldItem = player.getHeldItem();
		return itemStackIsWaterContainer(heldItem);
	}

	public static boolean itemStackIsWaterContainer(ItemStack itemStack) {
        if (itemStack == null || itemStack.stackSize <= 0) return false;
		FluidStack fluid = getFluidFromItemStack(itemStack);
		
		return itemStack != null && fluid != null && fluid.getFluid() == water;
	}

	private static FluidStack getFluidFromItemStack(ItemStack itemStack) {
		if (itemStack == null) {
			return null;
		}
		
		FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(itemStack);
		
		if (fluid != null) {
			return fluid;
		}
		
		if (itemStack.getItem() instanceof IFluidContainerItem) {
			IFluidContainerItem fluidContainerItem = (IFluidContainerItem) itemStack.getItem();
			return fluidContainerItem.getFluid(itemStack);
		}
		
		return null;
	}

    public static void fillTankFromHeldItem(EntityPlayer player, IFluidTank tank) {
        if (player.worldObj.isRemote || tank == null) return;
        ItemStack held = player.getHeldItem();
        if (held == null || held.stackSize <= 0) return;
        ItemStack single = held.copy();
        single.stackSize = 1;
        ItemStack drained = fillTankFromItem(single, tank);
        if (drained == single || player.capabilities.isCreativeMode) return;
        if (held.stackSize == 1) {
            player.inventory.setInventorySlotContents(player.inventory.currentItem, drained);
        } else {
            held.stackSize--;
            if (drained != null && !player.inventory.addItemStackToInventory(drained)) {
                player.dropPlayerItemWithRandomChoice(drained, false);
            }
        }
        player.inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }

    public static ItemStack fillTankFromItem(ItemStack container, IFluidTank tank) {
        // An inventory slot cannot hold both a remaining full stack and its empty container.
        if (container == null || container.stackSize != 1 || tank == null) return container;
        if (container.getItem() instanceof IFluidContainerItem) {
            IFluidContainerItem item = (IFluidContainerItem) container.getItem();
            ItemStack result = container.copy();
            FluidStack available = item.drain(result, Math.max(0, tank.getCapacity() - tank.getFluidAmount()), false);
            int accepted = available == null ? 0 : tank.fill(available, false);
            if (accepted <= 0) return container;
            FluidStack drained = item.drain(result, accepted, true);
            if (drained == null || drained.amount <= 0) return container;
            tank.fill(drained, true);
            return result;
        }
        FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(container);
        if (fluid == null || fluid.amount <= 0 || tank.fill(fluid, false) != fluid.amount) return container;
        // Fixed-volume containers must fit completely before either side changes.
        ItemStack empty = FluidContainerRegistry.drainFluidContainer(container);
        tank.fill(fluid, true);
        return empty;
    }
}
