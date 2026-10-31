package flaxbeard.steamcraft.tile;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import flaxbeard.steamcraft.api.ISteamTransporter;
import flaxbeard.steamcraft.api.IWrenchable;
import flaxbeard.steamcraft.api.block.IDisguisableBlock;
import flaxbeard.steamcraft.api.tile.SteamTransporterTileEntity;
import flaxbeard.steamcraft.client.render.BlockSteamPipeRenderer;
import flaxbeard.steamcraft.misc.FluidHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;

public class TileEntityBoiler extends SteamTransporterTileEntity implements IFluidHandler, ISidedInventory, ISteamTransporter, IWrenchable, IDisguisableBlock {
    private static final int[] slotsTop = new int[]{0, 1};
    private static final int[] slotsBottom = new int[]{0, 1};
    private static final int[] slotsSides = new int[]{0, 1};
    public FluidTank myTank = createWaterTank(10000);
    public int furnaceCookTime;
    public int furnaceBurnTime;
    public int currentItemBurnTime;
    public Block disguiseBlock = null;
    public int disguiseMeta = 0;
    private ItemStack[] furnaceItemStacks = new ItemStack[2];
    private String field_145958_o;
    private boolean wasBurning;
    private boolean lastWrench = false;

    public TileEntityBoiler(int capacity) {
        super(capacity, new ForgeDirection[]{ForgeDirection.UP});
        this.addSideToGaugeBlacklist(ForgeDirection.UP);
        this.setPressureResistance(0.5F);
    }

    public TileEntityBoiler() {
        this(50000);
    }

    public static int getItemBurnTime(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return 0;
        } else {
            Item item = stack.getItem();

            if (item instanceof ItemBlock && Block.getBlockFromItem(item) != Blocks.air) {
                Block block = Block.getBlockFromItem(item);

                if (block == Blocks.wooden_slab) {
                    return 150;
                }

                if (block.getMaterial() == Material.wood) {
                    return 300;
                }

                if (block == Blocks.coal_block) {
                    return 16000;
                }
            }

            if (item instanceof ItemTool && ((ItemTool) item).getToolMaterialName().equals("WOOD")) return 200;
            if (item instanceof ItemSword && ((ItemSword) item).getToolMaterialName().equals("WOOD")) return 200;
            if (item instanceof ItemHoe && ((ItemHoe) item).getToolMaterialName().equals("WOOD")) return 200;
            if (item == Items.stick) return 100;
            if (item == Items.coal) return 1600;
            if (item == Items.lava_bucket) return 20000;
            if (item == Item.getItemFromBlock(Blocks.sapling)) return 100;
            if (item == Items.blaze_rod) return 2400;
            return GameRegistry.getFuelValue(stack);
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound access = super.getDescriptionTag();
        access.setInteger("water", myTank.getFluidAmount());
        access.setInteger("BurnTime", this.furnaceBurnTime);
        access.setInteger("CookTime", this.furnaceCookTime);
        access.setInteger("cIBT", this.currentItemBurnTime);
        access.setInteger("disguiseBlock", Block.getIdFromBlock(disguiseBlock));
        access.setInteger("disguiseMeta", disguiseMeta);

        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, access);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        super.onDataPacket(net, pkt);
        NBTTagCompound access = pkt.func_148857_g();
        this.myTank.setFluid(new FluidStack(FluidRegistry.WATER, access.getInteger("water")));
        this.furnaceBurnTime = access.getInteger("BurnTime");
        this.currentItemBurnTime = access.getInteger("cIBT");
        this.furnaceCookTime = access.getInteger("CookTime");
        this.disguiseBlock = Block.getBlockById(access.getInteger("disguiseBlock"));
        this.disguiseMeta = access.getInteger("disguiseMeta");
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void readFromNBT(NBTTagCompound par1NBTTagCompound) {
        super.readFromNBT(par1NBTTagCompound);
        NBTTagList nbttaglist = par1NBTTagCompound.getTagList("Items", 10);
        this.furnaceItemStacks = new ItemStack[2];

        for (int i = 0; i < nbttaglist.tagCount(); ++i) {
            NBTTagCompound nbttagcompound1 = nbttaglist.getCompoundTagAt(i);
            byte b0 = nbttagcompound1.getByte("Slot");

            if (b0 >= 0 && b0 < this.furnaceItemStacks.length) {
                this.furnaceItemStacks[b0] = ItemStack.loadItemStackFromNBT(nbttagcompound1);
            }
        }

        this.furnaceBurnTime = par1NBTTagCompound.getInteger("BurnTime");
        this.furnaceCookTime = par1NBTTagCompound.getInteger("CookTime");
        this.currentItemBurnTime = par1NBTTagCompound.getInteger("cIBT");

        if (par1NBTTagCompound.hasKey("CustomName")) {
            this.field_145958_o = par1NBTTagCompound.getString("CustomName");
        }

        if (par1NBTTagCompound.hasKey("water")) {
            this.myTank.setFluid(new FluidStack(FluidRegistry.WATER, par1NBTTagCompound.getInteger("water")));
        }
        this.disguiseBlock = Block.getBlockById(par1NBTTagCompound.getInteger("disguiseBlock"));
        this.disguiseMeta = par1NBTTagCompound.getInteger("disguiseMeta");
    }

