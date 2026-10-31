package flaxbeard.steamcraft.block;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import flaxbeard.steamcraft.Config;
import flaxbeard.steamcraft.SteamcraftBlocks;
import flaxbeard.steamcraft.api.CrucibleLiquid;
import flaxbeard.steamcraft.api.IWrenchable;
import flaxbeard.steamcraft.api.SteamcraftRegistry;
import flaxbeard.steamcraft.api.Tuple3;
import flaxbeard.steamcraft.integration.CrossMod;
import flaxbeard.steamcraft.integration.thaumcraft.ThaumcraftIntegration;
import flaxbeard.steamcraft.tile.TileEntityCrucible;
import flaxbeard.steamcraft.tile.TileEntitySteamHeater;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.apache.commons.lang3.tuple.MutablePair;

public class BlockSteamcraftCrucible extends BlockContainer implements IWrenchable {
    private static float px = (1.0F / 16.0F);
    public IIcon innerIcon;
    public IIcon topIcon;
    public IIcon bottomIcon;
    public IIcon liquidIcon;
    public IIcon blank;

    public BlockSteamcraftCrucible() {
        super(Material.rock);
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int i, int j, int k) {
        return AxisAlignedBB.getBoundingBox(i + px, j + 0.0F + px, k + px, i + 1.0F - px, j + 1.0F - px, k + 1.0F - px);
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te != null && te instanceof TileEntityCrucible) {
            TileEntityCrucible crucible = (TileEntityCrucible) te;
            return crucible.getComparatorOutput();
        }
        return 0;
    }

    public boolean isCrucibleHeated(World world, int x, int y, int z) {
        Block blockUnderCrucible = world.getBlock(x, y - 1, z);
        TileEntity tileUnderCrucible = world.getTileEntity(x, y - 1, z);
        int steam = Config.heaterConsumption;

        if (this == SteamcraftBlocks.hellCrucible ||
          blockUnderCrucible.getMaterial() == Material.fire ||
          blockUnderCrucible.getMaterial() == Material.lava || Config.enableThaumcraftIntegration &&
          Config.enableNitorPoweredCrucible && CrossMod.THAUMCRAFT &&
          ThaumcraftIntegration.isNitorUnderBlock(world, x, y, z)) {
            return true;
        }

        if (tileUnderCrucible instanceof TileEntitySteamHeater) {
            TileEntitySteamHeater steamHeater = (TileEntitySteamHeater) tileUnderCrucible;

            if (steamHeater.myDir() == ForgeDirection.UP && steamHeater.getSteam() >= steam) {
                steamHeater.decrSteam(steam);
                return true;
            } else {
                return false;
            }
        }

        return false;
    }

    @Override
    public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
        if (world.isRemote || !(entity instanceof EntityItem)) {
            return;
        }
        EntityItem item = (EntityItem) entity;
        ItemStack stack = item.getEntityItem();
        if (stack == null || stack.stackSize <= 0) {
            return;
        }
        MutablePair<CrucibleLiquid, Integer> output = SteamcraftRegistry.liquidRecipes.get(
            MutablePair.of(stack.getItem(), stack.getItemDamage()));
        if (output == null) {
            output = SteamcraftRegistry.liquidRecipes.get(MutablePair.of(stack.getItem(), -1));
        }
        TileEntity tile = world.getTileEntity(x, y, z);
        if (output == null || !(tile instanceof TileEntityCrucible)) {
            return;
        }
        TileEntityCrucible crucible = (TileEntityCrucible) tile;
        int amount = output.right;
        if (!crucible.hasUpdated || crucible.getFill() + amount > 90) {
            return;
        }
        if (item.delayBeforeCanPickup > 2) {
            item.delayBeforeCanPickup = 2;
            return;
        }
        // A steam heater is charged by this check, so only call it for a melt that can proceed.
        if (item.delayBeforeCanPickup > 1 || !isCrucibleHeated(world, x, y, z)) {
            return;
        }
        ItemStack remaining = crucible.fillWith(stack, amount, output);
        if (crucible.getFill() + amount <= 90) {
            item.delayBeforeCanPickup = 2;
        }
        if (remaining.stackSize <= 0) {
            item.setDead();
        } else {
            item.setEntityItemStack(remaining);
        }
    }

    @Override
    public void onBlockPlacedBy(World worldIn, int x, int y, int z, EntityLivingBase placer, ItemStack itemIn) {
        int l = MathHelper.floor_double((double) (placer.rotationYaw * 4.0F / 360.0F) + 2.5D) & 3;
        worldIn.setBlockMetadataWithNotify(x, y, z, l, 2);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess par1iBlockAccess, int par2, int par3, int par4) {
        setBlockBounds(px, 0.0F + px, px, 1.0F - px, 1.0F - px, 1.0F - px);
        super.setBlockBoundsBasedOnState(par1iBlockAccess, par2, par3, par4);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(int side, int meta) {
        return blank;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister ir) {
        this.innerIcon = ir.registerIcon(this.getTextureName() + "_" + "inner");
        this.topIcon = ir.registerIcon(this.getTextureName() + "_top");
        this.bottomIcon = ir.registerIcon(this.getTextureName() + "_" + "bottom");
        this.blockIcon = ir.registerIcon(this.getTextureName() + "_side");
        this.liquidIcon = ir.registerIcon(this.getTextureName() + "_liquid");
        this.blank = ir.registerIcon("steamcraft:blankTexture");
    }

    @Override
    public TileEntity createNewTileEntity(World var1, int var2) {
        return new TileEntityCrucible();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float subX, float subY, float subZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (!(tileEntity instanceof TileEntityCrucible)) {
            return true;
        }
        TileEntityCrucible tile = (TileEntityCrucible) tileEntity;
        ItemStack held = player.getHeldItem();
        if (!player.isSneaking() && held == null) {
            if (!tile.isTipping()) {
                tile.setTipping();
                tile.needsUpdate = true;
                tile.markDirty();
            }
        } else if (held != null && held.stackSize > 0) {
            for (CrucibleLiquid liquid : tile.contents) {
                MutablePair<Integer, ItemStack> recipe = SteamcraftRegistry.dunkRecipes.get(
                    new Tuple3(held.getItem(), held.getItemDamage(), liquid));
                if (recipe == null) {
                    recipe = SteamcraftRegistry.dunkRecipes.get(new Tuple3(held.getItem(), -1, liquid));
                }
                if (recipe == null || tile.number.get(liquid) < recipe.left) {
                    continue;
                }
                ItemStack result = recipe.right.copy();
                player.inventory.decrStackSize(player.inventory.currentItem, 1);
                int remaining = tile.number.get(liquid) - recipe.left;
                if (remaining == 0) {
                    tile.contents.remove(liquid);
                    tile.number.remove(liquid);
                } else {
                    tile.number.put(liquid, remaining);
                }
                if (!player.inventory.addItemStackToInventory(result)) {
                    player.entityDropItem(result, 0.0F);
                }
                tile.needsUpdate = true;
                tile.markDirty();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean onWrench(ItemStack stack, EntityPlayer player, World world,
                            int x, int y, int z, int side, float xO, float yO, float zO) {
        int meta = world.getBlockMetadata(x, y, z);
        if (side != 0 && side != 1) {
            int output = meta;
            switch (side) {
                case 2:
                    output = 2;
                    break;
                case 3:
                    output = 0;
                    break;
                case 4:
                    output = 1;
                    break;
                case 5:
                    output = 3;
                    break;
            }
            if (output == meta && side > 1 && side < 6) {
                switch (ForgeDirection.getOrientation(side).getOpposite().ordinal()) {
                    case 2:
                        output = 2;
                        break;
                    case 3:
                        output = 0;
                        break;
                    case 4:
                        output = 1;
                        break;
                    case 5:
                        output = 3;
                        break;
                }
            }
            world.setBlockMetadataWithNotify(x, y, z, output, 2);
            return true;
        }
        return false;
    }
}
