package flaxbeard.steamcraft.block;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import flaxbeard.steamcraft.Steamcraft;
import flaxbeard.steamcraft.api.block.BlockSteamTransporter;
import flaxbeard.steamcraft.misc.FluidHelper;
import flaxbeard.steamcraft.tile.TileEntityFlashBoiler;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import java.util.Random;

// Notes:
// ATM meta 1 has a special texture, because that's going to be the master TE for the multi.
// meta 1 is always the one with the lowest x, y, and z.

// meta 0  : not in multiblock
// meta 1-8: in multiblock, signifies which corner


public class BlockFlashBoiler extends BlockSteamTransporter {

    private final Random rand = new Random();

    public IIcon otherIcon;
    public IIcon specialIcon;

    public IIcon topLeft;
    public IIcon topLeftSide;
    public IIcon bottomLeft;
    public IIcon topRight;
    public IIcon topRightSide;
    public IIcon bottomRight;
    public IIcon topLeftF;
    public IIcon bottomLeftF;
    public IIcon topRightF;
    public IIcon bottomRightF;
    public IIcon topLeftO;
    public IIcon bottomLeftO;
    public IIcon topRightO;
    public IIcon bottomRightO;

    public BlockFlashBoiler() {
        super(Material.iron);

    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityFlashBoiler();
    }