    @Override
    public void writeToNBT(NBTTagCompound par1NBTTagCompound) {
        super.writeToNBT(par1NBTTagCompound);
        par1NBTTagCompound.setInteger("BurnTime", this.furnaceBurnTime);
        par1NBTTagCompound.setInteger("water", myTank.getFluidAmount());
        par1NBTTagCompound.setInteger("CookTime", this.furnaceCookTime);
        par1NBTTagCompound.setInteger("cIBT", this.currentItemBurnTime);
        par1NBTTagCompound.setInteger("disguiseBlock", Block.getIdFromBlock(disguiseBlock));
        par1NBTTagCompound.setInteger("disguiseMeta", disguiseMeta);

        NBTTagList nbttaglist = new NBTTagList();

        for (int i = 0; i < this.furnaceItemStacks.length; ++i) {
            if (this.furnaceItemStacks[i] != null) {
                NBTTagCompound nbttagcompound1 = new NBTTagCompound();
                nbttagcompound1.setByte("Slot", (byte) i);
                this.furnaceItemStacks[i].writeToNBT(nbttagcompound1);
                nbttaglist.appendTag(nbttagcompound1);
            }
        }

        par1NBTTagCompound.setTag("Items", nbttaglist);

        if (this.hasCustomInventoryName()) {
            par1NBTTagCompound.setString("CustomName", this.field_145958_o);
        }
    }

