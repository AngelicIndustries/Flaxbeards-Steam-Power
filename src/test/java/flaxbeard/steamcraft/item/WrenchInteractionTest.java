package flaxbeard.steamcraft.item;

import flaxbeard.steamcraft.api.IWrenchable;
import flaxbeard.steamcraft.tile.TileEntitySteamPipe;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WrenchInteractionTest {
    private World world;
    private EntityPlayer player;
    private ItemWrench wrench;
    private ItemStack stack;
    private Block block;
    private TileEntity tile;

    @Before
    public void setUp() {
        world = mock(World.class);
        player = mock(EntityPlayer.class);
        wrench = spy(new ItemWrench());
        stack = new ItemStack(wrench);
        block = mock(Block.class, withSettings().extraInterfaces(IWrenchable.class));
        tile = mock(TileEntity.class, withSettings().extraInterfaces(IWrenchable.class));
        when(world.getBlock(0, 0, 0)).thenReturn(block);
        when(world.getTileEntity(0, 0, 0)).thenReturn(tile);
        when(world.canMineBlock(player, 0, 0, 0)).thenReturn(true);
        when(player.canPlayerEdit(0, 0, 0, 1, stack)).thenReturn(true);
    }

    private boolean use() {
        return wrench.onItemUseFirst(stack, player, world, 0, 0, 0, 1, 0, 0, 0);
    }

    private boolean callback(IWrenchable target) {
        return target.onWrench(stack, player, world, 0, 0, 0, 1, 0, 0, 0);
    }

    @Test
    public void tileSuccessIsReturnedEvenWhenBlockDeclines() {
        when(callback((IWrenchable) tile)).thenReturn(true);
        assertTrue(use());
        callback(verify((IWrenchable) block));
        callback(verify((IWrenchable) tile));
        verify(tile).markDirty();
        verify(world).playSoundEffect(0.5, 0.5, 0.5, "steamcraft:wrench", 2.0F, 0.9F);
    }

    @Test
    public void blockSuccessStillInvokesTileAndSavesItsChanges() {
        when(callback((IWrenchable) block)).thenReturn(true);
        assertTrue(use());
        callback(verify((IWrenchable) tile));
        verify(tile).markDirty();
    }

    @Test
    public void deniedEditsDoNotInvokeEitherCallback() {
        when(world.canMineBlock(player, 0, 0, 0)).thenReturn(false);
        assertFalse(use());
        when(world.canMineBlock(player, 0, 0, 0)).thenReturn(true);
        when(player.canPlayerEdit(0, 0, 0, 1, stack)).thenReturn(false);
        assertFalse(use());
        when(player.canPlayerEdit(0, 0, 0, 1, stack)).thenReturn(true);
        when(wrench.canWrench(player, 0, 0, 0)).thenReturn(false);
        assertFalse(use());
        callback(verify((IWrenchable) block, never()));
        callback(verify((IWrenchable) tile, never()));
    }

    @Test
    public void clientOnlyInvokesPipeTargetingAndKeepsVanillaInteractionPath() {
        world.isRemote = true;
        assertFalse(use());
        callback(verify((IWrenchable) block, never()));
        callback(verify((IWrenchable) tile, never()));
        TileEntitySteamPipe pipe = mock(TileEntitySteamPipe.class);
        when(world.getTileEntity(0, 0, 0)).thenReturn(pipe);
        when(callback(pipe)).thenReturn(true);
        assertFalse(use());
        callback(verify(pipe));
        callback(verify((IWrenchable) block, never()));
        when(player.isSneaking()).thenReturn(true);
        assertFalse(use());
        callback(verify(pipe));
        verify(pipe, never()).markDirty();
    }
}
