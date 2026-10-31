package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.Config;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HammerProcessingTest {
    private static final Item INPUT = new Item();
    private World world;
    private TestHammer hammer;
    private int consumption;

    public static class TestHammer extends TileEntitySteamHammer {
        int availableSteam;
        int charged;
        @Override public void refresh() {}
        @Override public int getSteamShare() { return availableSteam; }
        @Override public void decrSteam(int amount) { availableSteam -= amount; charged += amount; }
    }

    @BeforeClass
    public static void registerSaveTypes() throws Exception {
        java.lang.reflect.Method register = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        register.invoke(Item.itemRegistry, 30010, "hammer_test:input", INPUT);
        TileEntity.addMapping(TestHammer.class, "hammer_test:processing");
    }

    @Before
    public void setUp() {
        consumption = Config.hammerConsumption;
        Config.hammerConsumption = 10;
        world = mock(World.class);
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(mock(Block.class));
        hammer = new TestHammer();
        hammer.setWorldObj(world);
        hammer.setInventorySlotContents(0, new ItemStack(INPUT));
        hammer.setInventorySlotContents(2, new ItemStack(INPUT).setStackDisplayName("Forged"));
        hammer.cost = 2;
        clearInvocations(world);
    }

    @After public void restoreConfig() { Config.hammerConsumption = consumption; }
    private void ticks(int count) { for (int i = 0; i < count; i++) hammer.updateEntity(); }

    @Test
    public void eachFullStrokeCostsSteamAndNoSteamPausesAtBoundary() {
        hammer.availableSteam = 10;
        ticks(72);
        assertEquals(1, hammer.progress);
        assertEquals(0, hammer.hammerTicks);
        assertEquals(10, hammer.charged);
        ticks(100);
        assertEquals(1, hammer.progress);
        hammer.availableSteam = 10;
        ticks(72);
        assertEquals(2, hammer.progress);
        assertEquals(20, hammer.charged);
        ticks(100);
        assertEquals(20, hammer.charged);
    }

    @Test
    public void progressAndPhaseChangesMarkChunkForSaving() {
        hammer.availableSteam = 20;
        ticks(10);
        clearInvocations(world);
        ticks(1);
        verify(world).markTileEntityChunkModified(0, 0, 0, hammer);
    }

    @Test
    public void paidStrokeSurvivesReloadAndResumesWorkingWithoutAnotherCharge() {
        hammer.availableSteam = 10;
        ticks(30);
        NBTTagCompound save = new NBTTagCompound();
        hammer.writeToNBT(save);
        TestHammer restored = new TestHammer();
        restored.readFromNBT(save);
        restored.setWorldObj(world);
        hammer = restored;
        ticks(1);
        NBTTagCompound packet = ((S35PacketUpdateTileEntity) hammer.getDescriptionPacket()).func_148857_g();
        assertTrue(packet.getBoolean("isWorking"));
        ticks(41);
        assertEquals(1, hammer.progress);
        assertEquals(0, hammer.charged);
        assertEquals(0, hammer.hammerTicks);
    }

    @Test
    public void stoppedClientKeepsAuthoritativeCompletedOrPausedProgress() {
        world.isRemote = true;
        for (int progress : new int[] {1, 2}) {
            NBTTagCompound data = new NBTTagCompound();
            data.setInteger("cost", 2);
            data.setInteger("progress", progress);
            hammer.onDataPacket(null, new S35PacketUpdateTileEntity(0, 0, 0, 1, data));
            ticks(3);
            assertEquals(progress, hammer.progress);
        }
    }

    @Test
    public void inventoryChangesAreSavedAndEmptyNbtIsSafe() {
        hammer.decrStackSize(0, 1);
        verify(world).markTileEntityChunkModified(0, 0, 0, hammer);
        clearInvocations(world);
        hammer.setInventorySlotContents(0, new ItemStack(INPUT));
        verify(world).markTileEntityChunkModified(0, 0, 0, hammer);
        hammer.readFromNBT(new NBTTagCompound());
        assertNull(hammer.getStackInSlot(0));
    }
}
