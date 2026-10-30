package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.api.CrucibleFormula;
import flaxbeard.steamcraft.api.CrucibleLiquid;
import flaxbeard.steamcraft.api.SteamcraftRegistry;
import flaxbeard.steamcraft.item.ItemIngotMold;
import flaxbeard.steamcraft.item.ItemNuggetMold;
import flaxbeard.steamcraft.item.ItemPlateMold;
import flaxbeard.steamcraft.block.BlockMold;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.MutablePair;
import org.junit.*;
import java.util.ArrayList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CastingTest {
    private static final Item METAL = new Item();
    private static final Item INGOT_MOLD = new ItemIngotMold();
    private ArrayList<CrucibleLiquid> savedLiquids;
    private World world;
    private TileEntityCrucible crucible;
    private TileEntityMold mold;
    private CrucibleLiquid copper;

    @BeforeClass
    public static void registerSaveTypes() throws Exception {
        // The public Forge registration path requires a running LaunchWrapper/FML loader.
        java.lang.reflect.Method register = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        register.invoke(Item.itemRegistry, 30000, "casting_test:metal", METAL);
        register.invoke(Item.itemRegistry, 30001, "casting_test:ingot_mold", INGOT_MOLD);
        TileEntity.addMapping(TileEntityCrucible.class, "casting_test:crucible");
        TileEntity.addMapping(TileEntityMold.class, "casting_test:mold");
    }

    @Before
    public void setUp() {
        savedLiquids = SteamcraftRegistry.liquids;
        SteamcraftRegistry.liquids = new ArrayList<>();
        world = mock(World.class);
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(Blocks.air);
        crucible = new TileEntityCrucible();
        crucible.setWorldObj(world);
        mold = new TileEntityMold();
        mold.setWorldObj(world);
        mold.open = false;
        when(world.getTileEntity(0, 0, 1)).thenReturn(mold);
        copper = liquid("copper", null);
    }

    @After
    public void restoreRegistry() {
        SteamcraftRegistry.liquids = savedLiquids;
    }

    private CrucibleLiquid liquid(String name, CrucibleFormula recipe) {
        CrucibleLiquid liquid = new CrucibleLiquid(name, new ItemStack(METAL),
            new ItemStack(METAL), new ItemStack(METAL), recipe, 0, 0, 0);
        SteamcraftRegistry.liquids.add(liquid);
        return liquid;
    }

    private void fill(CrucibleLiquid liquid, int amount) {
        crucible.contents.add(liquid);
        crucible.number.put(liquid, amount);
    }

    private void assertSaved(TileEntity tile) {
        verify(world, atLeastOnce()).markTileEntityChunkModified(0, 0, 0, tile);
    }

    @Test
    public void repeatedSaveLoadingReplacesContents() {
        fill(copper, 27);
        NBTTagCompound save = new NBTTagCompound();
        crucible.writeToNBT(save);
        crucible.readFromNBT(save);
        crucible.readFromNBT(save);
        assertEquals(27, crucible.getFill());
        assertEquals(1, crucible.contents.size());
    }

    @Test
    public void missingLiquidListLoadsAsEmpty() {
        fill(copper, 9);
        crucible.readFromNBT(new NBTTagCompound());
        assertEquals(0, crucible.getFill());
    }

    @Test
    public void packetIgnoresUnknownLiquids() {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagList liquids = new NBTTagList();
        NBTTagCompound unknown = new NBTTagCompound();
        unknown.setString("name", "removed_mod_metal");
        unknown.setShort("amount", (short) 9);
        liquids.appendTag(unknown);
        data.setTag("liquids", liquids);
        crucible.onDataPacket(null, new S35PacketUpdateTileEntity(0, 0, 0, 1, data));
        assertEquals(0, crucible.getFill());
        assertTrue(crucible.contents.isEmpty());
    }

    @Test
    public void meltingOneNuggetIsSavedWithoutComparatorChange() {
        ItemStack input = new ItemStack(METAL, 2);
        crucible.fillWith(input, 1, new MutablePair<>(copper, 1));
        crucible.updateEntity();
        assertEquals(1, input.stackSize);
        assertEquals(1, crucible.getFill());
        assertEquals(0, crucible.getComparatorOutput());
        assertSaved(crucible);
    }

    @Test
    public void meltingRespectsCapacityAndServerAuthority() {
        fill(copper, 89);
        ItemStack input = new ItemStack(METAL, 2);
        crucible.fillWith(input, 9, new MutablePair<>(copper, 9));
        assertEquals(89, crucible.getFill());
        assertEquals(2, input.stackSize);
        world.isRemote = true;
        crucible.fillWith(input, 1, new MutablePair<>(copper, 1));
        assertEquals(89, crucible.getFill());
        assertEquals(2, input.stackSize);
    }

    @Test
    public void alloyingConservesMetalAndSavesWithoutComparatorChange() {
        CrucibleLiquid zinc = liquid("zinc", null);
        CrucibleLiquid brass = liquid("brass", new CrucibleFormula(copper, 3, zinc, 1, 4));
        fill(copper, 3);
        fill(zinc, 1);
        crucible.updateEntity();
        assertEquals(4, crucible.getFill());
        assertEquals(Integer.valueOf(4), crucible.number.get(brass));
        assertFalse(crucible.contents.contains(copper));
        assertFalse(crucible.number.containsKey(copper));
        assertSaved(crucible);
    }

    @Test
    public void clientsDoNotAlloyIndependently() {
        CrucibleLiquid zinc = liquid("zinc", null);
        liquid("brass", new CrucibleFormula(copper, 3, zinc, 1, 4));
        fill(copper, 3);
        fill(zinc, 1);
        world.isRemote = true;
        crucible.updateEntity();
        assertEquals(Integer.valueOf(3), crucible.number.get(copper));
        assertEquals(Integer.valueOf(1), crucible.number.get(zinc));
    }

    @Test
    public void eachMoldConsumesItsExactCostOncePerTip() {
        Item[] molds = {new ItemIngotMold(), new ItemNuggetMold(), new ItemPlateMold()};
        int[] costs = {9, 1, 6};
        for (int i = 0; i < molds.length; i++) {
            crucible.contents.clear();
            crucible.number.clear();
            fill(copper, 27);
            mold.setInventorySlotContents(0, null);
            mold.mold[0] = new ItemStack(molds[i]);
            crucible.setTipping();
            for (int tick = 0; tick < 44; tick++) crucible.updateEntity();
            assertNull(mold.getStackInSlot(0));
            crucible.updateEntity();
            assertEquals(27 - costs[i], crucible.getFill());
            assertEquals(1, mold.getStackInSlot(0).stackSize);
            for (int tick = 0; tick < 95; tick++) crucible.updateEntity();
            assertEquals(27 - costs[i], crucible.getFill());
        }
        assertSaved(mold);
    }

    @Test
    public void openOccupiedAndUnderfilledMoldsDoNotConsumeMetal() {
        mold.mold[0] = new ItemStack(INGOT_MOLD);
        fill(copper, 8);
        crucible.setTipping();
        for (int tick = 0; tick < 45; tick++) crucible.updateEntity();
        assertEquals(8, crucible.getFill());
        crucible.number.put(copper, 27);
        mold.open = true;
        crucible.setTipping();
        for (int tick = 0; tick < 45; tick++) crucible.updateEntity();
        assertEquals(27, crucible.getFill());
        mold.open = false;
        mold.setInventorySlotContents(0, new ItemStack(METAL));
        crucible.setTipping();
        for (int tick = 0; tick < 45; tick++) crucible.updateEntity();
        assertEquals(27, crucible.getFill());
    }

    @Test
    public void extractionSavesAndEmptyExtractionIsSafe() {
        mold.setInventorySlotContents(0, new ItemStack(METAL));
        clearInvocations(world);
        assertEquals(1, mold.decrStackSize(0, 1).stackSize);
        assertNull(mold.decrStackSize(0, 1));
        assertNull(mold.getStackInSlot(0));
        assertSaved(mold);
        verify(world).markBlockForUpdate(0, 0, 0);
    }

    @Test
    public void emptyPacketsAndSavesClearMoldOutput() {
        mold.setInventorySlotContents(0, new ItemStack(METAL));
        mold.onDataPacket(null, new S35PacketUpdateTileEntity(0, 0, 0, 1, new NBTTagCompound()));
        assertNull(mold.getStackInSlot(0));
        mold.setInventorySlotContents(0, new ItemStack(METAL));
        mold.readFromNBT(new NBTTagCompound());
        assertNull(mold.getStackInSlot(0));
    }

    @Test
    public void castOutputSurvivesSaveAndReload() {
        mold.mold[0] = new ItemStack(INGOT_MOLD);
        mold.pour(copper);
        NBTTagCompound save = new NBTTagCompound();
        mold.writeToNBT(save);
        TileEntityMold restored = new TileEntityMold();
        restored.readFromNBT(save);
        assertSame(METAL, restored.getStackInSlot(0).getItem());
        assertEquals(1, restored.getStackInSlot(0).stackSize);
        assertFalse(restored.open);
        assertSame(INGOT_MOLD, restored.mold[0].getItem());
    }

    @Test
    public void regeneratedAlloyRemainsVisibleAfterBeingConsumed() {
        CrucibleLiquid zinc = liquid("zinc", null);
        CrucibleLiquid brass = liquid("brass", new CrucibleFormula(copper, 3, zinc, 1, 4));
        CrucibleLiquid catalyst = liquid("catalyst", null);
        CrucibleLiquid composite = liquid("composite", new CrucibleFormula(brass, 4, catalyst, 1, 5));
        fill(copper, 6);
        fill(zinc, 2);
        fill(catalyst, 1);
        crucible.updateEntity();
        assertEquals(Integer.valueOf(5), crucible.number.get(composite));
        crucible.updateEntity();
        assertEquals(9, crucible.getFill());
        assertTrue(crucible.contents.contains(brass));
        assertEquals(Integer.valueOf(4), crucible.number.get(brass));
    }

    @Test
    public void openingEjectsOnceAfterDelayAndSavesEmptyOutput() {
        mold = spy(mold);
        ItemStack output = new ItemStack(METAL);
        doNothing().when(mold).dropItem(any(ItemStack.class));
        mold.setInventorySlotContents(0, output);
        mold.open = true;
        mold.changeTicks = 20;
        clearInvocations(world);
        for (int tick = 0; tick < 10; tick++) mold.updateEntity();
        assertSame(output, mold.getStackInSlot(0));
        mold.updateEntity();
        assertNull(mold.getStackInSlot(0));
        for (int tick = 0; tick < 20; tick++) mold.updateEntity();
        verify(mold, times(1)).dropItem(output);
        assertSaved(mold);
        verify(world, times(1)).markBlockForUpdate(0, 0, 0);
    }

    @Test
    public void clientKeepsOutputUntilServerClearsIt() {
        mold = spy(mold);
        ItemStack output = new ItemStack(METAL);
        mold.setInventorySlotContents(0, output);
        mold.open = true;
        world.isRemote = true;
        mold.updateEntity();
        assertSame(output, mold.getStackInSlot(0));
        verify(mold, never()).dropItem(any(ItemStack.class));
    }

    @Test
    public void installingMoldCopiesOneItemAndSavesIt() {
        EntityPlayer player = mock(EntityPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.capabilities = new PlayerCapabilities();
        ItemStack held = new ItemStack(INGOT_MOLD, 2);
        player.inventory.setInventorySlotContents(0, held);
        when(player.getHeldItem()).thenReturn(held);
        when(world.getTileEntity(0, 0, 0)).thenReturn(mold);
        mold.open = true;
        new BlockMold().onBlockActivated(world, 0, 0, 0, player, 1, 0, 0, 0);
        assertEquals(1, held.stackSize);
        assertEquals(1, mold.mold[0].stackSize);
        assertNotSame(held, mold.mold[0]);
        assertSaved(mold);
    }

    @Test
    public void togglingMoldSavesStateAndClientWaitsForServer() {
        EntityPlayer player = mock(EntityPlayer.class);
        when(world.getTileEntity(0, 0, 0)).thenReturn(mold);
        BlockMold block = new BlockMold();
        block.onBlockActivated(world, 0, 0, 0, player, 1, 0, 0, 0);
        assertTrue(mold.open);
        assertEquals(20, mold.changeTicks);
        assertSaved(mold);
        world.isRemote = true;
        mold.changeTicks = 0;
        block.onBlockActivated(world, 0, 0, 0, player, 1, 0, 0, 0);
        assertTrue(mold.open);
    }
}
