package flaxbeard.steamcraft.api.steamnet;

import flaxbeard.steamcraft.Config;
import flaxbeard.steamcraft.api.IWrenchable;
import flaxbeard.steamcraft.api.tile.SteamTransporterTileEntity;
import flaxbeard.steamcraft.api.util.Coord4;
import flaxbeard.steamcraft.block.BlockPipe;
import flaxbeard.steamcraft.block.BlockValvePipe;
import flaxbeard.steamcraft.tile.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.ForgeDirection;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SteamNetworkSplitTest {
    private World world;
    private final Map<Coord4, SteamTransporterTileEntity> tiles = new HashMap<>();
    private boolean redstoneConfig;

    @Before public void setUp() throws Exception {
        SteamNetworkRegistry.getInstance().clear();
        SteamNetworkRegistry.getInstance().newDimension(0);
        world = mock(World.class);
        setField("provider", mock(WorldProvider.class));
        setField("perWorldStorage", new MapStorage(null));
        when(world.blockExists(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(mock(BlockPipe.class));
        when(world.getTileEntity(anyInt(), anyInt(), anyInt())).thenAnswer(i ->
            tiles.get(new Coord4(i.getArgument(0), i.getArgument(1), i.getArgument(2), 0)));
        redstoneConfig = Config.enableRedstoneValvePipe;
        Config.enableRedstoneValvePipe = true;
    }

    @After public void tearDown() {
        Config.enableRedstoneValvePipe = redstoneConfig;
        SteamNetworkRegistry.getInstance().clear();
    }

    private void setField(String name, Object value) throws Exception {
        java.lang.reflect.Field field = World.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(world, value);
    }

    private <T extends SteamTransporterTileEntity> T place(T tile, int x) {
        tile.xCoord = x;
        tile.yCoord = 128;
        tile.setWorldObj(world);
        tiles.put(tile.getCoords(), tile);
        tile.refresh();
        return tile;
    }

    private TileEntitySteamPipe pipe(int x) { return place(new TileEntitySteamPipe(), x); }

    private int totalSteam() {
        Set<SteamNetwork> networks = new HashSet<>();
        int steam = 0;
        for (SteamTransporterTileEntity tile : tiles.values()) {
            if (tile.getNetwork() == null) steam += tile.getSteam();
            else networks.add(tile.getNetwork());
        }
        for (SteamNetwork network : networks) steam += network.getSteam();
        return steam;
    }

    @Test public void closingAndOpeningValvePreservesOddTotalAndDetachesClosedValve() {
        TileEntitySteamPipe left = pipe(0), right = pipe(2);
        TileEntityValvePipe valve = place(new TileEntityValvePipe(), 1);
        left.getNetwork().addSteam(8001);
        for (int i = 0; i < 5; i++) {
            valve.updateRedstoneState(false);
            assertFalse(valve.isOpen());
            assertNull(valve.getNetwork());
            assertNotSame(left.getNetwork(), right.getNetwork());
            assertEquals(8001, totalSteam());
            valve.updateRedstoneState(true);
            assertSame(left.getNetwork(), right.getNetwork());
            assertEquals(8001, totalSteam());
            assertEquals(20000, valve.getNetwork().getCapacity());
        }
    }

    @Test public void repeatedOpenOnIsolatedValveKeepsItsNetwork() {
        TileEntityValvePipe valve = place(new TileEntityValvePipe(), 1);
        SteamNetwork original = valve.getNetwork();
        valve.updateRedstoneState(true);
        valve.updateRedstoneState(true);
        assertSame(original, valve.getNetwork());
    }

    @Test public void closeBeforeInitializationIsAppliedAndSavedImmediately() {
        TileEntityValvePipe valve = new TileEntityValvePipe();
        valve.setWorldObj(world);
        valve.updateRedstoneState(false);
        assertFalse(valve.isOpen());
        verify(world, atLeastOnce()).markTileEntityChunkModified(0, 0, 0, valve);
    }

    @Test public void valveStateChangesMarkChunkDirty() {
        TileEntityValvePipe valve = place(new TileEntityValvePipe(), 1);
        clearInvocations(world);
        valve.updateRedstoneState(false);
        verify(world, atLeastOnce()).markTileEntityChunkModified(1, 128, 0, valve);
    }

    @Test public void valveHandleFaceDoesNotConnectToNetwork() {
        TileEntitySteamPipe left = pipe(0), right = pipe(2);
        when(world.getBlockMetadata(1, 128, 0)).thenReturn(ForgeDirection.EAST.ordinal());
        TileEntityValvePipe valve = place(new TileEntityValvePipe(), 1);
        assertSame(left.getNetwork(), valve.getNetwork());
        assertNotSame(right.getNetwork(), valve.getNetwork());
    }

    @Test public void wrenchDisconnectReconnectConservesSteamAndCapacity() {
        TileEntitySteamPipe left = pipe(0), middle = pipe(1), right = pipe(2);
        left.getNetwork().addSteam(9001);
        for (int i = 0; i < 4; i++) {
            middle.connectDisconnect(world, 1, 128, 0, ForgeDirection.EAST.ordinal());
            assertNotSame(left.getNetwork(), right.getNetwork());
            assertEquals(9001, totalSteam());
            middle.connectDisconnect(world, 1, 128, 0, ForgeDirection.EAST.ordinal());
            assertSame(left.getNetwork(), right.getNetwork());
            assertEquals(9001, totalSteam());
            assertEquals(30000, left.getNetwork().getCapacity());
        }
    }

    @Test public void removingBridgeRetiresOldNetworkAndOnlyVentsRemovedShare() {
        TileEntitySteamPipe left = pipe(0), middle = pipe(1), right = pipe(2);
        SteamNetwork old = left.getNetwork();
        old.addSteam(9001);
        int removed = old.getSavedSteam(middle);
        new BlockPipe().onBlockPreDestroy(world, 1, 128, 0, 0);
        tiles.remove(middle.getCoords());
        assertNull(middle.getNetwork());
        assertNotSame(left.getNetwork(), right.getNetwork());
        assertEquals(9001 - removed, totalSteam());
        assertEquals(0, old.getSize());
        assertEquals(0, old.getSteam());
    }

    @Test public void removingMissingTileIsSafe() {
        new BlockPipe().onBlockPreDestroy(world, 1, 128, 0, 0);
    }

    @Test public void repeatedJoinOfIsolatedTransporterIsIdempotent() {
        TileEntitySteamPipe pipe = pipe(0);
        SteamNetwork original = pipe.getNetwork();
        original.addSteam(3001);
        for (int i = 0; i < 3; i++) SteamNetwork.newOrJoin(pipe);
        assertSame(original, pipe.getNetwork());
        assertEquals(3001, totalSteam());
    }

    @Test public void changingConnectionsOfAnIsolatedPipePreservesItsSteam() {
        TileEntitySteamPipe pipe = pipe(0);
        pipe.getNetwork().addSteam(3001);
        pipe.connectDisconnect(world, 0, 128, 0, ForgeDirection.EAST.ordinal());
        assertEquals(3001, totalSteam());
        assertEquals(10000, pipe.getNetwork().getCapacity());
    }

    @Test public void rotatingMachinesKeepsExactlyOneCopyOfTheirSteam() {
        EntityPlayer player = mock(EntityPlayer.class);
        SteamTransporterTileEntity[] machines = {new TileEntitySteamHeater(), new TileEntityFan(),
            new TileEntityVacuum(), new TileEntityFluidSteamConverter(), new TileEntitySmasher()};
        for (int i = 0; i < machines.length; i++) {
            SteamTransporterTileEntity machine = place(machines[i], i * 10);
            machine.getNetwork().addSteam(3001);
            for (int rotation = 0; rotation < 3; rotation++) {
                ((IWrenchable) machine).onWrench(null, player, world, machine.xCoord, 128, 0, 0, 0, 0, 0);
                assertEquals(machine.getClass().getSimpleName(), 3001, machine.getNetwork().getSteam());
                assertEquals(machine.getCapacity(), machine.getNetwork().getCapacity());
            }
        }
    }

    @Test public void valveActivationDoesNotChangeClientState() {
        TileEntityValvePipe valve = place(new TileEntityValvePipe(), 0);
        world.isRemote = true;
        new BlockValvePipe().onBlockActivated(world, 0, 128, 0, mock(EntityPlayer.class), 0, 0, 0, 0);
        assertFalse(valve.isTurning());
    }
    @Test public void closedValveDoesNotTransferSteamBetweenSides() {
        TileEntitySteamPipe left = pipe(0), right = pipe(2);
        TileEntityValvePipe valve = place(new TileEntityValvePipe(), 1);
        left.getNetwork().addSteam(8000);
        valve.updateRedstoneState(false);
        left.getNetwork().decrSteam(1000);
        assertEquals(3000, left.getNetwork().getSteam());
        assertEquals(4000, right.getNetwork().getSteam());
        valve.updateRedstoneState(true);
        assertEquals(7000, left.getNetwork().getSteam());
    }

    @Test public void repeatedDetachDoesNotChargeTheRemainingNetworkAgain() {
        TileEntitySteamPipe left = pipe(0), middle = pipe(1), right = pipe(2);
        SteamNetwork old = left.getNetwork();
        old.addSteam(9001);
        int detached = old.split(middle, true);
        assertEquals(9001, totalSteam());
        assertEquals(detached, middle.getSteam());
        assertEquals(0, old.split(middle, true));
        assertEquals(9001, totalSteam());
    }

    @Test public void rotatingBeforeFirstTickCanCreateANetwork() {
        TileEntitySteamHeater heater = new TileEntitySteamHeater();
        heater.yCoord = 128;
        heater.setWorldObj(world);
        tiles.put(heater.getCoords(), heater);
        heater.onWrench(null, mock(EntityPlayer.class), world, 0, 128, 0, 0, 0, 0, 0);
        assertNotNull(heater.getNetwork());
        assertEquals(0, heater.getNetwork().getSteam());
    }

    @Test public void newlyPlacedPowerSourceClosesValveEvenWhenOldNeighborWasStone() {
        TileEntityValvePipe valve = place(new TileEntityValvePipe(), 0);
        when(world.isBlockIndirectlyGettingPowered(0, 128, 0)).thenReturn(true);
        new BlockValvePipe().onNeighborBlockChange(world, 0, 128, 0, mock(Block.class));
        assertFalse(valve.isOpen());
        assertNull(valve.getNetwork());
        when(world.isBlockIndirectlyGettingPowered(0, 128, 0)).thenReturn(false);
        Block oldPowerSource = mock(Block.class);
        when(oldPowerSource.canProvidePower()).thenReturn(true);
        new BlockValvePipe().onNeighborBlockChange(world, 0, 128, 0, oldPowerSource);
        assertTrue(valve.isOpen());
        assertNotNull(valve.getNetwork());
    }

    @Test public void clientRedstoneUpdatesDoNotOverrideAuthoritativeValveState() {
        TileEntityValvePipe valve = place(new TileEntityValvePipe(), 0);
        world.isRemote = true;
        valve.updateRedstoneState(false);
        assertTrue(valve.isOpen());
    }

}
