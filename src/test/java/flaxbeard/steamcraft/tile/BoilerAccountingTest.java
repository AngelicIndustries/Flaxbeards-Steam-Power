package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.api.steamnet.SteamNetwork;
import flaxbeard.steamcraft.test.ForgeFluidTestSupport;
import flaxbeard.steamcraft.misc.FluidHelper;
import flaxbeard.steamcraft.SteamcraftBlocks;
import flaxbeard.steamcraft.block.BlockBoiler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BoilerAccountingTest {
    private World world;
    private Block oldFlashBlock;
    private static final Item ITEM = new Item();
    public static class Boiler extends TileEntityBoiler { @Override public void refresh() {} }
    public static class FlashBoiler extends TileEntityFlashBoiler { @Override public void refresh() {} }

    @BeforeClass public static void bootstrap() throws Exception {
        ForgeFluidTestSupport.bootstrap();
        TileEntity.addMapping(Boiler.class, "boiler_test:boiler");
        TileEntity.addMapping(FlashBoiler.class, "boiler_test:flash");
        java.lang.reflect.Method register = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        register.invoke(Item.itemRegistry, 30020, "boiler_test:item", ITEM);
    }

    @Before public void setUp() {
        oldFlashBlock = SteamcraftBlocks.flashBoiler;
        SteamcraftBlocks.flashBoiler = mock(Block.class);
        world = mock(World.class);
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(mock(Block.class));
        when(world.getBlockMetadata(anyInt(), anyInt(), anyInt())).thenReturn(1);
        when(world.blockExists(anyInt(), anyInt(), anyInt())).thenReturn(true);
    }
    @After public void restoreBlock() { SteamcraftBlocks.flashBoiler = oldFlashBlock; }
    private FlashBoiler formedFlash() {
        FlashBoiler primary = null;
        for (int i = 0; i < 8; i++) {
            FlashBoiler tile = place(new FlashBoiler());
            tile.xCoord = i % 2; tile.yCoord = i / 4; tile.zCoord = i / 2 % 2;
            when(world.getBlock(tile.xCoord, tile.yCoord, tile.zCoord)).thenReturn(SteamcraftBlocks.flashBoiler);
            when(world.getBlockMetadata(tile.xCoord, tile.yCoord, tile.zCoord)).thenReturn(i + 1);
            when(world.getTileEntity(tile.xCoord, tile.yCoord, tile.zCoord)).thenReturn(tile);
            if (i == 0) primary = tile;
        }
        return primary;
    }
    private <T extends TileEntityBoiler> T place(T tile) {
        tile.setWorldObj(world);
        tile.setNetwork(new SteamNetwork(100000));
        return tile;
    }
    private TileEntityBoiler[] boilers() { return new TileEntityBoiler[]{place(new Boiler()), place(new FlashBoiler())}; }
    private FluidStack water(int amount) { return new FluidStack(FluidRegistry.WATER, amount); }

    @Test public void fillSimulationDoesNotChangeTankOrDirtyChunk() {
        for (TileEntityBoiler tile : boilers()) {
            clearInvocations(world);
            int before = tile.myTank.getFluidAmount();
            assertEquals(1000, tile.fill(ForgeDirection.UP, water(1000), false));
            assertEquals(before, tile.myTank.getFluidAmount());
            verify(world, never()).markTileEntityChunkModified(anyInt(), anyInt(), anyInt(), any());
            assertEquals(1000, tile.fill(ForgeDirection.UP, water(1000), true));
            assertEquals(before + 1000, tile.myTank.getFluidAmount());
            verify(world, atLeastOnce()).markTileEntityChunkModified(0, 0, 0, tile);
        }
    }

    @Test public void emptyTanksRejectNonWaterAndInvalidAmounts() {
        for (TileEntityBoiler tile : boilers()) {
            tile.myTank.setFluid(null);
            assertEquals(0, tile.fill(ForgeDirection.UP, new FluidStack(FluidRegistry.LAVA, 1000), true));
            assertEquals(0, tile.fill(ForgeDirection.UP, water(-100), true));
            assertEquals(0, tile.fill(ForgeDirection.UP, water(0), true));
            assertEquals(0, tile.fill(ForgeDirection.UP, null, true));
            assertEquals(0, tile.myTank.getFluidAmount());
        }
    }

    @Test public void clientAndInvalidTilesCannotAcceptWater() {
        for (TileEntityBoiler tile : boilers()) {
            int before = tile.myTank.getFluidAmount();
            world.isRemote = true;
            assertEquals(0, tile.fill(ForgeDirection.UP, water(100), true));
            world.isRemote = false;
            tile.invalidate();
            assertEquals(0, tile.fill(ForgeDirection.UP, water(100), true));
            assertEquals(before, tile.myTank.getFluidAmount());
        }
    }

    @Test public void flashBoilerSavesFullEightyBucketTankAndLongBurnTimes() {
        FlashBoiler tile = place(new FlashBoiler());
        tile.myTank.setFluid(water(80000));
        tile.furnaceBurnTime = 100000;
        tile.currentItemBurnTime = 120000;
        tile.heat = 1200;
        NBTTagCompound tag = new NBTTagCompound();
        tile.writeToNBT(tag);
        FlashBoiler loaded = place(new FlashBoiler());
        loaded.readFromNBT(tag);
        assertEquals(80000, loaded.myTank.getFluidAmount());
        assertEquals(100000, loaded.furnaceBurnTime);
        assertEquals(120000, loaded.currentItemBurnTime);
        assertEquals(1200, loaded.heat);
    }

    @Test public void ordinaryBoilerSavesLongFuelBurnTimes() {
        Boiler tile = place(new Boiler());
        tile.furnaceBurnTime = 100000; tile.currentItemBurnTime = 120000;
        NBTTagCompound tag = new NBTTagCompound(); tile.writeToNBT(tag);
        Boiler loaded = place(new Boiler()); loaded.readFromNBT(tag);
        assertEquals(100000, loaded.furnaceBurnTime);
        assertEquals(120000, loaded.currentItemBurnTime);
    }

    @Test public void legacyShortTagsLoadAndMissingInventoryIsSafe() {
        for (TileEntityBoiler tile : boilers()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("water", (short) 9000); tag.setShort("BurnTime", (short) 700);
            tile.readFromNBT(tag);
            assertEquals(9000, tile.myTank.getFluidAmount());
            assertEquals(700, tile instanceof TileEntityFlashBoiler ? ((TileEntityFlashBoiler)tile).furnaceBurnTime : tile.furnaceBurnTime);
            assertNull(tile.getStackInSlot(0));
        }
    }

    @Test public void boilerProducesTenSteamForTwoWaterAndSavesBurnProgress() {
        Boiler tile = place(new Boiler()); tile.myTank.setFluid(water(100)); tile.furnaceBurnTime = 10;
        clearInvocations(world); tile.updateEntity();
        assertEquals(98, tile.myTank.getFluidAmount()); assertEquals(10, tile.getNetwork().getSteam());
        assertEquals(9, tile.furnaceBurnTime);
        verify(world, atLeastOnce()).markTileEntityChunkModified(0, 0, 0, tile);
    }

    @Test public void dryBoilerBurnCountdownStillMarksDirty() {
        Boiler tile = place(new Boiler()); tile.myTank.setFluid(null); tile.furnaceBurnTime = 10;
        clearInvocations(world); tile.updateEntity();
        assertEquals(9, tile.furnaceBurnTime); assertEquals(0, tile.getNetwork().getSteam());
        verify(world, atLeastOnce()).markTileEntityChunkModified(0, 0, 0, tile);
    }

    @Test public void flashBoilerProducesHundredSteamForTwentyWater() {
        FlashBoiler tile = formedFlash(); tile.updateEntity();
        tile.myTank.setFluid(water(100)); tile.furnaceBurnTime = 10;
        clearInvocations(world); tile.updateEntity();
        assertEquals(80, tile.myTank.getFluidAmount()); assertEquals(100, tile.getNetwork().getSteam());
        assertEquals(9, tile.furnaceBurnTime);
        verify(world, atLeastOnce()).markTileEntityChunkModified(0, 0, 0, tile);
    }

    @Test public void flashBoilerDoesNotConsumeWaterWithoutSteamNetwork() {
        FlashBoiler tile = formedFlash(); tile.updateEntity(); tile.setNetwork(null);
        tile.myTank.setFluid(water(100)); tile.furnaceBurnTime = 10;
        tile.updateEntity(); assertEquals(100, tile.myTank.getFluidAmount());
    }

    @Test public void flashBoilerCoolingPersistsEvenWhenNotBurning() {
        FlashBoiler tile = formedFlash(); tile.updateEntity(); tile.heat = 50;
        clearInvocations(world); tile.updateEntity(); assertEquals(40, tile.heat);
        verify(world, atLeastOnce()).markTileEntityChunkModified(0, 0, 0, tile);
    }

    @Test public void inventoryInsertionAndRemovalMarkOwningTileDirty() {
        for (TileEntityBoiler tile : boilers()) {
            clearInvocations(world); tile.setInventorySlotContents(0, new ItemStack(ITEM, 2));
            verify(world, atLeastOnce()).markTileEntityChunkModified(0, 0, 0, tile);
            clearInvocations(world); assertEquals(1, tile.decrStackSize(0, 1).stackSize);
            verify(world, atLeastOnce()).markTileEntityChunkModified(0, 0, 0, tile);
            clearInvocations(world); assertEquals(1, tile.getStackInSlotOnClosing(0).stackSize);
            verify(world, atLeastOnce()).markTileEntityChunkModified(0, 0, 0, tile);
        }
    }

    @Test public void flashSecondaryWithMissingOrUnloadedPrimaryIsSafe() {
        FlashBoiler tile = place(new FlashBoiler());
        when(world.getBlockMetadata(0, 0, 0)).thenReturn(2);
        when(world.blockExists(-1, 0, 0)).thenReturn(false);
        assertEquals(0, tile.fill(ForgeDirection.UP, water(100), true));
        tile.setInventorySlotContents(0, new ItemStack(ITEM));
        verify(world, never()).getBlock(-1, 0, 0);
        verify(world, never()).getTileEntity(-1, 0, 0);
    }
    @Test public void mutableContainerTransfersOnlyAcceptedWaterAndLeavesInputUntouched() {
        Boiler tile = place(new Boiler()); tile.myTank.setFluid(water(9500));
        ItemFluidContainer item = new ItemFluidContainer(0, 2000);
        ItemStack input = new ItemStack(item); item.fill(input, water(1500), true);
        ItemStack output = FluidHelper.fillTankFromItem(input, tile.myTank);
        assertNotSame(input, output);
        assertEquals(10000, tile.myTank.getFluidAmount());
        assertEquals(1000, item.getFluid(output).amount);
        assertEquals(1500, item.getFluid(input).amount);
    }

    @Test public void mutableContainerRetainsFluidWhenTankRejectsIt() {
        Boiler tile = place(new Boiler()); tile.myTank.setFluid(null);
        ItemFluidContainer item = new ItemFluidContainer(0, 2000);
        ItemStack input = new ItemStack(item); item.fill(input, new FluidStack(FluidRegistry.LAVA, 1000), true);
        assertSame(input, FluidHelper.fillTankFromItem(input, tile.myTank));
        assertEquals(1000, item.getFluid(input).amount);
        assertEquals(0, tile.myTank.getFluidAmount());
    }

    @Test public void stackedInventoryContainersRemainIntact() {
        Boiler tile = place(new Boiler());
        ItemFluidContainer item = new ItemFluidContainer(0, 2000);
        ItemStack input = new ItemStack(item, 2); item.fill(input, water(1000), true);
        assertSame(input, FluidHelper.fillTankFromItem(input, tile.myTank));
        assertEquals(2, input.stackSize); assertEquals(1000, item.getFluid(input).amount);
        assertEquals(1, tile.myTank.getFluidAmount());
    }

    @Test public void secondaryWaterAndInventoryMutationsDirtyPrimary() {
        Block oldBlock = SteamcraftBlocks.flashBoiler;
        try {
            SteamcraftBlocks.flashBoiler = mock(Block.class);
            when(world.getBlock(0, 0, 0)).thenReturn(SteamcraftBlocks.flashBoiler);
            FlashBoiler primary = place(new FlashBoiler()), secondary = place(new FlashBoiler());
            secondary.xCoord = 1;
            when(world.getBlockMetadata(1, 0, 0)).thenReturn(2);
            when(world.getTileEntity(0, 0, 0)).thenReturn(primary);
            clearInvocations(world);
            assertEquals(1000, secondary.fill(ForgeDirection.UP, water(1000), true));
            assertEquals(1001, primary.myTank.getFluidAmount());
            assertEquals(1, secondary.myTank.getFluidAmount());
            verify(world, atLeastOnce()).markTileEntityChunkModified(0, 0, 0, primary);
            clearInvocations(world);
            secondary.setInventorySlotContents(0, new ItemStack(ITEM));
            assertSame(primary.getStackInSlot(0), secondary.getStackInSlot(0));
            verify(world, atLeastOnce()).markTileEntityChunkModified(0, 0, 0, primary);
        } finally { SteamcraftBlocks.flashBoiler = oldBlock; }
    }

    @Test public void clientActivationDoesNotReadOrDrainHeldContainer() {
        Boiler tile = place(new Boiler()); when(world.getTileEntity(0, 0, 0)).thenReturn(tile);
        world.isRemote = true;
        EntityPlayer player = mock(EntityPlayer.class);
        assertTrue(new BlockBoiler(false).onBlockActivated(world, 0, 0, 0, player, 1, 0, 0, 0));
        verify(player, never()).getHeldItem();
        assertEquals(1, tile.myTank.getFluidAmount());
    }

    @Test public void emptyStacksAreNotFuel() {
        assertEquals(0, TileEntityBoiler.getItemBurnTime(new ItemStack(ITEM, 0)));
        assertEquals(0, TileEntityFlashBoiler.getItemBurnTime(new ItemStack(ITEM, 0)));
    }

}
