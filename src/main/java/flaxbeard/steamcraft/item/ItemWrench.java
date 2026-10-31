package flaxbeard.steamcraft.item;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import flaxbeard.steamcraft.api.IPipeWrench;
import flaxbeard.steamcraft.api.IWrenchable;
import flaxbeard.steamcraft.tile.TileEntitySteamPipe;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class ItemWrench extends Item implements IPipeWrench{

    @SideOnly(Side.CLIENT)
    public boolean isFull3D() {
        return true;
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float xO, float yO, float zO) {
        if (stack == null || stack.stackSize <= 0 || side < 0 || side > 5 || !canWrench(player, x, y, z)) {
            return false;
        }
        TileEntity tile = world.getTileEntity(x, y, z);
        if (world.isRemote) {
            // Pipe subparts are targeted on the client and changed through ConnectPacket.
            if (tile instanceof TileEntitySteamPipe && !player.isSneaking()) {
                ((TileEntitySteamPipe) tile).onWrench(stack, player, world, x, y, z, side, xO, yO, zO);
            }
            return false;
        }
        if (!world.canMineBlock(player, x, y, z) || !player.canPlayerEdit(x, y, z, side, stack)) {
            return false;
        }
        Block block = world.getBlock(x, y, z);
        boolean handled = false;
        if (block instanceof IWrenchable) {
            handled = ((IWrenchable) block).onWrench(stack, player, world, x, y, z, side, xO, yO, zO);
        }
        if (tile instanceof IWrenchable) {
            handled |= ((IWrenchable) tile).onWrench(stack, player, world, x, y, z, side, xO, yO, zO);
        }
        if (handled) {
            if (tile != null) {
                tile.markDirty();
            }
            world.playSoundEffect(x + 0.5F, y + 0.5F, z + 0.5F, "steamcraft:wrench", 2.0F, 0.9F);
        }
        return handled;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float xO, float yO, float zO) {
        return false;
    }

    @Override
    public boolean canWrench(EntityPlayer player, int x, int y, int z){
        return true;
    }

    @Override
    public void wrenchUsed(EntityPlayer player, int x, int y, int z){
        player.swingItem();
    }
}
