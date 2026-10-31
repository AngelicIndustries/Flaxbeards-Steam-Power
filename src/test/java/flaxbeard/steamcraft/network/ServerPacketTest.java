package flaxbeard.steamcraft.network;

import cpw.mods.fml.common.eventhandler.*;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import flaxbeard.steamcraft.api.steamnet.SteamNetwork;
import flaxbeard.steamcraft.gui.ContainerSteamAnvil;
import flaxbeard.steamcraft.item.ItemWrench;
import flaxbeard.steamcraft.tile.TileEntitySteamHammer;
import flaxbeard.steamcraft.tile.TileEntitySteamPipe;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ServerPacketTest {
    private static final Block CAMO_BLOCK = new Block(Material.rock) {};
    private static final Item CAMO_ITEM = new ItemBlock(CAMO_BLOCK);

    @BeforeClass
    public static void registerBlock() throws Exception {
        java.lang.reflect.Method raw = Item.itemRegistry.getClass()
            .getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);
        raw.setAccessible(true);
        raw.invoke(Item.itemRegistry,3000,"packet_test:camo",CAMO_ITEM);
        raw.invoke(Block.blockRegistry,3000,"packet_test:camo",CAMO_BLOCK);
    }

    private EntityPlayerMP player;
    private World world;
    private TileEntitySteamPipe pipe;
    private ContainerSteamAnvil anvil;
    private TileEntitySteamHammer hammer;
    private IEventListener protection;
    private int busId;

    @Before
    public void setUp() throws Exception {
        ServerPacketDispatcher.INSTANCE.clear();
        player = mock(EntityPlayerMP.class);
        world = mock(World.class);
        world.playerEntities = new ArrayList<>();
        world.playerEntities.add(player);
        player.worldObj = world;
        player.capabilities = new PlayerCapabilities();
        player.inventory = new InventoryPlayer(player);
        player.inventoryContainer = mock(Container.class);
        Field manager = EntityPlayerMP.class.getDeclaredField("theItemInWorldManager");
        manager.setAccessible(true);
        manager.set(player,mock(ItemInWorldManager.class));
        when(player.theItemInWorldManager.getBlockReachDistance()).thenReturn(5.0D);
        when(player.getHeldItem()).thenAnswer(invocation -> player.inventory.getCurrentItem());
        when(world.blockExists(1, 64, 1)).thenReturn(true);
        when(world.canMineBlock(player, 1, 64, 1)).thenReturn(true);
        when(player.canPlayerEdit(eq(1), eq(64), eq(1), anyInt(), any())).thenReturn(true);
        pipe = mock(TileEntitySteamPipe.class);
        when(pipe.getNetwork()).thenReturn(mock(SteamNetwork.class));
        when(world.getTileEntity(1, 64, 1)).thenReturn(pipe);
        hammer = mock(TileEntitySteamHammer.class);
        anvil = mock(ContainerSteamAnvil.class);
        anvil.hammer = hammer;
        when(anvil.canInteractWith(player)).thenReturn(true);
    }

    @After
    public void cleanup() {
        ServerPacketDispatcher.INSTANCE.clear();
        if (protection != null) ListenerList.unregisterAll(busId, protection);
    }

    private void tick() {
        ServerPacketDispatcher.INSTANCE.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
    }

    private void wrench() {
        player.inventory.setInventorySlotContents(0, new ItemStack(new ItemWrench()));
    }

    private void connect(int side) {
        ConnectPacketHandler.handle(new ConnectPacket(1,64,1,side),player);
    }

    private void openAnvil() {
        player.openContainer = anvil;
        when(world.getTileEntity(1,64,1)).thenReturn(hammer);
    }

    private void rename(String name) {
        ItemNamePacketHandler.handle(new ItemNamePacket(1,64,1,name),player);
    }

    private Block camouflage() {
        Block block = CAMO_BLOCK;
        player.inventory.setInventorySlotContents(0, new ItemStack(CAMO_ITEM));
        when(player.isSneaking()).thenReturn(true);
        return block;
    }

    private void camo() {
        CamoPacketHandler.handle(new CamoPacket(1,64,1),player);
    }

    private void denyInteraction() throws Exception {
        Field field = EventBus.class.getDeclaredField("busID");
        field.setAccessible(true);
        busId = field.getInt(MinecraftForge.EVENT_BUS);
        // Forge normally adds cancellation support through ASM; DENY works without LaunchWrapper.
        protection = event -> ((PlayerInteractEvent) event).useBlock = Event.Result.DENY;
        new PlayerInteractEvent(player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK,1,64,1,0,world)
            .getListenerList().register(busId,EventPriority.HIGHEST,protection);
    }

    @Test
    public void networkHandlerDefersMutationUntilServerTick() {
        wrench();
        MessageContext context = mock(MessageContext.class);
        NetHandlerPlayServer handler = mock(NetHandlerPlayServer.class);
        handler.playerEntity = player;
        when(context.getServerHandler()).thenReturn(handler);
        new ConnectPacketHandler().onMessage(new ConnectPacket(1,64,1,2),context);
        verifyNoInteractions(pipe);
        ServerPacketDispatcher.INSTANCE.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        verifyNoInteractions(pipe);
        tick();
        verify(pipe).connectDisconnect(world,1,64,1,2);
        verify(pipe).markDirty();
    }

    @Test
    public void queuedPacketsRecheckChangedHeldItem() {
        wrench();
        ServerPacketDispatcher.INSTANCE.enqueue(player, () -> connect(2));
        player.inventory.setInventorySlotContents(0,null);
        tick();
        verifyNoInteractions(pipe);
    }

    @Test
    public void disconnectedAndDimensionChangedPlayersLoseQueuedActions() {
        Runnable action = mock(Runnable.class);
        ServerPacketDispatcher.INSTANCE.enqueue(player,action);
        world.playerEntities.clear();
        tick();
        world.playerEntities.add(player);
        ServerPacketDispatcher.INSTANCE.enqueue(player,action);
        player.dimension=1;
        tick();
        verifyNoInteractions(action);
    }

    @Test
    public void packetBacklogIsBoundedPerPlayerAndClearedOnShutdown() {
        AtomicInteger count = new AtomicInteger();
        for(int i=0;i<1000;i++) ServerPacketDispatcher.INSTANCE.enqueue(player,count::incrementAndGet);
        tick();
        assertEquals(32,count.get());
        ServerPacketDispatcher.INSTANCE.enqueue(player,count::incrementAndGet);
        ServerPacketDispatcher.INSTANCE.clear();
        tick();
        assertEquals(32,count.get());
    }

    @Test
    public void distantOrUnloadedCoordinatesNeverLookUpTiles() {
        wrench();
        when(player.getDistanceSq(anyDouble(),anyDouble(),anyDouble())).thenReturn(10000.0D);
        connect(2);
        when(player.getDistanceSq(anyDouble(),anyDouble(),anyDouble())).thenReturn(0.0D);
        when(world.blockExists(1,64,1)).thenReturn(false);
        connect(2);
        verify(world,never()).getTileEntity(anyInt(),anyInt(),anyInt());
    }

    @Test
    public void invalidHeightAndExtremeCoordinatesAreRejected() {
        assertFalse(ServerPacketDispatcher.canAccess(player,1,-1,1,8));
        assertFalse(ServerPacketDispatcher.canAccess(player,1,256,1,8));
        when(player.getDistanceSq(anyDouble(),anyDouble(),anyDouble())).thenReturn(Double.MAX_VALUE);
        assertFalse(ServerPacketDispatcher.canAccess(player,Integer.MIN_VALUE,64,Integer.MAX_VALUE,8));
        verify(world,never()).blockExists(anyInt(),anyInt(),anyInt());
    }

    @Test
    public void connectionRequiresValidSideAndWrench() {
        connect(2);
        wrench();
        connect(-1);
        connect(6);
        when(player.isSneaking()).thenReturn(true);
        connect(2);
        verifyNoInteractions(pipe);
    }

    @Test
    public void connectionHonorsWrenchPermission() {
        ItemWrench wrench = new ItemWrench() {
            @Override
            public boolean canWrench(net.minecraft.entity.player.EntityPlayer player, int x, int y, int z) {
                return false;
            }
        };
        player.inventory.setInventorySlotContents(0,new ItemStack(wrench));
        connect(2);
        assertSame(wrench,player.getHeldItem().getItem());
        verifyNoInteractions(pipe);
    }

    @Test
    public void uninitializedPipeNetworkIsNotModified() {
        wrench();
        when(pipe.getNetwork()).thenReturn(null);
        connect(2);
        verify(pipe,never()).connectDisconnect(any(),anyInt(),anyInt(),anyInt(),anyInt());
    }

    @Test
    public void spawnAndAdventureProtectionRejectChanges() {
        wrench();
        when(world.canMineBlock(player,1,64,1)).thenReturn(false);
        connect(2);
        when(world.canMineBlock(player,1,64,1)).thenReturn(true);
        when(player.canPlayerEdit(eq(1),eq(64),eq(1),anyInt(),any())).thenReturn(false);
        connect(2);
        verifyNoInteractions(pipe);
    }

    @Test
    public void deniedForgeInteractionRejectsPipeChanges() throws Exception {
        wrench();
        denyInteraction();
        connect(2);
        verify(pipe,never()).connectDisconnect(any(),anyInt(),anyInt(),anyInt(),anyInt());
    }

    @Test
    public void renameRequiresMatchingOpenHammer() {
        rename("Name");
        openAnvil();
        when(world.getTileEntity(1,64,1)).thenReturn(mock(TileEntitySteamHammer.class));
        rename("Name");
        when(world.getTileEntity(1,64,1)).thenReturn(hammer);
        when(anvil.canInteractWith(player)).thenReturn(false);
        rename("Name");
        verify(anvil,never()).updateItemName(anyString());
    }

    @Test
    public void invalidRenameTextIsRejected() {
        openAnvil();
        rename(null);
        rename(new String(new char[41]).replace('\0','a'));
        rename("bad\nname");
        rename("bad\u00a7cname");
        verify(anvil,never()).updateItemName(anyString());
    }

    @Test
    public void validAndEmptyNamesAreAppliedAndSaved() {
        openAnvil();
        rename("Brass Hammer");
        rename("");
        verify(anvil).updateItemName("Brass Hammer");
        verify(anvil).updateItemName("");
        verify(hammer,times(2)).markDirty();
    }

    @Test
    public void nonBlockCamouflageAndEmptyStacksAreIgnored() {
        when(player.isSneaking()).thenReturn(true);
        player.inventory.setInventorySlotContents(0,new ItemStack(new Item()));
        camo();
        camouflage();
        player.getHeldItem().stackSize=0;
        camo();
        verifyNoInteractions(pipe);
    }

    @Test
    public void camouflageConsumesOneItemClearsSlotAndSaves() {
        Block block = camouflage();
        camo();
        assertSame(block,pipe.disguiseBlock);
        assertNull(player.inventory.getCurrentItem());
        verify(pipe).markDirty();
        verify(world).markBlockForUpdate(1,64,1);
        verify(player.inventoryContainer).detectAndSendChanges();
    }

    @Test
    public void creativeCamouflageDoesNotConsumeAndDuplicateIsNoOp() {
        Block block = camouflage();
        player.capabilities.isCreativeMode=true;
        camo();
        camo();
        assertSame(block,pipe.disguiseBlock);
        assertEquals(1,player.getHeldItem().stackSize);
        verify(pipe,times(1)).markDirty();
    }

    @Test
    public void camouflageHonorsSneakingAndForgeProtection() throws Exception {
        camouflage();
        when(player.isSneaking()).thenReturn(false);
        camo();
        when(player.isSneaking()).thenReturn(true);
        denyInteraction();
        camo();
        assertNull(pipe.disguiseBlock);
        assertEquals(1,player.getHeldItem().stackSize);
        verify(pipe,never()).markDirty();
    }
}
