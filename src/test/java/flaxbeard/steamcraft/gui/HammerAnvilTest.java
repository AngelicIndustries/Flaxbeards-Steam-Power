package flaxbeard.steamcraft.gui;

import flaxbeard.steamcraft.block.BlockSteamHammer;
import flaxbeard.steamcraft.tile.TileEntitySteamHammer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAnvil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.junit.*;
import java.util.Random;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HammerAnvilTest {
    private World world;
    private EntityPlayer player;
    private TileEntitySteamHammer hammer;
    private BlockAnvil anvil;
    private ContainerSteamAnvil container;

    private static class ReplacementAnvil extends BlockAnvil {}

    @Before
    public void setUp() {
        world = mock(World.class);
        player = mock(EntityPlayer.class);
        player.inventory = new InventoryPlayer(player);
        player.capabilities = new PlayerCapabilities();
        when(player.getRNG()).thenReturn(mock(Random.class));
        when(player.getDistanceSq(anyDouble(), anyDouble(), anyDouble())).thenReturn(16.0);
        anvil = new ReplacementAnvil();
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(anvil);
        hammer = new TileEntitySteamHammer();
        hammer.setWorldObj(world);
        when(world.getTileEntity(0, 1, 0)).thenReturn(hammer);
        container = new ContainerSteamAnvil(player.inventory, hammer, world, 0, 0, 0, player);
    }

    @Test
    public void replacementAnvilAllowsOpeningAndUsingHammer() {
        assertTrue(new BlockSteamHammer().onBlockActivated(world, 0, 1, 0, player, 1, 0, 0, 0));
        verify(player).openGui(any(), eq(3), eq(world), eq(0), eq(1), eq(0));
        assertTrue(container.canInteractWith(player));
    }

    @Test
    public void missingAnvilAndDistantPlayerCannotUseHammer() {
        when(player.getDistanceSq(anyDouble(), anyDouble(), anyDouble())).thenReturn(65.0);
        assertFalse(container.canInteractWith(player));
        when(player.getDistanceSq(anyDouble(), anyDouble(), anyDouble())).thenReturn(16.0);
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(mock(Block.class));
        assertFalse(container.canInteractWith(player));
        assertFalse(new BlockSteamHammer().onBlockActivated(world, 0, 1, 0, player, 1, 0, 0, 0));
    }

    @Test
    public void wearPreservesReplacementBlockAndOrientation() {
        when(world.getBlockMetadata(0, 0, 0)).thenReturn(1);
        container.getSlot(2).onPickupFromSlot(player, new ItemStack(new Item()));
        verify(world).setBlockMetadataWithNotify(0, 0, 0, 5, 2);
        verify(world, never()).setBlock(anyInt(), anyInt(), anyInt(), any(Block.class), anyInt(), anyInt());
    }

    @Test
    public void finalWearBreaksReplacementAnvil() {
        when(world.getBlockMetadata(0, 0, 0)).thenReturn(9);
        container.getSlot(2).onPickupFromSlot(player, new ItemStack(new Item()));
        verify(world).setBlockToAir(0, 0, 0);
    }
}
