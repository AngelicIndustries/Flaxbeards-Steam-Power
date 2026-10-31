package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.Config;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MachineDropControlsTest {
    private boolean oldDrops;
    private boolean oldNear;
    private World world;
    private Block block;
    private TileEntityThumper thumper;

    @Before
    public void setup() {
        oldDrops = Config.dropItem;
        oldNear = Config.dropItemNearThumper;
        Config.dropItem = true;
        Config.dropItemNearThumper = false;
        world = mock(World.class);
        block = mock(Block.class);
        thumper = new TileEntityThumper();
        thumper.setWorldObj(world);
    }

    @After
    public void restore() {
        Config.dropItem = oldDrops;
        Config.dropItemNearThumper = oldNear;
    }

    @Test
    public void bonusSplitsStacksWithoutChangingRecipeOrTags() {
        ItemStack recipe = new ItemStack(new Item(), 40, 3).setStackDisplayName("Tagged output");
        List<ItemStack> drops = TileEntitySmasher.splitOutput(recipe, 3);
        assertEquals(2, drops.size());
        assertEquals(64, drops.get(0).stackSize);
        assertEquals(56, drops.get(1).stackSize);
        assertEquals(40, recipe.stackSize);
        for (ItemStack drop : drops) {
            assertEquals(3, drop.getItemDamage());
            assertEquals("Tagged output", drop.getDisplayName());
            assertNotSame(recipe.getTagCompound(), drop.getTagCompound());
        }
    }

    @Test
    public void itemStackLimitIsHonored() {
        ItemStack recipe = new ItemStack(new Item().setMaxStackSize(16), 8);
        List<ItemStack> drops = TileEntitySmasher.splitOutput(recipe, 3);
        assertEquals(2, drops.size());
        assertEquals(16, drops.get(0).stackSize);
        assertEquals(8, drops.get(1).stackSize);
    }

    @Test
    public void multiplierOneRetainsTheBaseYield() {
        assertEquals(4, TileEntitySmasher.splitOutput(new ItemStack(new Item(), 4), 1).get(0).stackSize);
    }

    @Test
    public void invalidMultiplierCannotDestroyOutputOrOverflow() {
        ItemStack recipe = new ItemStack(new Item(), 1);
        assertEquals(1, TileEntitySmasher.splitOutput(recipe, Integer.MIN_VALUE).get(0).stackSize);
        assertEquals(64, TileEntitySmasher.splitOutput(recipe, Integer.MAX_VALUE).get(0).stackSize);
    }

    @Test
    public void emptyRecipesDoNotSpawnItems() {
        assertTrue(TileEntitySmasher.splitOutput(null, 2).isEmpty());
        assertTrue(TileEntitySmasher.splitOutput(new ItemStack(new Item(), 0), 2).isEmpty());
        assertTrue(TileEntitySmasher.splitOutput(new ItemStack(new Item(), -1), 2).isEmpty());
    }

    @Test
    public void defaultThumperDropsUseOriginalBlockMethodAndPosition() {
        thumper.dropMinedBlock(block, 10, 20, 30, 7);
        verify(block).dropBlockAsItem(world, 10, 20, 30, 7, 0);
        verifyNoMoreInteractions(block);
    }

    @Test
    public void disabledDropsOverrideTheNearMachineOption() {
        Config.dropItem = false;
        Config.dropItemNearThumper = true;
        thumper.dropMinedBlock(block, 10, 20, 30, 7);
        verifyNoInteractions(block);
    }

    @Test
    public void clientAndSnapshotRestoreCannotDropItems() {
        for (boolean near : new boolean[]{false, true}) {
            Config.dropItemNearThumper = near;
            world.isRemote = true;
            thumper.dropMinedBlock(block, 10, 20, 30, 7);
            world.isRemote = false;
            world.restoringBlockSnapshots = true;
            thumper.dropMinedBlock(block, 10, 20, 30, 7);
            world.restoringBlockSnapshots = false;
        }
        verifyNoInteractions(block);
    }
}
