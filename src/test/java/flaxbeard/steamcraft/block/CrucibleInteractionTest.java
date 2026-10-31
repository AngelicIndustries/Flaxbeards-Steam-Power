package flaxbeard.steamcraft.block;

import flaxbeard.steamcraft.Config;
import flaxbeard.steamcraft.api.CrucibleLiquid;
import flaxbeard.steamcraft.api.SteamcraftRegistry;
import flaxbeard.steamcraft.api.Tuple3;
import flaxbeard.steamcraft.tile.TileEntityCrucible;
import flaxbeard.steamcraft.tile.TileEntitySteamHeater;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.apache.commons.lang3.tuple.MutablePair;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import java.util.HashMap;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CrucibleInteractionTest {
    private final Item input = new Item().setHasSubtypes(true);
    private final Item output = new Item();
    private final CrucibleLiquid metal = new CrucibleLiquid("test", null, null, null, null, 0, 0, 0);
    private HashMap<Tuple3, MutablePair<Integer, ItemStack>> savedDunkRecipes;
    private HashMap<MutablePair<Item, Integer>, MutablePair<CrucibleLiquid, Integer>> savedLiquidRecipes;
    private World world;
    private EntityPlayer player;
    private TileEntityCrucible tile;
    private TileEntitySteamHeater heater;
    private BlockSteamcraftCrucible block;

    @Before
    public void setUp() {
        savedDunkRecipes = SteamcraftRegistry.dunkRecipes;
        savedLiquidRecipes = SteamcraftRegistry.liquidRecipes;
        SteamcraftRegistry.dunkRecipes = new HashMap<>();
        SteamcraftRegistry.liquidRecipes = new HashMap<>();
        world = mock(World.class);
        Block support = mock(Block.class);
        when(support.getMaterial()).thenReturn(Material.rock);
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(support);
        player = mock(EntityPlayer.class);
        player.worldObj = world;
        player.capabilities = new PlayerCapabilities();
        player.inventory = new InventoryPlayer(player);
        player.inventory.mainInventory[0] = new ItemStack(input, 2, 7);
        when(player.getHeldItem()).thenAnswer(call -> player.inventory.getCurrentItem());
        tile = new TileEntityCrucible();
        tile.setWorldObj(world);
        when(world.getTileEntity(0, 0, 0)).thenReturn(tile);
        heater = mock(TileEntitySteamHeater.class);
        when(heater.myDir()).thenReturn(ForgeDirection.UP);
        when(heater.getSteam()).thenReturn(Config.heaterConsumption + 100);
        when(world.getTileEntity(0, -1, 0)).thenReturn(heater);
        block = new BlockSteamcraftCrucible();
    }

    @After
    public void restoreRecipes() {
        SteamcraftRegistry.dunkRecipes = savedDunkRecipes;
        SteamcraftRegistry.liquidRecipes = savedLiquidRecipes;
    }

    private void fill(int amount) {
        if (!tile.contents.contains(metal)) tile.contents.add(metal);
        tile.number.put(metal, amount);
    }

    private ItemStack recipe(int damage, int cost) {
        ItemStack result = new ItemStack(output, 3);
        result.setStackDisplayName("Coated metal");
        SteamcraftRegistry.dunkRecipes.put(new Tuple3(input, damage, metal), MutablePair.of(cost, result));
        return result;
    }

    private void activate() {
        assertTrue(block.onBlockActivated(world, 0, 0, 0, player, 1, 0, 0, 0));
    }

    @Test
    public void exactDunkConsumesOnceCopiesOutputAndSavesEmptyCrucible() {
        ItemStack template = recipe(7, 9);
        fill(9);
        activate();
        assertEquals(1, player.getHeldItem().stackSize);
        ItemStack result = player.inventory.mainInventory[1];
        assertSame(output, result.getItem());
        assertEquals(3, result.stackSize);
        assertEquals("Coated metal", result.getDisplayName());
        assertNotSame(template.getTagCompound(), result.getTagCompound());
        assertEquals(3, template.stackSize);
        assertTrue(tile.contents.isEmpty());
        assertFalse(tile.number.containsKey(metal));
        assertTrue(tile.needsUpdate);
        verify(world).markTileEntityChunkModified(0, 0, 0, tile);
    }

    @Test
    public void wildcardDunkAcceptsAnyMetadataAndClearsLastInput() {
        recipe(-1, 9);
        fill(18);
        player.getHeldItem().stackSize = 1;
        activate();
        assertSame(output, player.getHeldItem().getItem());
        assertEquals(3, player.getHeldItem().stackSize);
        assertEquals(9, tile.getFill());
    }

    @Test
    public void exactRecipeTakesPrecedenceEvenWhenUnderfilled() {
        recipe(-1, 1);
        recipe(7, 18);
        fill(9);
        activate();
        assertEquals(2, player.getHeldItem().stackSize);
        assertEquals(9, tile.getFill());
        assertNull(player.inventory.mainInventory[1]);
    }

    @Test
    public void clientDoesNotDunkOrStartTipping() {
        recipe(7, 9);
        fill(18);
        world.isRemote = true;
        activate();
        assertEquals(2, player.getHeldItem().stackSize);
        assertEquals(18, tile.getFill());
        player.inventory.mainInventory[0] = null;
        activate();
        assertFalse(tile.isTipping());
        verify(world, never()).markTileEntityChunkModified(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    public void fullInventoryDropsOnlyTheRemainder() {
        recipe(7, 9);
        fill(9);
        for (int slot = 1; slot < player.inventory.mainInventory.length; slot++) {
            player.inventory.mainInventory[slot] = new ItemStack(input, 64, 7);
        }
        player.inventory.mainInventory[1] = new ItemStack(output, 63);
        player.inventory.mainInventory[1].setStackDisplayName("Coated metal");
        activate();
        assertEquals(64, player.inventory.mainInventory[1].stackSize);
        ArgumentCaptor<ItemStack> dropped = ArgumentCaptor.forClass(ItemStack.class);
        verify(player).entityDropItem(dropped.capture(), eq(0.0F));
        assertSame(output, dropped.getValue().getItem());
        assertEquals(2, dropped.getValue().stackSize);
        assertEquals(1, player.getHeldItem().stackSize);
    }

    @Test
    public void emptyInputCannotProduceOutput() {
        recipe(7, 9);
        fill(9);
        player.getHeldItem().stackSize = 0;
        activate();
        assertEquals(9, tile.getFill());
        assertNull(player.inventory.mainInventory[1]);
    }

    @Test
    public void missingTileIsSafeAndServerCanTip() {
        player.inventory.mainInventory[0] = null;
        activate();
        assertTrue(tile.isTipping());
        when(world.getTileEntity(0, 0, 0)).thenReturn(null);
        activate();
    }

    private EntityItem droppedInput() {
        EntityItem entity = mock(EntityItem.class);
        when(entity.getEntityItem()).thenReturn(new ItemStack(input, 2, 7));
        return entity;
    }

    private void collide(EntityItem entity) {
        block.onEntityCollidedWithBlock(world, 0, 0, 0, entity);
    }

    @Test
    public void invalidFullAndThrottledMeltsDoNotConsumeSteam() {
        EntityItem entity = droppedInput();
        collide(entity);
        SteamcraftRegistry.liquidRecipes.put(MutablePair.of(input, 7), MutablePair.of(metal, 9));
        fill(90);
        collide(entity);
        fill(9);
        tile.hasUpdated = false;
        collide(entity);
        assertEquals(2, entity.getEntityItem().stackSize);
        verify(heater, never()).decrSteam(anyInt());
    }

    @Test
    public void clientCollisionDoesNotConsumeSteamOrChangePickupDelay() {
        SteamcraftRegistry.liquidRecipes.put(MutablePair.of(input, 7), MutablePair.of(metal, 9));
        EntityItem entity = droppedInput();
        entity.delayBeforeCanPickup = 20;
        world.isRemote = true;
        collide(entity);
        assertEquals(20, entity.delayBeforeCanPickup);
        assertEquals(2, entity.getEntityItem().stackSize);
        verify(heater, never()).decrSteam(anyInt());
    }

    @Test
    public void unheatedCrucibleLeavesItemIntact() {
        SteamcraftRegistry.liquidRecipes.put(MutablePair.of(input, 7), MutablePair.of(metal, 9));
        when(heater.getSteam()).thenReturn(-1);
        EntityItem entity = droppedInput();
        collide(entity);
        assertEquals(2, entity.getEntityItem().stackSize);
        assertEquals(0, tile.getFill());
        verify(heater, never()).decrSteam(anyInt());
    }

    @Test
    public void meltingLastItemRemovesEntity() {
        SteamcraftRegistry.liquidRecipes.put(MutablePair.of(input, 7), MutablePair.of(metal, 9));
        EntityItem entity = droppedInput();
        entity.getEntityItem().stackSize = 1;
        collide(entity);
        assertEquals(9, tile.getFill());
        verify(entity).setDead();
        verify(entity, never()).setEntityItemStack(any());
        verify(heater).decrSteam(Config.heaterConsumption);
    }

    @Test
    public void successfulMeltChargesOnceAfterPickupDelayAndUsesWildcardRecipe() {
        SteamcraftRegistry.liquidRecipes.put(MutablePair.of(input, -1), MutablePair.of(metal, 9));
        EntityItem entity = droppedInput();
        entity.delayBeforeCanPickup = 20;
        collide(entity);
        assertEquals(2, entity.delayBeforeCanPickup);
        verify(heater, never()).decrSteam(anyInt());
        entity.delayBeforeCanPickup = 1;
        collide(entity);
        collide(entity);
        assertEquals(1, entity.getEntityItem().stackSize);
        assertEquals(9, tile.getFill());
        verify(heater).decrSteam(Config.heaterConsumption);
    }
}
