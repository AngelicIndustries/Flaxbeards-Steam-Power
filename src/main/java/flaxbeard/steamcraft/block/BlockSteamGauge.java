package flaxbeard.steamcraft.block;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import flaxbeard.steamcraft.Steamcraft;
import flaxbeard.steamcraft.misc.SteamAttachment;
import flaxbeard.steamcraft.tile.TileEntitySteamGauge;
import flaxbeard.steamcraft.tile.TileEntitySteamPipe;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;


public class BlockSteamGauge extends BlockContainer {
    public IIcon front;
    public IIcon back;
    public IIcon top;

    public BlockSteamGauge() {
        super(Material.iron);
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride(World world, int x, int y, int z, int meta) {
        if (!world.blockExists(x, y, z)) return 0;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te != null && te instanceof TileEntitySteamGauge) {
            return ((TileEntitySteamGauge) te).getComparatorOutput();
        }
        return 0;
    }

    @Override
    public String getItemIconName() {
        return "steamcraft:gauge";
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int xl, int yl, int zl) {
        int meta = world.getBlockMetadata(xl, yl, zl);
        float px = 1.0F / 16.0F;
        float x = 4 * px;
        float y = 4 * px;
        float z = 0.0F;
        float x2 = 12 * px;
        float y2 = 12 * px;
        float z2 = px;
        if (SteamAttachment.getSource(world, xl, yl, zl, meta) instanceof TileEntitySteamPipe) {
            z = -5 * px;
            z2 = -4 * px + 0.0005F;
        }
        this.setBlockBounds(x, y, z, x2, y2, z2);
        switch (meta) {
            case 5:
                this.setBlockBounds(z, y, x, z2, y2, x2);
                break;
            case 2:
                this.setBlockBounds(1 - x2, y, 1 - z2, 1 - x, y2, 1 - z);
                break;
            case 4:
                this.setBlockBounds(1 - z2, y, 1 - x2, 1 - z, y2, 1 - x);
                break;
            case 3:
                this.setBlockBounds(x, y, z, x2, y2, z2);
                break;
        }
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, int x, int y, int z, int side) {
        return SteamAttachment.getSource(world, x, y, z, side) != null;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (world.isRemote || !world.blockExists(x, y, z)) return;
        int metadata = world.getBlockMetadata(x, y, z);
        int side = metadata;
        if (SteamAttachment.isSupportLoaded(world, x, y, z, side) && !canPlaceBlockOnSide(world, x, y, z, side)) {
            dropBlockAsItem(world, x, y, z, metadata, 0);
            world.setBlockToAir(x, y, z);
        }
    }

    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float subX, float subY, float subZ, int meta) {
        return side;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == meta || side == ForgeDirection.getOrientation(meta).getOpposite().ordinal() ? (side == meta ? back : front) : blockIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister ir) {
        this.blockIcon = ir.registerIcon("steamcraft:gaugeTop");
        this.back = ir.registerIcon("steamcraft:gaugeFront");
        this.front = ir.registerIcon("steamcraft:gaugeBack");
        this.top = blockIcon;
    }


    @Override
    public TileEntity createNewTileEntity(World var1, int var2) {
        return new TileEntitySteamGauge();
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return Steamcraft.gaugeRenderID;
    }

//    @Override
//    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float xf, float yf, float zf){
//    	TileEntitySteamGauge gauge = (TileEntitySteamGauge)world.getTileEntity(x, y, z);
//    	int pressurePerc = (int)(gauge.getPressure() * 100);
//    	if (world.isRemote){
//    		String color = "";
//    		
//    		if (pressurePerc > 120){
//    			color = "�4";
//    		} else if (pressurePerc > 115){
//    			color = "�c";
//    		} else if (pressurePerc > 100){
//    			color = "�e";
//    		}
//    		
//    		player.addChatComponentMessage(new ChatComponentText(color+"Current pressure: "+pressurePerc+"%"));
//    			
//    	}
//    	
//    	return true;
//    }

}
