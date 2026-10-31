package flaxbeard.steamcraft.gui;

import flaxbeard.steamcraft.SteamcraftBlocks;
import flaxbeard.steamcraft.test.ForgeFluidTestSupport;
import flaxbeard.steamcraft.tile.TileEntityBoiler;
import flaxbeard.steamcraft.tile.TileEntityFlashBoiler;
import net.minecraft.block.Block;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.ICrafting;
import net.minecraft.item.*;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BoilerGuiTest {
    private static final Item FUEL = new Item(), WATER = new Item(), OTHER = new Item();
    private World world;
    private EntityPlayer player;
    private Boiler boiler;
    private ContainerBoiler container;
    public static class Boiler extends TileEntityBoiler {
        @Override public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return stack != null && stack.getItem() == (slot == 0 ? FUEL : WATER);
        }
    }
    @BeforeClass public static void bootstrap() throws Exception { ForgeFluidTestSupport.bootstrap(); }
    @Before public void setUp() {
        world = mock(World.class);
        when(world.blockExists(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(mock(Block.class));
        player = player(world);
        boiler = new Boiler(); boiler.setWorldObj(world);
        when(world.getTileEntity(0, 0, 0)).thenReturn(boiler);
        container = new ContainerBoiler(player.inventory, boiler);
    }
    private EntityPlayer player(World w) {
        EntityPlayer p = mock(EntityPlayer.class); p.worldObj = w;
        p.inventory = new InventoryPlayer(p); p.capabilities = new PlayerCapabilities(); return p;
    }
    private ICrafting wire(ContainerBoiler target) {
        ICrafting listener = mock(ICrafting.class);
        // Vanilla window properties encode every value as a signed short on the wire.
        doAnswer(i -> { target.updateProgressBar(i.getArgument(1), (short)(int)i.getArgument(2)); return null; })
            .when(listener).sendProgressBarUpdate(any(), anyInt(), anyInt());
        return listener;
    }
    private Boiler clientBoiler() {
        World client = mock(World.class); client.isRemote = true;
        Boiler b = new Boiler(); b.setWorldObj(client); return b;
    }

    @Test public void openingSendsFullInitialWaterAndFuelValues() {
        boiler.myTank.setFluid(new FluidStack(FluidRegistry.WATER, 80000));
        boiler.furnaceBurnTime = 100000; boiler.currentItemBurnTime = 120000;
        Boiler client = clientBoiler();
        container.addCraftingToCrafters(wire(new ContainerBoiler(player(world).inventory, client)));
        assertEquals(80000, client.myTank.getFluidAmount());
        assertEquals(100000, client.furnaceBurnTime);
        assertEquals(120000, client.currentItemBurnTime);
    }

    @Test public void twoViewersReceiveLargeValuesAndClearedValues() {
        Boiler a = clientBoiler(), b = clientBoiler();
        container.addCraftingToCrafters(wire(new ContainerBoiler(player(world).inventory, a)));
        ContainerBoiler second = new ContainerBoiler(player(world).inventory, boiler);
        second.addCraftingToCrafters(wire(new ContainerBoiler(player(world).inventory, b)));
        for (int value : new int[]{32767,32768,65535,65536,80000,0}) {
            boiler.myTank.setFluid(new FluidStack(FluidRegistry.WATER,value));
            boiler.furnaceBurnTime = value;
            container.detectAndSendChanges(); second.detectAndSendChanges();
            assertEquals(value,a.myTank.getFluidAmount()); assertEquals(value,b.myTank.getFluidAmount());
            assertEquals(value,a.furnaceBurnTime); assertEquals(value,b.furnaceBurnTime);
        }
    }

    @Test public void unchangedPropertiesDoNotResend() {
        ICrafting listener = mock(ICrafting.class); container.addCraftingToCrafters(listener);
        container.detectAndSendChanges(); clearInvocations(listener); container.detectAndSendChanges();
        verify(listener,never()).sendProgressBarUpdate(any(),anyInt(),anyInt());
    }

    @Test public void manualSlotsRejectWrongInputsAndWaterSlotHoldsOne() {
        assertFalse(container.getSlot(0).isItemValid(new ItemStack(OTHER)));
        assertFalse(container.getSlot(1).isItemValid(new ItemStack(WATER)));
        assertTrue(container.getSlot(0).isItemValid(new ItemStack(WATER)));
        assertTrue(container.getSlot(1).isItemValid(new ItemStack(FUEL)));
        assertEquals(1,container.getSlot(0).getSlotStackLimit());
    }

    @Test public void shiftClickFirstPlayerSlotCanInsertFuel() {
        player.inventory.mainInventory[9] = new ItemStack(FUEL, 8);
        assertNotNull(container.transferStackInSlot(player,2));
        assertNull(player.inventory.mainInventory[9]); assertEquals(8,boiler.getStackInSlot(0).stackSize);
    }

    @Test public void shiftClickWaterMovesOnlyOneAndDoesNotMergeIntoOccupiedSlot() {
        player.inventory.mainInventory[9] = new ItemStack(WATER, 8);
        assertNotNull(container.transferStackInSlot(player,2));
        assertEquals(7,player.inventory.mainInventory[9].stackSize);
        assertEquals(1,boiler.getStackInSlot(1).stackSize);
        assertNull(container.transferStackInSlot(player,2));
        assertEquals(7,player.inventory.mainInventory[9].stackSize);
    }

    @Test public void hotbarAndMainInventoryRangesIncludeBothEnds() {
        for (int slot : new int[]{2,28,29,37}) {
            for (int i=0;i<36;i++) player.inventory.mainInventory[i]=null;
            container.getSlot(slot).putStack(new ItemStack(OTHER, 4));
            assertNotNull(container.transferStackInSlot(player,slot));
            assertFalse(container.getSlot(slot).getHasStack());
            assertEquals(4,player.inventory.mainInventory[slot < 29 ? 0 : 9].stackSize);
        }
    }

    @Test public void machineExtractionCanUseFirstPlayerSlotWhenItIsOnlySpace() {
        for(int i=0;i<36;i++)player.inventory.mainInventory[i]=new ItemStack(OTHER,64);
        player.inventory.mainInventory[9]=null; boiler.setInventorySlotContents(0,new ItemStack(FUEL,5));
        assertNotNull(container.transferStackInSlot(player,1));
        assertNull(boiler.getStackInSlot(0)); assertEquals(5,player.inventory.mainInventory[9].stackSize);
    }

    @Test public void fullInventoryLeavesMachineStackIntact() {
        for(int i=0;i<36;i++)player.inventory.mainInventory[i]=new ItemStack(OTHER,64);
        boiler.setInventorySlotContents(0,new ItemStack(FUEL,5));
        assertNull(container.transferStackInSlot(player,1)); assertEquals(5,boiler.getStackInSlot(0).stackSize);
    }

    @Test public void invalidSlotIndicesAreIgnored() {
        assertNull(container.transferStackInSlot(player,-1)); assertNull(container.transferStackInSlot(player,38));
    }

    @Test public void removedBoilerCannotBeUsedOrShiftClicked() {
        boiler.setInventorySlotContents(0,new ItemStack(FUEL,5));
        when(world.getTileEntity(0,0,0)).thenReturn(null);
        assertFalse(container.canInteractWith(player));
        assertNull(container.transferStackInSlot(player,1)); assertEquals(5,boiler.getStackInSlot(0).stackSize);
    }

    @Test public void flashBoilerFuelFieldsAreTheSameFieldsSeenThroughBaseType() {
        TileEntityFlashBoiler flash = new TileEntityFlashBoiler();
        flash.furnaceBurnTime=100000; flash.currentItemBurnTime=120000; flash.furnaceCookTime=9;
        TileEntityBoiler base=flash;
        assertEquals(100000,base.furnaceBurnTime); assertEquals(120000,base.currentItemBurnTime); assertEquals(9,base.furnaceCookTime);
    }

    @Test public void secondaryScreenUsesPrimaryInventoryAndClosesWhenStructureChanges() {
        Block previous=SteamcraftBlocks.flashBoiler;
        try {
            SteamcraftBlocks.flashBoiler=mock(Block.class);
            TileEntityFlashBoiler primary=new TileEntityFlashBoiler(), secondary=new TileEntityFlashBoiler();
            primary.setWorldObj(world); secondary.setWorldObj(world); secondary.xCoord=1;
            when(world.getBlock(0,0,0)).thenReturn(SteamcraftBlocks.flashBoiler);
            when(world.getBlockMetadata(0,0,0)).thenReturn(1); when(world.getBlockMetadata(1,0,0)).thenReturn(2);
            when(world.getTileEntity(0,0,0)).thenReturn(primary); when(world.getTileEntity(1,0,0)).thenReturn(secondary);
            ContainerBoiler c=new ContainerBoiler(player.inventory,secondary);
            assertSame(primary,c.getSlot(0).inventory);
            assertTrue(c.canInteractWith(player));
            when(world.getBlockMetadata(1,0,0)).thenReturn(0);
            assertFalse(c.canInteractWith(player));
            c.detectAndSendChanges();
            assertNull(c.transferStackInSlot(player,0));
        } finally { SteamcraftBlocks.flashBoiler=previous; }
    }
    @Test public void progressUpdatesApplyOnlyAfterBothHalvesAndIgnoreUnknownIds() {
        Boiler client = clientBoiler(); ContainerBoiler c = new ContainerBoiler(player(world).inventory,client);
        client.furnaceBurnTime=12;
        c.updateProgressBar(1,(short)(100000 & 0xffff));
        assertEquals(12,client.furnaceBurnTime);
        c.updateProgressBar(6,(short)(100000 >>> 16));
        assertEquals(100000,client.furnaceBurnTime);
        c.updateProgressBar(-1,10);c.updateProgressBar(10,10);
        assertEquals(100000,client.furnaceBurnTime);
    }

    @Test public void largeFuelValuesRenderWithoutOverflowOrChangingState() {
        for(TileEntityBoiler tile : new TileEntityBoiler[]{boiler,new TileEntityFlashBoiler()}) {
            tile.setWorldObj(world);when(world.getBlockMetadata(0,0,0)).thenReturn(1);
            tile.furnaceBurnTime=Integer.MAX_VALUE;tile.currentItemBurnTime=Integer.MAX_VALUE;
            assertEquals(14,tile.getBurnTimeRemainingScaled(14));
            tile.currentItemBurnTime=0;
            assertEquals(0,tile.getBurnTimeRemainingScaled(14));assertEquals(0,tile.currentItemBurnTime);
        }
    }

    @Test public void unloadedOrDistantBoilerCannotBeUsed() {
        when(player.getDistanceSq(anyDouble(),anyDouble(),anyDouble())).thenReturn(65D);
        assertFalse(container.canInteractWith(player));
        when(world.blockExists(0,0,0)).thenReturn(false);clearInvocations(world);
        assertFalse(container.canInteractWith(player));container.detectAndSendChanges();
        verify(world,never()).getTileEntity(0,0,0);
    }

}
