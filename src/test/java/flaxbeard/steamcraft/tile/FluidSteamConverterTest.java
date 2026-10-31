package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.api.steamnet.SteamNetwork;
import net.minecraft.block.Block;
import flaxbeard.steamcraft.Config;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;
import org.junit.*;
import java.lang.reflect.Field;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FluidSteamConverterTest {
    private static Fluid steam, ic2Steam;
    private World world;
    private TileEntityFluidSteamConverter converter;
    private SteamNetwork network;
    private boolean ic2Enabled;
    private static final ForgeDirection FACE = ForgeDirection.EAST;

    @BeforeClass public static void registerFluids() throws Exception {
        flaxbeard.steamcraft.test.ForgeFluidTestSupport.bootstrap();
        steam = FluidRegistry.getFluid("steam");
        ic2Steam = FluidRegistry.getFluid("ic2steam");
    }

    @Before public void setUp() {
        ic2Enabled = Config.enableIC2Integration;
        Config.enableIC2Integration = true;
        world = mock(World.class);
        when(world.getBlockMetadata(0, 0, 0)).thenReturn(FACE.ordinal());
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(mock(Block.class));
        when(world.blockExists(anyInt(), anyInt(), anyInt())).thenReturn(true);
        converter = new TileEntityFluidSteamConverter();
        converter.setWorldObj(world);
        network = new SteamNetwork(10000);
        converter.setNetwork(network);
    }

    @After public void restoreConfig() { Config.enableIC2Integration = ic2Enabled; }

    @Test public void capabilitiesMatchFluidFaceAndSupportedFluids() {
        for (ForgeDirection side : ForgeDirection.values()) {
            assertEquals(side == FACE, converter.canFill(side, steam));
            assertEquals(side == FACE, converter.canDrain(side, steam));
            assertEquals(side == FACE ? 1 : 0, converter.getTankInfo(side).length);
        }
        assertFalse(converter.canFill(FACE, FluidRegistry.WATER));
        assertFalse(converter.canDrain(FACE, FluidRegistry.WATER));
        assertFalse(converter.canFill(null, steam));
        assertFalse(converter.canDrain(FACE, null));
        assertEquals(0, converter.getTankInfo(null).length);
    }

    @Test public void simulatedFillDoesNotChangeSteamOrAnimation() {
        FluidStack input = new FluidStack(steam, 700);
        assertEquals(700, converter.fill(FACE, input, false));
        assertEquals(0, network.getSteam());
        assertEquals(0, converter.runTicks);
        assertEquals(700, input.amount);
        assertEquals(700, converter.fill(FACE, input, true));
        assertEquals(700, network.getSteam());
        assertTrue(converter.runTicks > 0);
    }

    @Test public void uninitializedNetworkRejectsSimulatedAndActualTransfers() {
        converter.setNetwork(null);
        assertEquals(0, converter.fill(FACE, new FluidStack(steam, 100), false));
        assertEquals(0, converter.fill(FACE, new FluidStack(steam, 100), true));
        assertNull(converter.drain(FACE, 100, true));
        assertEquals(0, converter.runTicks);
    }

    @Test public void fillRejectsInvalidRequests() {
        assertEquals(0, converter.fill(FACE, null, true));
        assertEquals(0, converter.fill(null, new FluidStack(steam, 1), true));
        assertEquals(0, converter.fill(FACE, new FluidStack(steam, -100), true));
        assertEquals(0, converter.fill(FACE, new FluidStack(steam, 0), true));
        assertEquals(0, converter.fill(FACE, new FluidStack(FluidRegistry.WATER, 100), true));
        assertEquals(0, network.getSteam());
    }

    @Test public void inputCanPressurizeNetworkButCannotOverflowIntegerStorage() {
        assertEquals(12000, converter.fill(FACE, new FluidStack(steam, 12000), true));
        network.addSteam(Integer.MAX_VALUE - 12000 - 50);
        assertEquals(50, converter.fill(FACE, new FluidStack(steam, 100), false));
        assertEquals(50, converter.fill(FACE, new FluidStack(steam, 100), true));
        assertEquals(Integer.MAX_VALUE, network.getSteam());
        assertEquals(0, converter.fill(FACE, new FluidStack(steam, 1), true));
    }

    @Test public void registeredIc2SteamConvertsOneForOneInBothDirections() {
        assertEquals(2000, converter.fill(FACE, new FluidStack(ic2Steam, 2000), true));
        FluidStack output = converter.drain(FACE, new FluidStack(ic2Steam, 300), true);
        assertSame(ic2Steam, output.getFluid());
        assertEquals(300, output.amount);
        assertEquals(1700, network.getSteam());
        assertSame(steam, converter.drain(FACE, 100, true).getFluid());
        assertEquals(1600, network.getSteam());
    }

    @Test public void simulatedDrainDoesNotConsumeOrAnimate() {
        network.addSteam(2000);
        FluidStack request = new FluidStack(steam, 300);
        assertEquals(300, converter.drain(FACE, request, false).amount);
        assertEquals(300, converter.drain(FACE, 300, false).amount);
        assertEquals(300, request.amount);
        assertEquals(2000, network.getSteam());
        assertEquals(0, converter.runTicks);
    }

    @Test public void drainRejectsWrongFluidAndInvalidRequests() {
        network.addSteam(2000);
        assertNull(converter.drain(FACE, new FluidStack(FluidRegistry.WATER, 500), true));
        assertNull(converter.drain(FACE, (FluidStack) null, true));
        assertNull(converter.drain(null, 500, true));
        assertNull(converter.drain(FACE, 0, true));
        assertNull(converter.drain(FACE, -100, true));
        assertNull(converter.drain(FACE, new FluidStack(steam, -100), true));
        assertEquals(2000, network.getSteam());
    }

    @Test public void emptyDrainReturnsNull() {
        assertNull(converter.drain(FACE, 100, false));
        assertNull(converter.drain(FACE, new FluidStack(steam, 100), true));
    }

    @Test public void extractionIsLimitedToConverterShareAndDebitedOnce() {
        network = new SteamNetwork(20000);
        converter.setNetwork(network);
        network.addSteam(4000);
        assertEquals(2000, converter.drain(FACE, 5000, true).amount);
        assertEquals(2000, network.getSteam());
    }

    @Test public void pushingDisablesInputButKeepsOutputAvailable() {
        converter.pushing = true;
        network.addSteam(2000);
        assertFalse(converter.canFill(FACE, steam));
        assertEquals(0, converter.fill(FACE, new FluidStack(steam, 100), true));
        assertTrue(converter.canDrain(FACE, steam));
        assertEquals(100, converter.drain(FACE, 100, true).amount);
        assertEquals(1900, network.getSteam());
    }

    @Test public void partialPushOnlyDebitsAmountAcceptedByNeighborAndAnimates() {
        TileFluidHandler target = new TileFluidHandler() {
            @Override public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
                assertEquals(ForgeDirection.WEST, from);
                return super.fill(from, resource, doFill);
            }
        };
        target.fill(ForgeDirection.WEST, new FluidStack(steam, 990), true);
        when(world.getTileEntity(1, 0, 0)).thenReturn(target);
        converter.pushing = true;
        network.addSteam(4000);
        converter.updateEntity();
        assertEquals(1000, target.getTankInfo(ForgeDirection.WEST)[0].fluid.amount);
        assertEquals(3990, network.getSteam());
        assertTrue(converter.runTicks > 0);
        converter.updateEntity();
        assertEquals(3990, network.getSteam());
    }

    @Test public void pushDoesNotReadUnloadedNeighbor() {
        converter.pushing = true;
        network.addSteam(4000);
        when(world.blockExists(1, 0, 0)).thenReturn(false);
        converter.updateEntity();
        verify(world, never()).getTileEntity(1, 0, 0);
        assertEquals(4000, network.getSteam());
    }

    @Test public void clientCannotTransferSteam() {
        world.isRemote = true;
        converter.pushing = true;
        network.addSteam(4000);
        TileFluidHandler target = new TileFluidHandler();
        when(world.getTileEntity(1, 0, 0)).thenReturn(target);
        converter.updateEntity();
        converter.pushing = false;
        assertEquals(0, converter.fill(FACE, new FluidStack(steam, 100), true));
        assertNull(converter.drain(FACE, 100, true));
        assertNull(target.getTankInfo(FACE)[0].fluid);
        assertEquals(4000, network.getSteam());
    }

    @Test public void disabledIc2IntegrationRejectsItsFluid() {
        Config.enableIC2Integration = false;
        network.addSteam(2000);
        assertFalse(converter.canFill(FACE, ic2Steam));
        assertFalse(converter.canDrain(FACE, ic2Steam));
        assertEquals(0, converter.fill(FACE, new FluidStack(ic2Steam, 100), true));
        assertNull(converter.drain(FACE, new FluidStack(ic2Steam, 100), true));
        assertEquals(2000, network.getSteam());
    }

    @Test public void wrongFaceCannotTransferAndTaggedDrainDoesNotMatchPlainSteam() {
        network.addSteam(2000);
        assertEquals(0, converter.fill(ForgeDirection.WEST, new FluidStack(steam, 100), true));
        assertNull(converter.drain(ForgeDirection.WEST, 100, true));
        assertNull(converter.drain(ForgeDirection.WEST, new FluidStack(steam, 100), true));
        FluidStack tagged = new FluidStack(steam, 100, new NBTTagCompound());
        tagged.tag.setString("test", "different fluid stack");
        assertNull(converter.drain(FACE, tagged, true));
        assertEquals(2000, network.getSteam());
    }

    @Test public void badNeighborReturnCannotAddSteamOrDebitMoreThanOffered() {
        TileFluidHandler target = mock(TileFluidHandler.class);
        when(world.getTileEntity(1, 0, 0)).thenReturn(target);
        converter.pushing = true;
        network.addSteam(4000);
        when(target.fill(any(), any(), eq(true))).thenReturn(-50);
        converter.updateEntity();
        assertEquals(4000, network.getSteam());
        when(target.fill(any(), any(), eq(true))).thenReturn(Integer.MAX_VALUE);
        converter.updateEntity();
        assertEquals(3900, network.getSteam());
    }

    @Test public void lastUnitOfSteamCanBeExtractedWithoutFloatingPointLoss() {
        network.addSteam(1);
        assertEquals(1, converter.getTankInfo(FACE)[0].fluid.amount);
        FluidStack out = converter.drain(FACE, 1, true);
        assertNotNull(out);
        assertEquals(1, out.amount);
        assertEquals(0, network.getSteam());
    }

    @Test public void missingRegisteredFluidsAreSafe() throws Exception {
        Field field = FluidRegistry.class.getDeclaredField("fluids");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Map<String, Fluid> fluids =
            (java.util.Map<String, Fluid>) field.get(null);
        Fluid savedSteam = fluids.remove("steam"), savedIc2 = fluids.remove("ic2steam");
        try {
            network.addSteam(2000);
            assertFalse(converter.canFill(FACE, steam));
            assertFalse(converter.canFill(FACE, ic2Steam));
            assertNull(converter.drain(FACE, 100, true));
            assertEquals(0, converter.getTankInfo(FACE).length);
            assertEquals(2000, network.getSteam());
        } finally {
            fluids.put("steam", savedSteam);
            fluids.put("ic2steam", savedIc2);
        }
    }

    @Test public void invalidTileCannotTransferSteam() {
        converter.invalidate();
        assertEquals(0, converter.fill(FACE, new FluidStack(steam, 100), true));
        assertNull(converter.drain(FACE, 100, true));
    }
}
