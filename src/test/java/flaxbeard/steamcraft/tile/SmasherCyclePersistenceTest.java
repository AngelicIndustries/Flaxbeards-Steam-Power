package flaxbeard.steamcraft.tile;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SmasherCyclePersistenceTest {
    @org.junit.BeforeClass
    public static void registerTile() {
        net.minecraft.tileentity.TileEntity.addMapping(TileEntitySmasher.class, "smasher_cycle:test");
    }

    private static class Smasher extends TileEntitySmasher {
        @Override public void refresh() {}
    }

    private void flag(TileEntitySmasher tile, String name, boolean value) throws Exception {
        Field field = TileEntitySmasher.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(tile, value);
    }

    private boolean flag(TileEntitySmasher tile, String name) throws Exception {
        Field field = TileEntitySmasher.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(tile);
    }

    private TileEntitySmasher roundTrip(TileEntitySmasher tile) {
        NBTTagCompound tag = new NBTTagCompound();
        tile.writeToNBT(tag);
        TileEntitySmasher restored = new TileEntitySmasher();
        restored.readFromNBT(tag);
        return restored;
    }

    @Test
    public void paidCycleStateSurvivesSaveAndLoad() throws Exception {
        for (String name : new String[]{"isActive", "isBreaking", "shouldStop", "running", "smashNextRound"}) {
            TileEntitySmasher tile = new TileEntitySmasher();
            flag(tile, name, true);
            assertTrue(name, flag(roundTrip(tile), name));
        }
    }

    @Test
    public void legacySpinupResumesAsPaidWork() throws Exception {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("spinup", 20);
        tag.setTag("Items", new NBTTagList());
        TileEntitySmasher tile = new TileEntitySmasher();
        tile.readFromNBT(tag);
        assertTrue(flag(tile, "isActive"));
        assertTrue(flag(tile, "isBreaking"));
        assertTrue(flag(tile, "running"));
        assertFalse(flag(tile, "smashNextRound"));
    }

    @Test
    public void legacyRetractionDoesNotRestartBreaking() throws Exception {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setFloat("extendedLength", 0.25F);
        tag.setInteger("extendedTicks", 14);
        tag.setTag("Items", new NBTTagList());
        TileEntitySmasher tile = new TileEntitySmasher();
        tile.readFromNBT(tag);
        assertTrue(flag(tile, "isActive"));
        assertFalse(flag(tile, "isBreaking"));
    }

    @Test
    public void missingOrWrongTypeItemListLoadsEmpty() {
        for (boolean wrongType : new boolean[]{false, true}) {
            NBTTagCompound tag = new NBTTagCompound();
            if (wrongType) tag.setInteger("Items", 3);
            TileEntitySmasher tile = new TileEntitySmasher();
            tile.readFromNBT(tag);
            assertTrue(tile.smooshedStack.isEmpty());
        }
    }

    @Test
    public void missingItemEntriesDoNotBecomeNullPendingDrops() {
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagCompound());
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("Items", list);
        TileEntitySmasher tile = new TileEntitySmasher();
        tile.readFromNBT(tag);
        assertTrue(tile.smooshedStack.isEmpty());
    }

    @Test
    public void invalidPendingEntriesDoNotBreakSaving() {
        TileEntitySmasher tile = new TileEntitySmasher();
        tile.smooshedStack = new ArrayList<>();
        tile.smooshedStack.add(null);
        assertTrue(roundTrip(tile).smooshedStack.isEmpty());
    }

    @Test
    public void retractionProgressMarksTheTileForSaving() throws Exception {
        World world = mock(World.class);
        when(world.blockExists(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockMetadata(anyInt(), anyInt(), anyInt())).thenReturn(4);
        when(world.isAirBlock(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(mock(Block.class));
        Smasher tile = new Smasher();
        tile.setWorldObj(world);
        tile.extendedLength = 0.25F;
        tile.extendedTicks = 10;
        flag(tile, "isActive", true);
        tile.updateEntity();
        assertTrue(tile.extendedLength < 0.25F);
        verify(world).markTileEntityChunkModified(0, 0, 0, tile);
    }
}
