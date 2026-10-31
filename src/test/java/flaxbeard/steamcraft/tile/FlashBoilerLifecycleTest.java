package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.SteamcraftBlocks;
import flaxbeard.steamcraft.api.ISteamTransporter;
import flaxbeard.steamcraft.api.steamnet.*;
import flaxbeard.steamcraft.api.tile.SteamTransporterTileEntity;
import flaxbeard.steamcraft.api.util.Coord4;
import flaxbeard.steamcraft.block.BlockFlashBoiler;
import flaxbeard.steamcraft.test.ForgeFluidTestSupport;
import net.minecraft.block.Block;
import net.minecraft.world.*;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FlashBoilerLifecycleTest {
    private World world;
    private Block oldBlock;
    private final Map<Coord4, SteamTransporterTileEntity> tiles = new HashMap<>();
    private final Map<Coord4, Integer> metadata = new HashMap<>();
    private final Set<Integer> unloaded = new HashSet<>();
    private final TileEntityFlashBoiler[] boilers = new TileEntityFlashBoiler[8];

    @BeforeClass public static void bootstrap() throws Exception { ForgeFluidTestSupport.bootstrap(); }
    @Before public void setup() throws Exception {
        oldBlock = SteamcraftBlocks.flashBoiler;
        SteamcraftBlocks.flashBoiler = new BlockFlashBoiler();
        SteamNetworkRegistry.getInstance().clear();
        SteamNetworkRegistry.getInstance().newDimension(0);
        world = mock(World.class);
        for (String field : new String[]{"provider", "perWorldStorage"}) {
            java.lang.reflect.Field f = World.class.getDeclaredField(field); f.setAccessible(true);
            f.set(world, field.equals("provider") ? mock(WorldProvider.class) : new MapStorage(null));
        }
        when(world.blockExists(anyInt(), anyInt(), anyInt())).thenAnswer(i -> !unloaded.contains((int)i.getArgument(0) >> 4));
        when(world.getTileEntity(anyInt(), anyInt(), anyInt())).thenAnswer(i -> tiles.get(coord(i.getArguments())));
        Block air = mock(Block.class);
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(i ->
            tiles.get(coord(i.getArguments())) instanceof TileEntityFlashBoiler ? SteamcraftBlocks.flashBoiler : air);
        when(world.getBlockMetadata(anyInt(), anyInt(), anyInt())).thenAnswer(i -> metadata.getOrDefault(coord(i.getArguments()), 0));
        when(world.setBlockMetadataWithNotify(anyInt(), anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(i -> {
            Coord4 c = coord(i.getArguments()); metadata.put(c, i.getArgument(3));
            if (tiles.containsKey(c)) tiles.get(c).updateContainingBlockInfo();
            return true;
        });
        for (int i = 0; i < 8; i++) boilers[i] = place(new TileEntityFlashBoiler(), 15 + i % 2, 128 + i / 4, 5 + i / 2 % 2);
    }
    @After public void cleanup() { SteamcraftBlocks.flashBoiler = oldBlock; SteamNetworkRegistry.getInstance().clear(); }
    private Coord4 coord(Object[] a) { return new Coord4((int)a[0], (int)a[1], (int)a[2], 0); }
    private <T extends SteamTransporterTileEntity> T place(T tile, int x, int y, int z) {
        tile.xCoord=x; tile.yCoord=y; tile.zCoord=z; tile.setWorldObj(world); tiles.put(tile.getCoords(),tile); return tile;
    }
    private void form() { boilers[7].checkMultiblock(false, 3); }
    private int total() {
        Set<SteamNetwork> nets = new HashSet<>(); int result=0;
        for (SteamTransporterTileEntity tile : tiles.values()) {
            if (tile.getNetwork() == null) result += tile.getSteam(); else nets.add(tile.getNetwork());
        }
        for (SteamNetwork net : nets) result += net.getSteam();
        return result;
    }
    private HashSet<ISteamTransporter> unload(int chunk) {
        HashSet<ISteamTransporter> departing = new HashSet<>();
        for (TileEntityFlashBoiler tile : boilers) if (tile.xCoord >> 4 == chunk) departing.add(tile);
        boilers[0].getNetwork().unloadTransporters(departing); unloaded.add(chunk); return departing;
    }
    @Test public void allEightMembersAndOutputPipeShareCapacity() {
        TileEntitySteamPipe pipe = place(new TileEntitySteamPipe(), 17,129,5); pipe.refresh();
        form();
        for (TileEntityFlashBoiler tile : boilers) assertSame(pipe.getNetwork(), tile.getNetwork());
        assertEquals(110000, pipe.getNetwork().getCapacity());
        assertEquals(9, pipe.getNetwork().getSize());
    }
    @Test public void formationAfterTicksDoesNotKeepZeroCapacityNetworks() {
        for (TileEntityFlashBoiler tile : boilers) tile.updateEntity();
        form();
        assertEquals(100000, boilers[0].getNetwork().getCapacity());
        for (TileEntityFlashBoiler tile : boilers) assertSame(boilers[0].getNetwork(), tile.getNetwork());
    }
    @Test public void disassemblyAndReformationPreserveOddSteamWithExternalPipe() {
        TileEntitySteamPipe pipe = place(new TileEntitySteamPipe(),17,129,5); pipe.refresh();
        form(); boilers[0].getNetwork().addSteam(80003);
        for (int round=0; round<3; round++) {
            boilers[3].destroyMultiblock();
            for (TileEntityFlashBoiler tile : boilers) { tile.updateEntity(); assertNull(tile.getNetwork()); }
            assertEquals(10000,pipe.getNetwork().getCapacity());
            assertEquals(80003,total());
            form(); assertEquals(110000,pipe.getNetwork().getCapacity()); assertEquals(80003,total());
        }
    }
    @Test public void primaryChunkUnloadKeepsSurvivingCapacityAndExactSteam() {
        form(); boilers[0].getNetwork().addSteam(80003); unload(0);
        clearInvocations(world);
        boilers[1].getNetwork().refresh();
        assertEquals(50000,boilers[1].getNetwork().getCapacity());
        assertEquals(80003,total());
        verify(world,never()).getTileEntity(15,128,5);
        unloaded.clear(); for (TileEntityFlashBoiler tile : boilers) tile.refresh();
        assertEquals(100000,boilers[0].getNetwork().getCapacity()); assertEquals(80003,total());
    }
    @Test public void incompleteLoadedHalfPausesWaterAndFuelWithoutLoadingMissingChunk() {
        form(); boilers[0].updateEntity(); unload(1);
        TileEntityFlashBoiler primary=boilers[0]; primary.myTank.setFluid(new FluidStack(FluidRegistry.WATER,100)); primary.furnaceBurnTime=50;
        clearInvocations(world); primary.updateEntity();
        assertEquals(100,primary.myTank.getFluidAmount()); assertEquals(50,primary.furnaceBurnTime);
        verify(world,never()).getBlock(16,128,5); verify(world,never()).getTileEntity(16,128,5);
        unloaded.clear(); for (TileEntityFlashBoiler tile : boilers) tile.refresh(); primary.updateEntity();
        assertEquals(80,primary.myTank.getFluidAmount()); assertEquals(49,primary.furnaceBurnTime);
    }
    @Test public void brokenMemberInvalidatesReturningHalfWithoutSteamLoss() {
        form(); boilers[0].getNetwork().addSteam(80003); unload(0);
        int removed=boilers[1].getNetwork().getSavedSteam(boilers[1]);
        SteamcraftBlocks.flashBoiler.onBlockPreDestroy(world,16,128,5,2); tiles.remove(boilers[1].getCoords());
        unloaded.clear();
        for (TileEntityFlashBoiler tile : boilers) if (tiles.containsValue(tile)) tile.updateEntity();
        for (SteamTransporterTileEntity tile : tiles.values()) { assertEquals(0,tile.getBlockMetadata()); assertNull(tile.getNetwork()); }
        assertEquals(80003-removed,total());
    }
    @Test public void detachedSteamOperationsDoNotRecurseOrDereferenceMissingPrimary() {
        form(); boilers[0].destroyMultiblock();
        metadata.put(boilers[0].getCoords(),1); metadata.put(boilers[1].getCoords(),2);
        boilers[0].insertSteam(1,ForgeDirection.UP); boilers[0].decrSteam(1);
        unloaded.add(0); boilers[1].insertSteam(1,ForgeDirection.UP); boilers[1].decrSteam(1);
        assertEquals(0,total());
    }
    @Test public void secondarySteamOperationsUseItsOwnLoadedNetwork() {
        form(); boilers[0].getNetwork().addSteam(80003); unload(0);
        int before=total(); boilers[5].insertSteam(7,ForgeDirection.EAST); boilers[5].decrSteam(3);
        assertEquals(before+4,total());
    }
    @Test public void formationDoesNotReadUnloadedCandidatesOrAcceptMissingTiles() {
        unloaded.add(1); clearInvocations(world); boilers[0].checkMultiblock(false,3);
        assertEquals(0,boilers[0].getBlockMetadata());
        verify(world,never()).getBlock(16,128,5);
        unloaded.clear(); tiles.remove(boilers[7].getCoords()); form(); assertEquals(0,boilers[0].getBlockMetadata());
    }
    @Test public void clusterMembershipUsesZAndUnformedDisassemblyIsSafe() {
        boilers[0].destroyMultiblock(); assertFalse(boilers[0].isInCluster(15,128,5));
        form(); assertTrue(boilers[0].isInCluster(16,129,6)); assertFalse(boilers[0].isInCluster(16,129,129));
    }
    @Test public void clientBreakDoesNotDisassembleOrMutateSteam() {
        form(); boilers[0].getNetwork().addSteam(80003); world.isRemote=true;
        SteamcraftBlocks.flashBoiler.onBlockPreDestroy(world,15,128,5,1);
        assertEquals(1,boilers[0].getBlockMetadata()); assertEquals(80003,total());
    }
    @Test public void breakWithMissingTileIsSafe() {
        tiles.remove(boilers[0].getCoords()); SteamcraftBlocks.flashBoiler.onBlockPreDestroy(world,15,128,5,1);
    }
    @Test public void anyLastPlacedCornerFormsTheSamePrimaryAndSavesOrientation() {
        for (int corner = 0; corner < 8; corner++) {
            boilers[corner].checkMultiblock(false, 5);
            for (int i = 0; i < 8; i++) {
                assertEquals(i + 1, boilers[i].getBlockMetadata());
                assertSame(boilers[0], boilers[i].getPrimaryTileEntity());
                assertEquals(5, boilers[i].getFront());
                verify(world, atLeastOnce()).markTileEntityChunkModified(boilers[i].xCoord, boilers[i].yCoord, boilers[i].zCoord, boilers[i]);
            }
            boilers[corner].destroyMultiblock();
        }
    }
    @Test public void fullStructureProducesIntoTheOutputPipeNetwork() {
        TileEntitySteamPipe pipe = place(new TileEntitySteamPipe(),17,129,5); pipe.refresh(); form();
        TileEntityFlashBoiler primary=boilers[0]; primary.updateEntity();
        primary.myTank.setFluid(new FluidStack(FluidRegistry.WATER,100)); primary.furnaceBurnTime=50;
        primary.updateEntity(); assertEquals(100,pipe.getNetwork().getSteam()); assertEquals(80,primary.myTank.getFluidAmount());
    }
    @Test public void secondarySteamOperationsRejectInvalidAmountsFacesAndClientCalls() {
        form(); boilers[0].getNetwork().addSteam(500);
        boilers[5].insertSteam(-7,ForgeDirection.EAST); boilers[5].decrSteam(-7);
        boilers[5].insertSteam(7,ForgeDirection.UNKNOWN); boilers[5].insertSteam(7,ForgeDirection.SOUTH);
        world.isRemote=true; boilers[5].insertSteam(7,ForgeDirection.EAST); boilers[5].decrSteam(7);
        assertEquals(500,total());
    }
    @Test public void mismatchedMemberIsNotClaimedOrDisassembled() {
        form();
        metadata.put(boilers[7].getCoords(),1); boilers[7].updateContainingBlockInfo();
        boilers[0].updateEntity();
        assertEquals(0,boilers[0].getBlockMetadata()); assertEquals(1,boilers[7].getBlockMetadata());
    }

    @Test public void explosionLookupDoesNotLoadTheMissingHalf() {
        form(); boilers[0].getNetwork().addSteam(500); unload(0);
        clearInvocations(world); boilers[5].explode();
        verify(world,never()).getBlock(15,128,5);
        verify(world,never()).getTileEntity(15,128,5);
        verify(world,never()).getTileEntity(15,129,5);
        assertNull(boilers[5].getNetwork());
    }
    @Test public void detachedAndClientExplosionsAreIgnored() {
        boilers[0].explode(); form(); boilers[0].getNetwork().addSteam(500);
        world.isRemote=true; boilers[0].explode(); assertEquals(500,total());
    }

    @Test public void dirtyNotificationsSkipUnloadedNeighborsAndRetainLoadedWeakChanges() {
        unloaded.add(0);
        Block direct = mock(Block.class), indirect = mock(Block.class);
        when(world.getBlock(17,129,5)).thenReturn(direct);
        when(direct.isNormalCube(world,17,129,5)).thenReturn(true);
        when(world.getBlock(18,129,5)).thenReturn(indirect);
        when(indirect.getWeakChanges(world,18,129,5)).thenReturn(true);
        clearInvocations(world); boilers[5].markDirty();
        verify(world).markTileEntityChunkModified(16,129,5,boilers[5]);
        verify(world,never()).getBlock(15,129,5);
        verify(world,never()).func_147453_f(anyInt(),anyInt(),anyInt(),any());
        verify(direct).onNeighborChange(world,17,129,5,16,129,5);
        verify(indirect).onNeighborChange(world,18,129,5,16,129,5);
    }
    @Test public void dirtyNotificationsDoNotReadUnloadedIndirectNeighbors() {
        TileEntityFlashBoiler tile=place(new TileEntityFlashBoiler(),14,128,5);
        Block direct=mock(Block.class); when(world.getBlock(15,128,5)).thenReturn(direct);
        when(direct.isNormalCube(world,15,128,5)).thenReturn(true);
        unloaded.add(1); clearInvocations(world); tile.markDirty();
        verify(direct).onNeighborChange(world,15,128,5,14,128,5);
        verify(world,never()).getBlock(16,128,5);
    }
    @Test public void dirtyUnloadedTileDoesNotLoadItsOwnChunk() {
        unloaded.add(0); clearInvocations(world); boilers[0].markDirty();
        verify(world,never()).getBlockMetadata(15,128,5);
        verify(world,never()).markTileEntityChunkModified(anyInt(),anyInt(),anyInt(),any());
    }

    @Test public void connectedPipeDirtyNotificationsAlsoAvoidUnloadedNeighbors() {
        TileEntitySteamPipe pipe=place(new TileEntitySteamPipe(),15,140,5);
        unloaded.add(1); clearInvocations(world); pipe.markDirty();
        verify(world).markTileEntityChunkModified(15,140,5,pipe);
        verify(world,never()).getBlock(16,140,5);
        verify(world,never()).func_147453_f(anyInt(),anyInt(),anyInt(),any());
    }

}
