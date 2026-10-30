package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.api.CrucibleFormula;
import flaxbeard.steamcraft.api.CrucibleLiquid;
import flaxbeard.steamcraft.api.ICrucibleMold;
import flaxbeard.steamcraft.api.SteamcraftRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.ArrayList;
import java.util.HashMap;

public class TileEntityCrucible extends TileEntity {
    public ArrayList<CrucibleLiquid> contents = new ArrayList<CrucibleLiquid>();
    public HashMap<CrucibleLiquid, Integer> number = new HashMap<CrucibleLiquid, Integer>();
    public boolean hasUpdated = true;
    public boolean needsUpdate = false;
    public int tipTicks = 0;
    private int targetFill = -1;
    private boolean tipping;
    private ForgeDirection[] dirs = {ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.NORTH, ForgeDirection.EAST};
    public boolean isPowered;
    private int lastComparatorOutput = 0;

    public TileEntityCrucible() {
        //contents.add(Steamcraft.liquidCopper);
        //number.put(Steamcraft.liquidCopper, 90);
        //contents.add(Steamcraft.liquidGold);
        //number.put(Steamcraft.liquidGold, 27);
        isPowered = false;
    }

    @Override
    public void readFromNBT(NBTTagCompound par1NBTTagCompound) {
        super.readFromNBT(par1NBTTagCompound);
        readLiquids(par1NBTTagCompound);
        targetFill = getFill();
        hasUpdated = true;
    }

