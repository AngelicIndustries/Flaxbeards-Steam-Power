package flaxbeard.steamcraft.api.steamnet;

import cpw.mods.fml.common.gameevent.TickEvent;
import flaxbeard.steamcraft.api.tile.SteamTransporterTileEntity;
import flaxbeard.steamcraft.api.util.Coord4;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SteamNetworkLifecycleTest {
    private World world;
    private final Map<Coord4, SteamTransporterTileEntity> tiles = new HashMap<>();
    private final Set<Integer> loaded = new HashSet<>();
    private final SteamNetworkRegistry registry = SteamNetworkRegistry.getInstance();
    private SteamNetworkRegistry.NetworkLoader loader;

    @BeforeClass public static void registerTile() {
        TileEntity.addMapping(SteamTransporterTileEntity.class, "test:network_transporter");
    }

    @Before public void setUp() throws Exception {
        resetRegistry();
        world = mock(World.class);
        setField(World.class, world, "provider", mock(WorldProvider.class));
        setField(World.class, world, "perWorldStorage", new MapStorage(null));
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(mock(Block.class));
        when(world.blockExists(anyInt(), anyInt(), anyInt())).thenAnswer(i -> loaded.contains((Integer)i.getArgument(0) >> 4));
        when(world.getTileEntity(anyInt(), anyInt(), anyInt())).thenAnswer(i ->
            tiles.get(new Coord4(i.getArgument(0), i.getArgument(1), i.getArgument(2), 0)));
        registry.newDimension(0);
        loader = new SteamNetworkRegistry.NetworkLoader();

    }

    @After public void tearDown() throws Exception {

        resetRegistry();
    }

    private static void setField(Class<?> type,Object target,String name,Object value) throws Exception {
        java.lang.reflect.Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }

    private void resetRegistry() {
        registry.clear();
    }

    private SteamTransporterTileEntity tile(int x) {
        SteamTransporterTileEntity t = new SteamTransporterTileEntity(10000, new ForgeDirection[]{ForgeDirection.WEST, ForgeDirection.EAST});
        t.xCoord=x;t.yCoord=128;t.setWorldObj(world);
        tiles.put(t.getCoords(),t);loaded.add(x >> 4);
        t.refresh();
        return t;
    }

    private NBTTagCompound save(SteamTransporterTileEntity t) {
        NBTTagCompound nbt=new NBTTagCompound();t.writeToNBT(nbt);return nbt;
    }

    private void unload(int chunkX) throws Exception {
        Chunk chunk=mock(Chunk.class);
        chunk.worldObj=world;
        setField(Chunk.class,chunk,"chunkTileEntityMap",new HashMap<>());
        for (SteamTransporterTileEntity t:tiles.values()) if((t.xCoord>>4)==chunkX) chunk.chunkTileEntityMap.put(new net.minecraft.world.ChunkPosition(t.xCoord,128,0),t);
        loader.onChunkUnload(new ChunkEvent.Unload(chunk));
        loaded.remove(chunkX);
    }

    @Test public void neighborDiscoveryDoesNotReadUnloadedChunks() {
        SteamTransporterTileEntity t=tile(15);
        clearInvocations(world);
        SteamNetwork.getNeighboringTransporters(t);
        verify(world,never()).getTileEntity(16,128,0);
    }

    @Test public void saveUsesCurrentNetworkSteamAndConservesRemainders() {
        SteamTransporterTileEntity a=tile(14),b=tile(15),c=tile(16);
        a.getNetwork().addSteam(10001);
        assertEquals(10001,save(a).getInteger("steam")+save(b).getInteger("steam")+save(c).getInteger("steam"));
        a.getNetwork().decrSteam(4000);
        assertEquals(6001,save(a).getInteger("steam")+save(b).getInteger("steam")+save(c).getInteger("steam"));
    }

    @Test public void unloadDetachesSavedShareAndReloadDoesNotDuplicateCapacity() throws Exception {
        SteamTransporterTileEntity a=tile(15),b=tile(16);
        a.getNetwork().addSteam(8000);
        unload(1);
        assertNull(b.getNetwork());
        assertEquals(10000,a.getNetwork().getCapacity());
        assertEquals(4000,a.getNetwork().getSteam());
        NBTTagCompound nbt=save(b);
        assertEquals(4000,nbt.getInteger("steam"));
        SteamTransporterTileEntity restored=new SteamTransporterTileEntity();
        restored.readFromNBT(nbt);restored.setWorldObj(world);
        tiles.put(restored.getCoords(),restored);loaded.add(1);restored.refresh();
        assertSame(a.getNetwork(),restored.getNetwork());
        assertEquals(20000,a.getNetwork().getCapacity());
        assertEquals(8000,a.getNetwork().getSteam());
    }

    @Test public void dormantTileCanRejoinWithoutLosingItsStoredSteam() throws Exception {
        SteamTransporterTileEntity a=tile(15),b=tile(16);
        a.getNetwork().addSteam(8000);
        unload(1);loaded.add(1);
        b.updateEntity();
        assertSame(a.getNetwork(),b.getNetwork());
        assertEquals(8000,a.getNetwork().getSteam());
        assertEquals(20000,a.getNetwork().getCapacity());
    }

    @Test public void unloadingBridgeSeparatesLoadedComponents() throws Exception {
        SteamTransporterTileEntity left=tile(15);
        for(int x=16;x<=32;x++)tile(x);
        SteamTransporterTileEntity right=tiles.get(new Coord4(32,128,0,0));
        left.getNetwork().addSteam(18000);
        unload(1);
        assertNotSame(left.getNetwork(),right.getNetwork());
        assertEquals(10000,left.getNetwork().getCapacity());
        assertEquals(10000,right.getNetwork().getCapacity());
        assertEquals(1000,left.getNetwork().getSteam());
        assertEquals(1000,right.getNetwork().getSteam());
    }

    @Test public void joiningNetworksTransfersLiveSteamRatherThanStaleTileSnapshots() {
        SteamTransporterTileEntity a=tile(14),b=tile(16);
        a.getNetwork().addSteam(3000);b.getNetwork().addSteam(4000);
        tile(15);
        assertSame(a.getNetwork(),b.getNetwork());
        assertEquals(7000,a.getNetwork().getSteam());
    }

    @Test public void refreshDoesNotReadUnloadedMembers() {
        SteamTransporterTileEntity a=tile(15),b=tile(16);
        loaded.remove(1);clearInvocations(world);
        a.getNetwork().refresh();
        verify(world,never()).getTileEntity(16,128,0);
    }

    @Test public void worldUnloadDropsRegistryReferencesAndInitialization() throws Exception {
        SteamTransporterTileEntity a=tile(15);
        loader.onWorldUnload(new WorldEvent.Unload(world));
        assertFalse(registry.isInitialized(0));
        SteamNetwork net=a.getNetwork();
        SteamTransporterTileEntity other=tile(30);
        assertNotSame(net,registry.getNetwork(net.getName(),other));
    }

    @Test public void registryTicksEachNetworkOncePerServerTick() {
        SteamNetwork net=mock(SteamNetwork.class);
        when(net.getName()).thenReturn("test-network");when(net.tick()).thenReturn(true);
        registry.add(net);
        registry.onTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
        registry.onTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        verify(net,times(1)).tick();
    }

    @Test public void repeatedUnloadReloadPreservesOddSteamTotals() throws Exception {
        SteamTransporterTileEntity a=tile(15),b=tile(16);
        a.getNetwork().addSteam(8001);
        for(int i=0;i<5;i++) {
            unload(1);
            assertEquals(8001,a.getNetwork().getSteam()+save(b).getInteger("steam"));
            loaded.add(1);b.updateEntity();
            assertEquals(8001,a.getNetwork().getSteam());
            assertEquals(20000,a.getNetwork().getCapacity());
        }
    }

    @Test public void pipeTickDoesNotLoadNeighborForGaugesConnectionsOrLeaks() {
        flaxbeard.steamcraft.tile.TileEntitySteamPipe pipe=new flaxbeard.steamcraft.tile.TileEntitySteamPipe();
        pipe.xCoord=15;pipe.yCoord=128;pipe.setWorldObj(world);
        tiles.put(pipe.getCoords(),pipe);loaded.add(0);
        pipe.updateEntity();
        verify(world,never()).getTileEntity(16,128,0);
        verify(world,never()).getBlock(16,128,0);
    }

    @Test public void removingEmptyNetworksDoesNotSkipRemainingNetworks() {
        List<SteamNetwork> nets=new ArrayList<>();
        for(int i=0;i<8;i++) {
            SteamNetwork net=mock(SteamNetwork.class);when(net.getName()).thenReturn("empty-"+i);
            registry.add(net);nets.add(net);
        }
        registry.onTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        for(SteamNetwork net:nets)verify(net).tick();
    }
}
