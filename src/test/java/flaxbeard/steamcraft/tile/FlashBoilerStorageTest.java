package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.SteamcraftBlocks;
import flaxbeard.steamcraft.block.BlockFlashBoiler;
import flaxbeard.steamcraft.test.ForgeFluidTestSupport;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FlashBoilerStorageTest {
    private static final Item ITEM = new Item();
    private World world;
    private Block oldBlock;
    private final TileEntityFlashBoiler[] boilers = new TileEntityFlashBoiler[8];
    private final int[] metadata = new int[8];
    private final List<ItemStack> dropped = new ArrayList<>();
    public static class Flash extends TileEntityFlashBoiler { @Override public void refresh() {} }

    @BeforeClass public static void bootstrap() throws Exception {
        ForgeFluidTestSupport.bootstrap();
        TileEntity.addMapping(Flash.class,"flash_storage:tile");
        java.lang.reflect.Method register=Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);
        register.setAccessible(true); register.invoke(Item.itemRegistry,30025,"flash_storage:item",ITEM);
    }
    private int index(Object[] args) {
        int x=(int)args[0],y=(int)args[1]-128,z=(int)args[2];
        return x>=0&&x<2&&y>=0&&y<2&&z>=0&&z<2 ? x+2*z+4*y : -1;
    }
    @Before public void setup() throws Exception {
        oldBlock=SteamcraftBlocks.flashBoiler; SteamcraftBlocks.flashBoiler=new BlockFlashBoiler();
        world=mock(World.class); Block air=mock(Block.class);
        java.lang.reflect.Field provider=World.class.getDeclaredField("provider"); provider.setAccessible(true);
        provider.set(world,mock(net.minecraft.world.WorldProvider.class));
        when(world.blockExists(anyInt(),anyInt(),anyInt())).thenReturn(true);
        when(world.getBlock(anyInt(),anyInt(),anyInt())).thenAnswer(i -> index(i.getArguments())<0?air:SteamcraftBlocks.flashBoiler);
        when(world.getTileEntity(anyInt(),anyInt(),anyInt())).thenAnswer(i -> index(i.getArguments())<0?null:boilers[index(i.getArguments())]);
        when(world.getBlockMetadata(anyInt(),anyInt(),anyInt())).thenAnswer(i -> index(i.getArguments())<0?0:metadata[index(i.getArguments())]);
        when(world.setBlockMetadataWithNotify(anyInt(),anyInt(),anyInt(),anyInt(),anyInt())).thenAnswer(i -> {
            int n=index(i.getArguments()); metadata[n]=i.getArgument(3); boilers[n].updateContainingBlockInfo(); return true;
        });
        when(world.spawnEntityInWorld(any())).thenAnswer(i -> { dropped.add(((EntityItem)i.getArgument(0)).getEntityItem().copy()); return true; });
        for(int i=0;i<8;i++) {
            boilers[i]=new Flash(); boilers[i].setWorldObj(world);
            boilers[i].xCoord=i%2; boilers[i].yCoord=128+i/4; boilers[i].zCoord=i/2%2; metadata[i]=i+1;
        }
    }
    @After public void cleanup() { SteamcraftBlocks.flashBoiler=oldBlock; }
    private ItemStack named(int amount,String name) { return new ItemStack(ITEM,amount).setStackDisplayName(name); }
    private void localContents(int i,ItemStack stack) {
        int old=metadata[i]; metadata[i]=1; boilers[i].setInventorySlotContents(0,stack); metadata[i]=old; boilers[i].updateContainingBlockInfo();
    }
    private void breakMember(int i) {
        TileEntityFlashBoiler b=boilers[i]; SteamcraftBlocks.flashBoiler.onBlockPreDestroy(world,b.xCoord,b.yCoord,b.zCoord,metadata[i]);
    }
    private int count(String name) { return dropped.stream().filter(s -> s.getDisplayName().equals(name)).mapToInt(s -> s.stackSize).sum(); }
    private NBTTagCompound save(int i) { NBTTagCompound n=new NBTTagCompound();boilers[i].writeToNBT(n);return n; }

    @Test public void breakingFormerPrimaryDropsStoredInventoryOnceWithTags() {
        localContents(0,named(37,"stored")); boilers[0].destroyMultiblock();
        breakMember(0); breakMember(0);
        assertEquals(37,count("stored")); assertEquals(0,save(0).getTagList("Items",10).tagCount());
    }
    @Test public void breakingSecondaryDropsPrimaryAndItsOwnLegacyContentsOnce() {
        localContents(0,named(13,"primary")); localContents(5,named(7,"secondary"));
        breakMember(5); breakMember(5); breakMember(0);
        assertEquals(13,count("primary")); assertEquals(7,count("secondary"));
        assertEquals(0,save(0).getTagList("Items",10).tagCount()); assertEquals(0,save(5).getTagList("Items",10).tagCount());
    }
    @Test public void missingPrimaryDoesNotPreventLocalDropsOrLoadPrimary() {
        localContents(5,named(7,"secondary")); when(world.blockExists(0,128,0)).thenReturn(false);
        clearInvocations(world); breakMember(5);
        assertEquals(7,count("secondary")); verify(world,never()).getTileEntity(0,128,0);
    }
    @Test public void clientBreakDoesNotDropOrClearInventory() {
        localContents(0,named(13,"primary")); world.isRemote=true;breakMember(0);
        assertEquals(0,dropped.size());assertEquals(13,boilers[0].getStackInSlot(0).stackSize);
    }
    @Test public void secondaryDoesNotBurnItsStrandedFuel() {
        boilers[5].furnaceBurnTime=50;boilers[5].currentItemBurnTime=80;
        boilers[5].updateEntity();boilers[5].updateEntity();
        assertEquals(50,boilers[5].furnaceBurnTime);assertEquals(80,boilers[5].currentItemBurnTime);
    }
    @Test public void detachedContentsSurviveSaveBeforeTheBlockIsBroken() {
        localContents(0,named(37,"stored")); boilers[0].destroyMultiblock();
        NBTTagCompound tag=save(0); Flash loaded=new Flash();loaded.setWorldObj(world);loaded.readFromNBT(tag);boilers[0]=loaded;
        breakMember(0);assertEquals(37,count("stored"));
    }
    private void prepareReassembly() {
        boilers[0].destroyMultiblock();
        for(TileEntityFlashBoiler boiler:boilers)boiler.myTank.setFluid(null);
    }
    private void form() { boilers[7].checkMultiblock(false,3); }
    private int storedWater() { int sum=0;for(TileEntityFlashBoiler b:boilers)sum+=b.myTank.getFluidAmount();return sum; }
    @Test public void rebuildingMovesFormerPrimaryResourcesToTheNewPrimary() {
        prepareReassembly();localContents(1,named(20,"fuel"));
        boilers[1].myTank.setFluid(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,70000));
        boilers[1].furnaceBurnTime=100000;boilers[1].currentItemBurnTime=120000;boilers[1].heat=800;
        form();
        assertEquals(70000,boilers[0].myTank.getFluidAmount());assertEquals(70000,storedWater());
        assertEquals(20,boilers[0].getStackInSlot(0).stackSize);assertEquals("fuel",boilers[0].getStackInSlot(0).getDisplayName());
        assertEquals(100000,boilers[0].furnaceBurnTime);assertEquals(120000,boilers[0].currentItemBurnTime);assertEquals(800,boilers[0].heat);
        assertEquals(0,boilers[1].furnaceBurnTime);assertEquals(0,boilers[1].heat);assertEquals(0,save(1).getTagList("Items",10).tagCount());
    }
    @Test public void rebuildingSpillsOnlyWaterBeyondEightyBuckets() {
        prepareReassembly();
        boilers[0].myTank.setFluid(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,50000));
        boilers[1].myTank.setFluid(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,70000));
        form();assertEquals(80000,boilers[0].myTank.getFluidAmount());assertEquals(80000,storedWater());
        boilers[0].destroyMultiblock();form();assertEquals(80000,storedWater());
    }
    @Test public void conflictingAndExcessItemsDropWithTheirTagsInsteadOfDisappearing() {
        prepareReassembly();localContents(0,named(63,"same"));localContents(1,named(4,"same"));localContents(2,named(5,"other"));
        // Simulate a stacked water-input legacy save; the active water slot can process only one item.
        metadata[0]=1;boilers[0].setInventorySlotContents(1,named(2,"water"));metadata[0]=0;boilers[0].updateContainingBlockInfo();
        form();
        verify(world,atLeastOnce()).spawnEntityInWorld(argThat(entity -> entity instanceof EntityItem && entity.posY >= 130));
        assertEquals(64,boilers[0].getStackInSlot(0).stackSize);assertEquals(1,boilers[0].getStackInSlot(1).stackSize);
        assertEquals(3,count("same"));assertEquals(5,count("other"));assertEquals(1,count("water"));
        boilers[0].destroyMultiblock();form();assertEquals(3,count("same"));assertEquals(5,count("other"));assertEquals(1,count("water"));
    }
    @Test public void mergedResourcesSurviveSavingBothOldAndNewOwners() {
        prepareReassembly();localContents(1,named(20,"fuel"));boilers[1].furnaceBurnTime=700;
        boilers[1].myTank.setFluid(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,76543));form();
        for(int i=0;i<2;i++){NBTTagCompound tag=save(i);Flash loaded=new Flash();loaded.setWorldObj(world);loaded.readFromNBT(tag);boilers[i]=loaded;}
        assertEquals(76543,boilers[0].myTank.getFluidAmount());assertEquals(700,boilers[0].furnaceBurnTime);
        assertEquals(20,boilers[0].getStackInSlot(0).stackSize);assertEquals(0,save(1).getTagList("Items",10).tagCount());assertEquals(0,boilers[1].myTank.getFluidAmount());
    }
    @Test public void burnTimeOverflowIsRetainedUntilThePrimaryCanUseIt() {
        prepareReassembly();boilers[0].furnaceBurnTime=Integer.MAX_VALUE-20;boilers[1].furnaceBurnTime=30;form();
        assertEquals(Integer.MAX_VALUE,boilers[0].furnaceBurnTime);assertEquals(10,boilers[1].furnaceBurnTime);
        boilers[0].furnaceBurnTime=0;boilers[0].updateEntity();boilers[0].updateEntity();
        assertEquals(0,boilers[1].furnaceBurnTime);assertEquals(9,boilers[0].furnaceBurnTime);
    }
    @Test public void incompleteFormationLeavesResourcesUntouched() {
        prepareReassembly();localContents(1,named(20,"fuel"));boilers[1].myTank.setFluid(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,70000));
        when(world.blockExists(1,128,0)).thenReturn(false);form();
        assertEquals(0,metadata[0]);assertEquals(70000,boilers[1].myTank.getFluidAmount());assertEquals(0,dropped.size());
        assertEquals(1,save(1).getTagList("Items",10).tagCount());
    }
    @Test public void freshFlashBoilersDoNotCreateFreeWaterWhenCombined() {
        for(TileEntityFlashBoiler b:boilers)assertEquals(0,b.myTank.getFluidAmount());
        boilers[0].destroyMultiblock();form();assertEquals(0,storedWater());
    }

}
