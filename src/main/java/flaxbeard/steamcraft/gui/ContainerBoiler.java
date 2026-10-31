package flaxbeard.steamcraft.gui;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import flaxbeard.steamcraft.tile.TileEntityBoiler;
import flaxbeard.steamcraft.tile.TileEntityFlashBoiler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

public class ContainerBoiler extends Container {
    private static final int PLAYER_START = 2;
    private static final int HOTBAR_START = 29;
    private static final int PLAYER_END = 38;
    private static final int PROPERTY_COUNT = 5;
    private final TileEntityBoiler accessTile;
    private final TileEntityBoiler furnace;
    private final int[] lastValues = new int[PROPERTY_COUNT];
    private final int[] receivedLow = new int[PROPERTY_COUNT];

    public ContainerBoiler(InventoryPlayer inventory, TileEntityBoiler boiler) {
        accessTile = boiler;
        furnace = resolveBoiler(boiler);
        if (furnace == null) throw new IllegalArgumentException("Boiler has no available primary");
        addSlotToContainer(new Slot(furnace, 1, 58, 55) {
            @Override public boolean isItemValid(ItemStack stack) { return furnace.isItemValidForSlot(1, stack); }
            @Override public int getSlotStackLimit() { return 1; }
        });
        addSlotToContainer(new Slot(furnace, 0, 58, 32) {
            @Override public boolean isItemValid(ItemStack stack) { return furnace.isItemValidForSlot(0, stack); }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlotToContainer(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlotToContainer(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    public static TileEntityBoiler resolveBoiler(TileEntityBoiler boiler) {
        if (boiler == null || boiler.isInvalid()) return null;
        return boiler instanceof TileEntityFlashBoiler ? ((TileEntityFlashBoiler) boiler).getPrimaryTileEntity() : boiler;
    }

    public TileEntityBoiler getBoiler() { return furnace; }

    private boolean isStructureValid() {
        World world = accessTile.getWorldObj();
        return world != null && !accessTile.isInvalid() && !furnace.isInvalid()
            && world.blockExists(accessTile.xCoord, accessTile.yCoord, accessTile.zCoord)
            && world.getTileEntity(accessTile.xCoord, accessTile.yCoord, accessTile.zCoord) == accessTile
            && world.blockExists(furnace.xCoord, furnace.yCoord, furnace.zCoord)
            && world.getTileEntity(furnace.xCoord, furnace.yCoord, furnace.zCoord) == furnace
            && resolveBoiler(accessTile) == furnace;
    }

    private int[] values() {
        return new int[]{furnace.furnaceCookTime, furnace.furnaceBurnTime, furnace.currentItemBurnTime,
            furnace.getPressureAsInt(), furnace.getTank().getFluidAmount()};
    }

    private void sendValue(ICrafting listener, int id, int value) {
        // Vanilla window properties carry signed shorts. Send both halves, low first.
        listener.sendProgressBarUpdate(this, id, value & 0xffff);
        listener.sendProgressBarUpdate(this, id + PROPERTY_COUNT, value >>> 16);
    }

    @Override
    public void addCraftingToCrafters(ICrafting listener) {
        super.addCraftingToCrafters(listener);
        if (!isStructureValid()) return;
        int[] values = values();
        for (int id = 0; id < PROPERTY_COUNT; id++) sendValue(listener, id, values[id]);
    }

    @Override
    public void detectAndSendChanges() {
        if (!isStructureValid()) return;
        super.detectAndSendChanges();
        int[] values = values();
        for (Object crafter : crafters) {
            for (int id = 0; id < PROPERTY_COUNT; id++) {
                if (values[id] != lastValues[id]) sendValue((ICrafting) crafter, id, values[id]);
            }
        }
        System.arraycopy(values, 0, lastValues, 0, PROPERTY_COUNT);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void updateProgressBar(int id, int value) {
        if (id < 0 || id >= PROPERTY_COUNT * 2) return;
        if (id < PROPERTY_COUNT) {
            receivedLow[id] = value & 0xffff;
            return;
        }
        id -= PROPERTY_COUNT;
        int combined = receivedLow[id] | ((value & 0xffff) << 16);
        switch (id) {
            case 0: furnace.furnaceCookTime = combined; break;
            case 1: furnace.furnaceBurnTime = combined; break;
            case 2: furnace.currentItemBurnTime = combined; break;
            case 3: furnace.pressure = combined / 1000F; break;
            case 4:
                if (furnace.getTank() != null) {
                    furnace.getTank().setFluid(new FluidStack(FluidRegistry.WATER, Math.max(0, combined)));
                }
                break;
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return isStructureValid() && accessTile.isUseableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index < 0 || index >= PLAYER_END || !canInteractWith(player)) return null;
        Slot slot = getSlot(index);
        if (!slot.getHasStack()) return null;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (index < PLAYER_START) {
            if (!mergeItemStack(stack, PLAYER_START, PLAYER_END, true)) return null;
        } else if (getSlot(0).isItemValid(stack)) {
            // Vanilla mergeItemStack ignores custom slot limits for an empty slot.
            if (getSlot(0).getHasStack()) return null;
            getSlot(0).putStack(stack.splitStack(1));
        } else if (getSlot(1).isItemValid(stack)) {
            if (!mergeItemStack(stack, 1, 2, false)) return null;
        } else if (index < HOTBAR_START) {
            if (!mergeItemStack(stack, HOTBAR_START, PLAYER_END, false)) return null;
        } else if (!mergeItemStack(stack, PLAYER_START, HOTBAR_START, false)) {
            return null;
        }
        if (stack.stackSize == original.stackSize) return null;
        if (stack.stackSize == 0) slot.putStack(null);
        else slot.onSlotChanged();
        slot.onPickupFromSlot(player, stack);
        return original;
    }
}