    public void superUpdateOnly() {
        super.updateEntity();
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (worldObj.isRemote) {
            boolean hasWrench = BlockSteamPipeRenderer.updateWrenchStatus();
            if (hasWrench != lastWrench && !(disguiseBlock == null || disguiseBlock == Blocks.air)) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
            lastWrench = hasWrench;
            return;
        }

        fillFromInventoryContainer();
        int previousBurn = furnaceBurnTime;
        int previousCook = furnaceCookTime;
        if (furnaceBurnTime > 0) furnaceBurnTime--;
        if (furnaceBurnTime == 0 && canSmelt() && getNetwork() != null) {
            currentItemBurnTime = furnaceBurnTime = getItemBurnTime(furnaceItemStacks[0]);
            if (furnaceBurnTime > 0) {
                if (--furnaceItemStacks[0].stackSize == 0) {
                    furnaceItemStacks[0] = furnaceItemStacks[0].getItem().getContainerItem(furnaceItemStacks[0]);
                }
                markDirty();
            }
        }
        if (isBurning() && canSmelt() && getNetwork() != null) {
            getNetwork().addSteam(10);
            myTank.drain(2, true);
        }
        furnaceCookTime = 0;
        if (previousBurn != furnaceBurnTime || previousCook != furnaceCookTime) markDirty();
        if (wasBurning != isBurning()) {
            wasBurning = isBurning();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    protected void fillFromInventoryContainer() {
        ItemStack input = getStackInSlot(1);
        if (FluidHelper.itemStackIsWaterContainer(input)) {
            ItemStack drained = FluidHelper.fillTankFromItem(input, myTank);
            if (drained != input) setInventorySlotContents(1, drained);
        }
    }

    protected FluidTank createWaterTank(int capacity) {
        return new FluidTank(new FluidStack(FluidRegistry.WATER, 1), capacity) {
            @Override public int fill(FluidStack resource, boolean doFill) {
                if (worldObj == null || worldObj.isRemote || isInvalid() || resource == null
                    || resource.amount <= 0 || resource.getFluid() != FluidRegistry.WATER) return 0;
                int filled = super.fill(resource, doFill);
                if (doFill && filled > 0) markDirty();
                return filled;
            }
            @Override public FluidStack drain(int amount, boolean doDrain) {
                if (worldObj == null || worldObj.isRemote || isInvalid() || amount <= 0) return null;
                FluidStack drained = super.drain(amount, doDrain);
                if (doDrain && drained != null && drained.amount > 0) markDirty();
                return drained;
            }
        };
    }

    private boolean canSmelt() {
        return myTank.getFluidAmount() > 9;
    }

    public boolean isBurning() {
        return this.furnaceBurnTime > 0;
    }

    private boolean canDrainItem(ItemStack stack) {
        return stack.stackSize == 1;
    }

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        return myTank.fill(resource, doFill);
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        return null;
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return fluid == FluidRegistry.WATER;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return false;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        return new FluidTankInfo[]{new FluidTankInfo(myTank)};
    }

    @Override
    public int getSizeInventory() {
        return this.furnaceItemStacks.length;
    }

    @Override
    public ItemStack getStackInSlot(int par1) {
        return this.furnaceItemStacks[par1];
    }

    @Override
    public ItemStack decrStackSize(int par1, int par2) {
        if (this.furnaceItemStacks[par1] != null) {
            ItemStack itemstack;

            if (this.furnaceItemStacks[par1].stackSize <= par2) {
                itemstack = this.furnaceItemStacks[par1];
                this.furnaceItemStacks[par1] = null;
                markDirty();
                return itemstack;
            } else {
                itemstack = this.furnaceItemStacks[par1].splitStack(par2);
                markDirty();

                if (this.furnaceItemStacks[par1].stackSize == 0) {
                    this.furnaceItemStacks[par1] = null;
                }

                return itemstack;
            }
        } else {
            return null;
        }
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int par1) {
        if (this.furnaceItemStacks[par1] != null) {
            ItemStack itemstack = this.furnaceItemStacks[par1];
            this.furnaceItemStacks[par1] = null;
            markDirty();
            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public void setInventorySlotContents(int par1, ItemStack par2ItemStack) {
        this.furnaceItemStacks[par1] = par2ItemStack;
        markDirty();

        if (par2ItemStack != null && par2ItemStack.stackSize > this.getInventoryStackLimit()) {
            par2ItemStack.stackSize = this.getInventoryStackLimit();
        }
    }

    @Override
    public String getInventoryName() {
        return this.hasCustomInventoryName() ? this.field_145958_o : "container.furnace";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return this.field_145958_o != null && this.field_145958_o.length() > 0;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer var1) {
        return this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) != this ? false : var1.getDistanceSq((double) this.xCoord + 0.5D, (double) this.yCoord + 0.5D, (double) this.zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public void openInventory() {
    }

    public int getPressureAsInt() {
        return (int) Math.floor((double) this.getPressure() * 1000);
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
    	return slot == 0 ? getItemBurnTime(stack) > 0 : FluidHelper.itemStackIsWaterContainer(stack);
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int par1) {
        return par1 == 0 ? slotsBottom : (par1 == 1 ? slotsTop : slotsSides);
    }

    @Override
    public boolean canInsertItem(int par1, ItemStack par2ItemStack, int par3) {
        return this.isItemValidForSlot(par1, par2ItemStack);
    }

    @Override
    public boolean canExtractItem(int par1, ItemStack par2ItemStack, int par3) {
        return par2ItemStack.getItem() == Items.bucket;
    }

    @SideOnly(Side.CLIENT)
    public int getCookProgressScaled(int p_145953_1_) {
        return this.furnaceCookTime * p_145953_1_ / 200;
    }

    @SideOnly(Side.CLIENT)
    public int getBurnTimeRemainingScaled(int p_145955_1_) {
        if (currentItemBurnTime <= 0) return 0;
        return (int) Math.max(0, Math.min(p_145955_1_, (long) furnaceBurnTime * p_145955_1_ / currentItemBurnTime));
    }

    public FluidTank getTank() {
        return myTank;
    }

    @Override
    public boolean onWrench(ItemStack stack, EntityPlayer player, World world,
                            int x, int y, int z, int side, float xO, float yO, float zO) {
        if (player.isSneaking()) {
            if (this.disguiseBlock != null) {
                if (!player.capabilities.isCreativeMode) {
                    EntityItem entityItem = new EntityItem(world, player.posX, player.posY, player.posZ, new ItemStack(disguiseBlock, 1, disguiseMeta));
                    world.spawnEntityInWorld(entityItem);
                }
                world.playSoundEffect((double) ((float) x + 0.5F), (double) ((float) y + 0.5F), (double) ((float) z + 0.5F), disguiseBlock.stepSound.getBreakSound(), (disguiseBlock.stepSound.getVolume() + 1.0F) / 2.0F, disguiseBlock.stepSound.getPitch() * 0.8F);
                disguiseBlock = null;

                this.worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                return true;
            }
        } else {
            return true;
        }
        return false;
    }

    @Override
    public Block getDisguiseBlock() {
        return this.disguiseBlock;
    }

    @Override
    public void setDisguiseBlock(Block block) {
        this.disguiseBlock = block;
    }

    @Override
    public int getDisguiseMeta() {
        return this.disguiseMeta;
    }

    @Override
    public void setDisguiseMeta(int meta) {
        this.disguiseMeta = meta;
    }
}