    @Override
    public void onBlockPreDestroy(World world, int x, int y, int z, int meta) {
        if (world.isRemote) return;
        super.onBlockPreDestroy(world, x, y, z, meta);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityFlashBoiler)) return;
        TileEntityFlashBoiler boiler = (TileEntityFlashBoiler) tile;
        TileEntityFlashBoiler primary = boiler.getPrimaryTileEntity();
        // Preserve the existing shared-inventory drops, and also recover this block's hidden local items.
        if (primary != null && primary != boiler) dropInventory(world, x, y, z, primary.removeLocalInventory());
        dropInventory(world, x, y, z, boiler.removeLocalInventory());
        if (meta > 0) boiler.destroyMultiblock();
    }

    private void dropInventory(World world, int x, int y, int z, ItemStack[] contents) {
        for (ItemStack stack : contents) {
            if (stack == null || stack.stackSize <= 0) continue;
            float ox = rand.nextFloat() * 0.8F + 0.1F;
            float oy = rand.nextFloat() * 0.8F + 0.1F;
            float oz = rand.nextFloat() * 0.8F + 0.1F;
            while (stack.stackSize > 0) {
                ItemStack drop = stack.splitStack(Math.min(stack.stackSize, rand.nextInt(21) + 10));
                EntityItem entity = new EntityItem(world, x + ox, y + oy, z + oz, drop);
                entity.motionX = rand.nextGaussian() * 0.05F;
                entity.motionY = rand.nextGaussian() * 0.05F + 0.2F;
                entity.motionZ = rand.nextGaussian() * 0.05F;
                world.spawnEntityInWorld(entity);
            }
        }
    }

    public void checkMultiblock(World world, int x, int y, int z, boolean isBreaking, int frontSide) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof TileEntityFlashBoiler) ((TileEntityFlashBoiler) tile).checkMultiblock(isBreaking, frontSide);
        }

    }

    @Override
    public void registerBlockIcons(IIconRegister ir) {
        this.blockIcon = ir.registerIcon("steamcraft:flashBoiler");
        this.otherIcon = ir.registerIcon("steamcraft:testFront");
        this.specialIcon = ir.registerIcon("steamcraft:testSpecial");

        this.topLeft = ir.registerIcon("steamcraft:flashBoilerTopLeft");
        this.topLeftSide = ir.registerIcon("steamcraft:flashBoilerTopLeftSide");
        this.bottomLeft = ir.registerIcon("steamcraft:flashBoilerBottomLeft");
        this.topRight = ir.registerIcon("steamcraft:flashBoilerTopRight");
        this.topRightSide = ir.registerIcon("steamcraft:flashBoilerTopRightSide");

        this.bottomRight = ir.registerIcon("steamcraft:flashBoilerBottomRight");
        this.topLeftF = ir.registerIcon("steamcraft:flashBoilerTopLeftO");
        this.bottomLeftF = ir.registerIcon("steamcraft:flashBoilerBottomLeftO");
        this.topRightF = ir.registerIcon("steamcraft:flashBoilerTopRightO");
        this.bottomRightF = ir.registerIcon("steamcraft:flashBoilerBottomRightO");
        this.topLeftO = ir.registerIcon("steamcraft:flashBoilerTopLeftT");
        this.bottomLeftO = ir.registerIcon("steamcraft:flashBoilerBottomLeftT");
        this.topRightO = ir.registerIcon("steamcraft:flashBoilerTopRightT");
        this.bottomRightO = ir.registerIcon("steamcraft:flashBoilerBottomRightT");

    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float xf, float yf, float zf) {
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityFlashBoiler)) return false;
        TileEntityFlashBoiler boiler = (TileEntityFlashBoiler) tile;
        boiler = boiler.getPrimaryTileEntity();
        if (boiler == null) return false;
        if (world.isRemote) return true;
        if (FluidHelper.playerIsHoldingWaterContainer(player)) {
            FluidHelper.fillTankFromHeldItem(player, boiler.getTank());
        } else {
            player.openGui(Steamcraft.instance, 0, world, x, y, z);
        }
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack stack) {
        ////Steamcraft.log.debug("onBlockPlacedBy fired");
        int l = MathHelper.floor_double((double) (entity.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        int frontSide = -1;
        switch (l) {
            case 0:
                frontSide = 2;
                break;
            case 1:
                frontSide = 5;
                break;
            case 2:
                frontSide = 3;
                break;
            case 3:
                frontSide = 4;
                break;
            default:
                ////Steamcraft.log.debug(l);
                break;
        }

        checkMultiblock(world, x, y, z, false, frontSide);
    }

    @Override
    public IIcon getIcon(IBlockAccess block, int x, int y, int z, int side) {
        ////Steamcraft.log.debug(meta);
        int meta = block.getBlockMetadata(x, y, z);
        if (meta == 0) {
            return blockIcon;
        } else {
            TileEntityFlashBoiler boiler = (TileEntityFlashBoiler) block.getTileEntity(x, y, z);
            IIcon tex = otherIcon;
            if (meta == 1) {
                if (side == 0) {
                    tex = topLeft;
                } else if (side == 4) {
                    tex = bottomLeft;
                } else {
                    tex = bottomRight;
                }
            }
            if (meta == 2) {
                if (side == 2) {
                    tex = bottomLeft;
                } else if (side == 0) {
                    tex = topRight;
                } else {
                    tex = bottomRight;
                }
            }
            if (meta == 3) {
                if (side == 3 || side == 0) {
                    tex = bottomLeft;
                } else {
                    tex = bottomRight;
                }
            }
            //Fixed
            if (meta == 4) {
                if (side == 5) {
                    tex = bottomLeft;
                } else {
                    tex = bottomRight;
                }
            }
            //Front
            if (meta == 5) {
                if (side == 1) {
                    tex = topLeft;
                } else if (side == 4) {
                    tex = topLeftSide;
                } else {
                    tex = topRightSide;
                }
            }
            //Fixed
            if (meta == 6) {
                if (side == 2) {
                    tex = topLeftSide;
                } else if (side == 1) {
                    tex = topRight;
                } else {
                    tex = topRightSide;
                }
            }
            //Front
            if (meta == 7) {
                if (side == 3) {
                    tex = topLeftSide;
                } else if (side == 1) {
                    tex = bottomLeft;
                } else {
                    tex = topRightSide;
                }
            }
            if (meta == 8) {
                if (side == 1) {
                    tex = bottomRight;
                } else if (side == 5) {
                    tex = topLeftSide;
                } else {
                    tex = topRightSide;
                }
            }
            if (side == boiler.getFront()) {
                if (boiler.getBurning()) {
                    if (tex == topLeftSide) {
                        return topLeftO;
                    }
                    if (tex == topRightSide) {
                        return topRightO;
                    }
                    if (tex == bottomRight) {
                        return bottomRightO;
                    }
                    if (tex == bottomLeft) {
                        return bottomLeftO;
                    }
                }
                if (tex == topLeftSide) {
                    return topLeftF;
                }
                if (tex == topRightSide) {
                    return topRightF;
                }
                if (tex == bottomRight) {
                    return bottomRightF;
                }
                if (tex == bottomLeft) {
                    return bottomLeftF;
                }
            }
            return tex;
        }

    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random random) {
        if (world.getBlockMetadata(x, y, z) < 5) {
            TileEntityFlashBoiler boiler = (TileEntityFlashBoiler) world.getTileEntity(x, y, z);

            if (boiler.isBurning()) {
                int front = boiler.getFront();
                float xOffset = (float) x + 0.5F;
                float yOffset = (float) y + 0.25F + random.nextFloat() * 0.8F;
                float zOffset = (float) z + 0.5F;
                float f3 = 0.52F;
                float f4 = getRandomFlameOffset(random);

                if (front == 4) {
                    world.spawnParticle("smoke", (double) (xOffset - f3), (double) yOffset, (double) (zOffset + f4), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset - f3), (double) yOffset, (double) (zOffset + f4), 0.0D, 0.0D, 0.0D);
                    f4 = getRandomFlameOffset(random);

                    world.spawnParticle("smoke", (double) (xOffset - f3), (double) yOffset, (double) (zOffset + f4), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset - f3), (double) yOffset, (double) (zOffset + f4), 0.0D, 0.0D, 0.0D);
                    f4 = getRandomFlameOffset(random);

                    world.spawnParticle("smoke", (double) (xOffset - f3), (double) yOffset, (double) (zOffset + f4), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset - f3), (double) yOffset, (double) (zOffset + f4), 0.0D, 0.0D, 0.0D);
                    f4 = getRandomFlameOffset(random);
//			                
//			                world.spawnParticle("smoke", (double)(xOffset - f3), (double)yOffset, (double)(zOffset + f4), 0.0D, 0.0D, 0.0D);
//			                world.spawnParticle("flame", (double)(xOffset - f3), (double)yOffset, (double)(zOffset + f4), 0.0D, 0.0D, 0.0D);
                } else if (front == 5) {
                    world.spawnParticle("smoke", (double) (xOffset + f3), (double) yOffset, (double) (zOffset + f4), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset + f3), (double) yOffset, (double) (zOffset + f4), 0.0D, 0.0D, 0.0D);
                    f4 = getRandomFlameOffset(random);

                    world.spawnParticle("smoke", (double) (xOffset + f3), (double) yOffset, (double) (zOffset + f4), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset + f3), (double) yOffset, (double) (zOffset + f4), 0.0D, 0.0D, 0.0D);
                    f4 = getRandomFlameOffset(random);

                    world.spawnParticle("smoke", (double) (xOffset + f3), (double) yOffset, (double) (zOffset + f4), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset + f3), (double) yOffset, (double) (zOffset + f4), 0.0D, 0.0D, 0.0D);
                    f4 = getRandomFlameOffset(random);

                    world.spawnParticle("smoke", (double) (xOffset + f3), (double) yOffset, (double) (zOffset + f4), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset + f3), (double) yOffset, (double) (zOffset + f4), 0.0D, 0.0D, 0.0D);
                } else if (front == 2) {
                    world.spawnParticle("smoke", (double) (xOffset + f4), (double) yOffset, (double) (zOffset - f3), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset + f4), (double) yOffset, (double) (zOffset - f3), 0.0D, 0.0D, 0.0D);
                    f4 = getRandomFlameOffset(random);

                    world.spawnParticle("smoke", (double) (xOffset + f4), (double) yOffset, (double) (zOffset - f3), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset + f4), (double) yOffset, (double) (zOffset - f3), 0.0D, 0.0D, 0.0D);
                    f4 = getRandomFlameOffset(random);

                    world.spawnParticle("smoke", (double) (xOffset + f4), (double) yOffset, (double) (zOffset - f3), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset + f4), (double) yOffset, (double) (zOffset - f3), 0.0D, 0.0D, 0.0D);
                    f4 = getRandomFlameOffset(random);

                    world.spawnParticle("smoke", (double) (xOffset + f4), (double) yOffset, (double) (zOffset - f3), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset + f4), (double) yOffset, (double) (zOffset - f3), 0.0D, 0.0D, 0.0D);
                } else if (front == 3) {
                    world.spawnParticle("smoke", (double) (xOffset + f4), (double) yOffset, (double) (zOffset + f3), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset + f4), (double) yOffset, (double) (zOffset + f3), 0.0D, 0.0D, 0.0D);
                    f4 = getRandomFlameOffset(random);

                    world.spawnParticle("smoke", (double) (xOffset + f4), (double) yOffset, (double) (zOffset + f3), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset + f4), (double) yOffset, (double) (zOffset + f3), 0.0D, 0.0D, 0.0D);
                    f4 = getRandomFlameOffset(random);

                    world.spawnParticle("smoke", (double) (xOffset + f4), (double) yOffset, (double) (zOffset + f3), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset + f4), (double) yOffset, (double) (zOffset + f3), 0.0D, 0.0D, 0.0D);
                    f4 = getRandomFlameOffset(random);

                    world.spawnParticle("smoke", (double) (xOffset + f4), (double) yOffset, (double) (zOffset + f3), 0.0D, 0.0D, 0.0D);
                    world.spawnParticle("flame", (double) (xOffset + f4), (double) yOffset, (double) (zOffset + f3), 0.0D, 0.0D, 0.0D);
                }
            }
        }

    }

    private float getRandomFlameOffset(Random random) {
        return random.nextFloat() * 0.8F - 0.4F;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {

        super.breakBlock(world, x, y, z, block, meta);

    }

}
