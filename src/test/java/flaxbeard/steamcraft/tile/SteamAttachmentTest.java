package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.api.tile.SteamTransporterTileEntity;
import flaxbeard.steamcraft.block.*;
import flaxbeard.steamcraft.test.ForgeFluidTestSupport;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SteamAttachmentTest {
    private World world;
    private SteamTransporterTileEntity source;
    private TileEntitySteamGauge gauge;
    private TileEntityRuptureDisc disc;
    private BlockSteamGauge gaugeBlock;
    private RecordingDisc discBlock;
    private int metadata;
    private static class RecordingDisc extends BlockRuptureDisc {
        int dropped = -1;
        @Override public void dropBlockAsItemWithChance(World w,int x,int y,int z,int meta,float chance,int fortune) { dropped=damageDropped(meta); }
    }
    @BeforeClass public static void bootstrap() throws Exception { ForgeFluidTestSupport.bootstrap(); }
    @Before public void setup() {
        world=mock(World.class);source=mock(SteamTransporterTileEntity.class);gaugeBlock=new BlockSteamGauge();discBlock=new RecordingDisc();
        when(world.blockExists(anyInt(),anyInt(),anyInt())).thenReturn(true);
        when(world.getBlock(anyInt(),anyInt(),anyInt())).thenReturn(mock(Block.class));
        when(world.getTileEntity(15,128,5)).thenReturn(source);
        metadata=5;
        when(world.getBlockMetadata(16,128,5)).thenAnswer(i -> metadata);
        when(world.setBlockMetadataWithNotify(eq(16),eq(128),eq(5),anyInt(),anyInt())).thenAnswer(i -> {metadata=i.getArgument(3);return true;});
        when(source.acceptsGauge(any())).thenReturn(true);
        gauge=place(new TileEntitySteamGauge());disc=place(new TileEntityRuptureDisc());
        when(world.getTileEntity(16,128,5)).thenReturn(disc);
    }
    private <T extends TileEntity> T place(T tile) { tile.xCoord=16;tile.yCoord=128;tile.zCoord=5;tile.setWorldObj(world);return tile; }
    @Test public void comparatorStaysWithinRedstoneRange() {
        float[] pressure={-1,0,.5F,1,1.2F,Float.MAX_VALUE,Float.NaN};int[] expected={0,0,7,15,15,15,0};
        for(int i=0;i<pressure.length;i++){when(source.getPressure()).thenReturn(pressure[i]);assertEquals(expected[i],gauge.getComparatorOutput());}
    }
    @Test public void chargerGaugeStillMeasuresItsItem() {
        TileEntitySteamCharger charger=mock(TileEntitySteamCharger.class);when(charger.acceptsGauge(any())).thenReturn(true);when(charger.getSteamInItem()).thenReturn(.6F);
        when(world.getTileEntity(15,128,5)).thenReturn(charger);assertEquals(.6F,gauge.getPressure(),0);
    }
    @Test public void unloadedSourceIsNotReadByGaugeOrDisc() {
        when(world.blockExists(15,128,5)).thenReturn(false);metadata=15;clearInvocations(world);
        assertEquals(0,gauge.getPressure(),0);disc.updateEntity();verify(world,never()).getTileEntity(15,128,5);verify(source,never()).decrSteam(anyInt());
    }
    @Test public void gaugeNotificationsDoNotReadUnloadedNeighbors() {
        when(source.getPressure()).thenReturn(.5F);when(world.blockExists(16,128,4)).thenReturn(false);gauge.updateEntity();
        verify(world,never()).func_147453_f(anyInt(),anyInt(),anyInt(),any());verify(world,never()).getBlock(16,128,4);
    }
    @Test public void placementChecksTheFacePointingTowardTheAttachment() {
        for(int side=2;side<=5;side++) {
            ForgeDirection d=ForgeDirection.getOrientation(side);when(world.getTileEntity(16-d.offsetX,128,5-d.offsetZ)).thenReturn(source);
            when(source.acceptsGauge(any())).thenReturn(false);when(source.acceptsGauge(d)).thenReturn(true);
            assertTrue(gaugeBlock.canPlaceBlockOnSide(world,16,128,5,side));assertTrue(discBlock.canPlaceBlockOnSide(world,16,128,5,side));
        }
    }
    @Test public void invalidOrBlockedSourceCannotBeReadOrVented() {
        when(source.acceptsGauge(any())).thenReturn(false);when(source.getPressure()).thenReturn(2F);when(source.getSteamShare()).thenReturn(100);
        assertEquals(0,gauge.getPressure(),0);metadata=15;disc.updateEntity();verify(source,never()).decrSteam(anyInt());
        when(source.acceptsGauge(any())).thenReturn(true);when(source.isInvalid()).thenReturn(true);metadata=5;assertEquals(0,gauge.getPressure(),0);
        assertFalse(gaugeBlock.canPlaceBlockOnSide(world,16,128,5,5));
    }
    @Test public void invalidOrientationsCannotReadAnUnrelatedTile() {
        when(world.getTileEntity(16,128,5)).thenReturn(source);when(source.getPressure()).thenReturn(2F);
        for(int side:new int[]{-1,0,1,6,9}){metadata=side;assertEquals(0,gauge.getPressure(),0);disc.updateEntity();}
        verify(source,never()).decrSteam(anyInt());verify(world,never()).createExplosion(any(),anyDouble(),anyDouble(),anyDouble(),anyFloat(),anyBoolean());
    }
    @Test public void unloadedSupportDoesNotDropEitherAttachment() {
        when(world.blockExists(15,128,5)).thenReturn(false);gaugeBlock.onNeighborBlockChange(world,16,128,5,mock(Block.class));
        metadata=15;discBlock.onNeighborBlockChange(world,16,128,5,mock(Block.class));assertEquals(-1,discBlock.dropped);
        verify(world,never()).setBlockToAir(anyInt(),anyInt(),anyInt());verify(world,never()).getTileEntity(15,128,5);
    }
    @Test public void detachedRupturedDiscDropsTheRupturedItem() {
        metadata=15;when(world.getTileEntity(15,128,5)).thenReturn(null);discBlock.onNeighborBlockChange(world,16,128,5,mock(Block.class));
        assertEquals(1,discBlock.dropped);verify(world).setBlockToAir(16,128,5);
    }
    @Test public void clientNeighborChangesCannotRemoveAttachments() {
        world.isRemote=true;when(world.getTileEntity(15,128,5)).thenReturn(null);gaugeBlock.onNeighborBlockChange(world,16,128,5,mock(Block.class));discBlock.onNeighborBlockChange(world,16,128,5,mock(Block.class));
        verify(world,never()).setBlockToAir(anyInt(),anyInt(),anyInt());assertEquals(-1,discBlock.dropped);
    }
    @Test public void discKeepsBurstThresholdAndVentingRate() {
        when(source.getPressure()).thenReturn(1.1F);disc.updateEntity();assertEquals(5,metadata);
        when(source.getPressure()).thenReturn(1.1001F);final int[] steam={125};when(source.getSteamShare()).thenAnswer(i -> steam[0]);doAnswer(i -> {steam[0]=Math.max(0,steam[0]-(int)i.getArgument(0));return null;}).when(source).decrSteam(anyInt());
        disc.updateEntity();assertEquals(15,metadata);assertEquals(25,steam[0]);disc.updateEntity();assertEquals(0,steam[0]);
    }
    @Test public void boundsQueriesDoNotLoadSupportChunks() {
        when(world.blockExists(15,128,5)).thenReturn(false);gaugeBlock.setBlockBoundsBasedOnState(world,16,128,5);discBlock.setBlockBoundsBasedOnState(world,16,128,5);
        verify(world,never()).getTileEntity(15,128,5);
    }
}