    private void readLiquids(NBTTagCompound nbt) {
        contents.clear();
        number.clear();
        NBTTagList liquids = nbt.getTagList("liquids", 10);
        for (int i = 0; i < liquids.tagCount(); i++) {
            NBTTagCompound entry = liquids.getCompoundTagAt(i);
            CrucibleLiquid liquid = SteamcraftRegistry.getLiquidFromName(entry.getString("name"));
            int amount = entry.getShort("amount");
            if (liquid != null && amount > 0) {
                if (!contents.contains(liquid)) {
                    contents.add(liquid);
                }
                number.put(liquid, amount);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound par1NBTTagCompound) {
        super.writeToNBT(par1NBTTagCompound);
        NBTTagList nbttaglist = new NBTTagList();

        for (CrucibleLiquid liquid : this.contents) {
            NBTTagCompound nbttagcompound1 = new NBTTagCompound();
            nbttagcompound1.setShort("amount", (short) (int) this.number.get(liquid));
            nbttagcompound1.setString("name", liquid.name);
            nbttaglist.appendTag(nbttagcompound1);
        }

        par1NBTTagCompound.setTag("liquids", nbttaglist);
    }

    @Override
    public Packet getDescriptionPacket() {
        super.getDescriptionPacket();
        NBTTagCompound access = new NBTTagCompound();
        NBTTagList nbttaglist = new NBTTagList();
        access.setInteger("tipTicks", this.tipTicks);
        access.setBoolean("tipping", tipping);

        for (CrucibleLiquid liquid : this.contents) {
            if (liquid != null) {
                NBTTagCompound nbttagcompound1 = new NBTTagCompound();
                nbttagcompound1.setShort("amount", (short) (int) this.number.get(liquid));
                nbttagcompound1.setString("name", liquid.name);
                nbttaglist.appendTag(nbttagcompound1);
            }
        }

        access.setTag("liquids", nbttaglist);

        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, access);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        super.onDataPacket(net, pkt);
        NBTTagCompound access = pkt.func_148857_g();
        readLiquids(access);
        if (this.tipTicks == 0) {
            this.tipTicks = access.getInteger("tipTicks");
        }
        this.tipping = access.getBoolean("tipping");
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void updateEntity() {
        if (targetFill < 0) {
            this.targetFill = this.getFill();
        }
        if (this.getFill() == targetFill) {
            hasUpdated = true;
        }
        int meta = this.worldObj.getBlockMetadata(this.xCoord, this.yCoord, this.zCoord);

        if (worldObj.isBlockIndirectlyGettingPowered(this.xCoord, this.yCoord, this.zCoord)) {
            isPowered = true;
        }
        if (this.tipping || isPowered) {
            this.tipTicks++;
            if (this.tipTicks == 45 && !this.worldObj.isRemote) {

                int posX = this.xCoord + dirs[meta].offsetX;
                int posZ = this.zCoord + dirs[meta].offsetZ;
                if (this.worldObj.getTileEntity(posX, this.yCoord, posZ) != null) {
                    if (this.worldObj.getTileEntity(posX, this.yCoord, posZ) instanceof TileEntityMold) {
                        TileEntityMold mold = (TileEntityMold) this.worldObj.getTileEntity(posX, this.yCoord, posZ);
                        if (mold.canPour() && this.contents.size() > 0) {
                            ICrucibleMold crucibleMold = (ICrucibleMold) mold.mold[0].getItem();
                            CrucibleLiquid liquid = this.getNextLiquid(crucibleMold);
                            if (liquid != null) {
                                if (!worldObj.isRemote) {
                                    mold.pour(liquid);
                                }
                                int currNum = number.get(liquid);
                                currNum -= crucibleMold.getCostToMold(liquid);
                                if (currNum == 0) {
                                    contents.remove(liquid);
                                }
                                number.remove(liquid);
                                if (currNum > 0) {
                                    number.put(liquid, currNum);
                                }
                                needsUpdate = true;

                            }
                        }
                    }
                }
            }
            if (this.tipTicks > 140) {
                this.tipTicks = 0;
                this.tipping = false;
                isPowered = false;
            }
        }
        if (!worldObj.isRemote) {
            for (CrucibleLiquid liquid : SteamcraftRegistry.liquids) {
                CrucibleFormula recipe = liquid.recipe;
                if (recipe != null && recipe.matches(contents, number, recipe)) {
                    consumeLiquid(recipe.liquid1, recipe.liquid1num);
                    consumeLiquid(recipe.liquid2, recipe.liquid2num);
                    if (!contents.contains(liquid)) {
                        contents.add(liquid);
                        number.put(liquid, 0);
                    }
                    number.put(liquid, number.get(liquid) + recipe.output);
                    needsUpdate = true;
                }
            }
        }
        if (this.getComparatorOutput() != this.lastComparatorOutput){
            this.lastComparatorOutput = this.getComparatorOutput();
            this.markDirty();
        }
        if (needsUpdate && !worldObj.isRemote) {
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            needsUpdate = false;
        }

    }

    private void consumeLiquid(CrucibleLiquid liquid, int amount) {
        int remaining = number.get(liquid) - amount;
        if (remaining == 0) {
            contents.remove(liquid);
            number.remove(liquid);
        } else {
            number.put(liquid, remaining);
        }
    }

    public int getFill() {
        int fill = 0;
        for (CrucibleLiquid liquid : contents) {
            fill += number.get(liquid);
        }
        ////Steamcraft.log.debug("fill: " + fill);
        return fill;

    }

    public ItemStack fillWith(ItemStack stack, int amount, MutablePair output) {
        int fill = this.getFill();
        if (!worldObj.isRemote) {
            if (fill + amount <= 90 && this.hasUpdated) {
                ////Steamcraft.log.debug(fill + " " +this.hasUpdated);
                CrucibleLiquid fluid = (CrucibleLiquid) output.left;
                if (!this.contents.contains(fluid)) {
                    this.contents.add(fluid);
                    this.number.put(fluid, 0);
                }
                int currAmount = this.number.get(fluid);
                currAmount += amount;
                this.number.remove(fluid);
                this.number.put(fluid, currAmount);
                stack.stackSize--;
                this.hasUpdated = false;
                this.targetFill = fill + amount;
                needsUpdate = true;
                markDirty();

            }
        }
        return stack;
    }

    public CrucibleLiquid getLiquidFromIngot(ItemStack ingot) {
        CrucibleLiquid output = null;
        for (CrucibleLiquid liquid : SteamcraftRegistry.liquids) {
            ItemStack ingotClone = ingot.copy();
            ingotClone.stackSize = 1;
            if (liquid.ingot.equals(ingotClone)) {
                output = liquid;
                break;
            }
        }
        return output;
    }

    public CrucibleLiquid getNextLiquid(ICrucibleMold mold) {
        for (CrucibleLiquid liquid : SteamcraftRegistry.liquids) {
            if (this.number.containsKey(liquid)) {
                if (mold.canUseOn(liquid) && this.number.get(liquid) >= mold.getCostToMold(liquid)) {
                    return liquid;
                }
            }
        }
        return null;
    }

    public boolean isTipping() {
        return !(!this.tipping || this.tipTicks > 90);
    }

    public void setTipping() {
        //needsUpdate = true;
        this.tipping = true;
        this.tipTicks = 0;
    }

    public int getComparatorOutput() {
       return  (int) ((double) 15 * (((double) getFill() / 90D)));
        ////Steamcraft.log.debug(out);
    }
}
