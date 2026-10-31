package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.test.ForgeFluidTestSupport;
import flaxbeard.steamcraft.SteamcraftBlocks;
import net.minecraft.block.Block;
import net.minecraft.item.*;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BoilerInventoryTest {
    private World world;
    private Block oldBlock;
    private static final Item ITEM=new Item();
    @BeforeClass public static void bootstrap() throws Exception {
        ForgeFluidTestSupport.bootstrap();
        // The standalone test JVM has no vanilla item registration for Forge's container constants.
        java.lang.reflect.Field modifiers=java.lang.reflect.Field.class.getDeclaredField("modifiers"); modifiers.setAccessible(true);
        java.util.Map<java.lang.reflect.Field,Object> replaced=new java.util.HashMap<>();
        try {
        for(String name:new String[]{"bucket","glass_bottle","water_bucket","lava_bucket","potionitem"}) {
            java.lang.reflect.Field field=net.minecraft.init.Items.class.getField(name);
            modifiers.setInt(field,field.getModifiers() & ~java.lang.reflect.Modifier.FINAL); field.setAccessible(true);
            if(field.get(null)==null) {
                replaced.put(field,null); field.set(null,field.getType().newInstance());
            }
        }
        Class.forName("net.minecraftforge.fluids.FluidContainerRegistry");
        } finally { for(java.lang.reflect.Field field:replaced.keySet())field.set(null,replaced.get(field)); }
    }
    @Before public void setup() {
        oldBlock=SteamcraftBlocks.flashBoiler; SteamcraftBlocks.flashBoiler=mock(Block.class);
        world=mock(World.class);
        when(world.blockExists(anyInt(),anyInt(),anyInt())).thenReturn(true);
        when(world.getBlock(anyInt(),anyInt(),anyInt())).thenReturn(SteamcraftBlocks.flashBoiler);
        when(world.getBlockMetadata(anyInt(),anyInt(),anyInt())).thenReturn(1);
    }
    @After public void restore() { SteamcraftBlocks.flashBoiler=oldBlock; }
    private TileEntityBoiler[] boilers() {
        TileEntityBoiler[] result={new TileEntityBoiler(),new TileEntityFlashBoiler()};
        for(TileEntityBoiler b:result)b.setWorldObj(world);
        when(world.getTileEntity(0,0,0)).thenReturn(result[1]);
        return result;
    }
    @Test public void nonpositiveExtractionCannotGrowOrDirtyInventory() {
        for(TileEntityBoiler b:boilers()) {
            b.setInventorySlotContents(0,new ItemStack(ITEM,5));clearInvocations(world);
            assertNull(b.decrStackSize(0,-3));assertNull(b.decrStackSize(0,0));assertEquals(5,b.getStackInSlot(0).stackSize);
            verify(world,never()).markTileEntityChunkModified(anyInt(),anyInt(),anyInt(),any());
        }
    }
    @Test public void invalidSlotsAreRejectedByEveryInventoryEntryPoint() {
        for(TileEntityBoiler b:boilers())for(int slot:new int[]{-1,2,Integer.MIN_VALUE,Integer.MAX_VALUE}) {
            assertNull(b.getStackInSlot(slot));assertNull(b.decrStackSize(slot,1));assertNull(b.getStackInSlotOnClosing(slot));
            b.setInventorySlotContents(slot,new ItemStack(ITEM));assertFalse(b.isItemValidForSlot(slot,new ItemStack(ITEM)));
        }
    }
    @Test public void settingEmptyStacksClearsTheSlot() {
        for(TileEntityBoiler b:boilers())for(int count:new int[]{0,-3}) {
            b.setInventorySlotContents(0,new ItemStack(ITEM,count));assertNull(b.getStackInSlot(0));
        }
    }
    @Test public void emptyAndInvalidExtractionCapabilitiesAreSafe() {
        for(TileEntityBoiler b:boilers()) {
            assertFalse(b.canExtractItem(1,null,0));assertFalse(b.canExtractItem(1,new ItemStack(ITEM,0),0));
            assertFalse(b.canExtractItem(-1,new ItemStack(ITEM),0));assertFalse(b.canExtractItem(2,new ItemStack(ITEM),0));
        }
    }
    @Test public void unknownSidesDoNotAdvertiseSlotsOrAcceptItems() {
        ItemFluidContainer item=new ItemFluidContainer(0,1000);ItemStack full=new ItemStack(item);item.fill(full,new FluidStack(FluidRegistry.WATER,1000),true);
        for(TileEntityBoiler b:boilers())for(int side:new int[]{-1,6,99}) {
            assertEquals(0,b.getAccessibleSlotsFromSide(side).length);assertFalse(b.canInsertItem(1,full,side));assertFalse(b.canExtractItem(1,new ItemStack(item),side));
        }
    }
    @Test public void waterOnlyBelongsInItsActualSlot() {
        ItemFluidContainer item=new ItemFluidContainer(0,1000);ItemStack full=new ItemStack(item);item.fill(full,new FluidStack(FluidRegistry.WATER,1000),true);
        for(TileEntityBoiler b:boilers()) { assertTrue(b.isItemValidForSlot(1,full));assertFalse(b.isItemValidForSlot(2,full));assertFalse(b.isItemValidForSlot(-1,full)); }
    }
    @Test public void automationCanRecoverEmptyRefillableWaterContainersButNotPartialOnes() {
        ItemFluidContainer item=new ItemFluidContainer(0,1000);ItemStack empty=new ItemStack(item),partial=new ItemStack(item);
        item.fill(partial,new FluidStack(FluidRegistry.WATER,100),true);
        for(TileEntityBoiler b:boilers()) {
            assertTrue(b.canExtractItem(1,empty,0));assertFalse(b.canExtractItem(1,partial,0));assertFalse(b.canExtractItem(0,empty,0));
        }
    }
    @Test public void automationCanRecoverRegisteredEmptyWaterContainers() {
        Item full=new Item(),empty=new Item();
        FluidContainerRegistry.registerFluidContainer(new FluidStack(FluidRegistry.WATER,1000),new ItemStack(full),new ItemStack(empty));
        for(TileEntityBoiler b:boilers()) { assertTrue(b.canExtractItem(1,new ItemStack(empty),0));assertFalse(b.canExtractItem(1,new ItemStack(full),0)); }
    }
    @Test public void unformedFlashBoilersAdvertiseNoAutomationSlots() {
        when(world.getBlockMetadata(anyInt(),anyInt(),anyInt())).thenReturn(0);
        TileEntityFlashBoiler b=new TileEntityFlashBoiler();b.setWorldObj(world);
        for(int side=0;side<6;side++)assertEquals(0,b.getAccessibleSlotsFromSide(side).length);
    }
    @Test public void automationDoesNotStackWaterContainersInTheSingleInputSlot() {
        ItemFluidContainer item=new ItemFluidContainer(0,1000);ItemStack full=new ItemStack(item);item.fill(full,new FluidStack(FluidRegistry.WATER,1000),true);
        for(TileEntityBoiler b:boilers()) {
            assertTrue(b.canInsertItem(1,full,1)); b.setInventorySlotContents(1,full.copy());
            assertFalse(b.canInsertItem(1,full,1));
        }
    }

}
