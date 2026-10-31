package flaxbeard.steamcraft.gui;

import flaxbeard.steamcraft.tile.TileEntitySteamHammer;
import net.minecraft.block.BlockAnvil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import java.util.Random;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HammerPickupTest {
    private final Item material = new Item();
    private final Item input = new Item().setMaxStackSize(64);
    private World world;
    private EntityPlayer player;
    private TileEntitySteamHammer hammer;
    private ContainerSteamAnvil container;
    private static class Anvil extends BlockAnvil {}

    @Before
    public void setUp() {
        world = mock(World.class);
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(new Anvil());
        player = mock(EntityPlayer.class);
        player.worldObj = world;
        player.inventory = new InventoryPlayer(player);
        player.capabilities = new PlayerCapabilities();
        Random random = mock(Random.class);
        when(random.nextFloat()).thenReturn(1.0F);
        when(player.getRNG()).thenReturn(random);
        hammer = new TileEntitySteamHammer();
        hammer.setWorldObj(world);
        hammer.setInventorySlotContents(0, new ItemStack(input, 8));
        container = new ContainerSteamAnvil(player.inventory, hammer, world, 0, 0, 0, player);
        container.updateItemName("Batch");
        hammer.progress = hammer.cost;
    }

    @Test
    public void rightClickTakesWholeCompletedBatchWithoutLosingHalf() {
        container.slotClick(2, 1, 0, player);
        assertEquals(8, player.inventory.getItemStack().stackSize);
        assertNull(hammer.getStackInSlot(0));
        assertNull(hammer.getStackInSlot(2));
        assertEquals(0, hammer.progress);
    }

    @Test
    public void dropKeyDropsWholeBatch() {
        container.slotClick(2, 0, 4, player);
        ArgumentCaptor<ItemStack> dropped = ArgumentCaptor.forClass(ItemStack.class);
        verify(player).dropPlayerItemWithRandomChoice(dropped.capture(), eq(true));
        assertEquals(8, dropped.getValue().stackSize);
        assertNull(hammer.getStackInSlot(0));
        assertNull(hammer.getStackInSlot(2));
    }

    @Test
    public void shiftClickWithPartialSpaceLeavesBatchAndInputsIntact() {
        for (int i = 0; i < 36; i++) player.inventory.mainInventory[i] = new ItemStack(material, 64);
        player.inventory.mainInventory[0] = hammer.getStackInSlot(2).copy();
        player.inventory.mainInventory[0].stackSize = 63;
        container.slotClick(2, 0, 1, player);
        assertEquals(63, player.inventory.mainInventory[0].stackSize);
        assertEquals(8, hammer.getStackInSlot(0).stackSize);
        assertEquals(8, hammer.getStackInSlot(2).stackSize);
        assertEquals(hammer.cost, hammer.progress);
    }

    @Test
    public void shiftClickCanCombineSpaceAcrossMultipleStacks() {
        for (int i = 0; i < 36; i++) player.inventory.mainInventory[i] = new ItemStack(material, 64);
        for (int i = 0; i < 2; i++) {
            player.inventory.mainInventory[i] = hammer.getStackInSlot(2).copy();
            player.inventory.mainInventory[i].stackSize = 60;
        }
        container.slotClick(2, 0, 1, player);
        assertEquals(64, player.inventory.mainInventory[0].stackSize);
        assertEquals(64, player.inventory.mainInventory[1].stackSize);
        assertNull(hammer.getStackInSlot(0));
        assertNull(hammer.getStackInSlot(2));
    }

    @Test
    public void unfinishedResultCannotBeTaken() {
        hammer.progress = 0;
        container.slotClick(2, 0, 0, player);
        container.slotClick(2, 0, 1, player);
        assertNull(player.inventory.getItemStack());
        assertNotNull(hammer.getStackInSlot(0));
        assertNotNull(hammer.getStackInSlot(2));
    }

    @Test
    public void doubleClickDoesNotPartiallyCollectResult() {
        ItemStack cursor = hammer.getStackInSlot(2).copy();
        cursor.stackSize = 63;
        player.inventory.setItemStack(cursor);
        container.slotClick(3, 0, 6, player);
        assertEquals(63, cursor.stackSize);
        assertEquals(8, hammer.getStackInSlot(0).stackSize);
        assertEquals(8, hammer.getStackInSlot(2).stackSize);
    }

    @Test
    public void changingRecipeResetsBothProgressAndPaidStroke() {
        hammer.progress = 1;
        hammer.hammerTicks = 200;
        container.getSlot(0).putStack(null);
        assertEquals(0, hammer.progress);
        assertEquals(0, hammer.hammerTicks);
        assertEquals(0, hammer.cost);
    }

    @Test
    public void rejectedRepairResetsPreviouslyCompletedWork() {
        hammer.hammerTicks = 200;
        container.getSlot(1).putStack(new ItemStack(material));
        assertNull(hammer.getStackInSlot(2));
        assertEquals(0, hammer.progress);
        assertEquals(0, hammer.hammerTicks);
    }

    @Test
    public void repeatedCalculationPreservesUnchangedWork() {
        hammer.progress = 3;
        hammer.hammerTicks = 200;
        container.updateRepairOutput();
        assertEquals(3, hammer.progress);
        assertEquals(200, hammer.hammerTicks);
    }

    @Test
    public void anotherViewUsesCurrentRepairMaterialCount() {
        Item tool = new Item() {
            @Override public boolean getIsRepairable(ItemStack a, ItemStack b) { return b.getItem() == material; }
        }.setMaxDamage(100).setMaxStackSize(1);
        hammer.itemName = "";
        container.getSlot(0).putStack(new ItemStack(tool, 1, 50));
        container.getSlot(1).putStack(new ItemStack(material, 4));
        ContainerSteamAnvil observer = new ContainerSteamAnvil(player.inventory, hammer, world, 0, 0, 0, player);
        container.getSlot(0).putStack(new ItemStack(tool, 1, 25));
        hammer.progress = hammer.cost;
        observer.slotClick(2, 0, 0, player);
        assertEquals(3, hammer.getStackInSlot(1).stackSize);
        assertEquals(0, player.inventory.getItemStack().getItemDamage());
    }
    @Test
    public void changingOnlyTheNameRestartsWork() {
        hammer.progress = 2;
        hammer.hammerTicks = 200;
        container.updateItemName("Different batch");
        assertEquals(0, hammer.progress);
        assertEquals(0, hammer.hammerTicks);
    }

    @Test
    public void directInputRemovalInvalidatesThePreviewBeforePickup() {
        hammer.decrStackSize(0, 1);
        assertNull(hammer.getStackInSlot(2));
        assertEquals(0, hammer.cost);
        assertEquals(0, hammer.progress);
        assertFalse(container.getSlot(2).canTakeStack(player));
    }

    @Test
    public void hopperCannotExtractAResultPreview() {
        ItemStack preview = hammer.getStackInSlot(2).copy();
        hammer.setInventorySlotContents(0, null);
        hammer.setInventorySlotContents(2, preview);
        net.minecraft.tileentity.TileEntityHopper hopper = new net.minecraft.tileentity.TileEntityHopper();
        hopper.setWorldObj(world);
        when(world.getTileEntity(0, 1, 0)).thenReturn(hammer);
        assertFalse(net.minecraft.tileentity.TileEntityHopper.func_145891_a(hopper));
        assertSame(preview, hammer.getStackInSlot(2));
        assertNull(hopper.getStackInSlot(0));
    }

}
