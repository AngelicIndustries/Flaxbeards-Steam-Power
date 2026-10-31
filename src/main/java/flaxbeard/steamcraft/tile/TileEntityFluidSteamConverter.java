package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.Config;
import flaxbeard.steamcraft.api.ISteamTransporter;
import flaxbeard.steamcraft.api.IWrenchable;
import flaxbeard.steamcraft.api.steamnet.SteamNetwork;
import flaxbeard.steamcraft.api.tile.SteamTransporterTileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.*;

public class TileEntityFluidSteamConverter extends SteamTransporterTileEntity implements ISteamTransporter, IFluidHandler, IWrenchable {
    public int runTicks = 0;
    private boolean lastRunning = false;

    public boolean pushing = false; // Indicates that converter is pushing steam actively.
    private static final int PUSH_MAX = 250; // in mb a tick

    public TileEntityFluidSteamConverter() {
        // Do not join neighbors through arbitrary faces before the first server tick.
        super(new ForgeDirection[0]);
    }

    @Override
    public void refresh() {
        setDistributionDirections(new ForgeDirection[]{fluidSide().getOpposite()});
        super.refresh();
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound access = super.getDescriptionTag();
        access.setShort("runTicks", (short) runTicks);
        access.setBoolean("pushing", pushing);

        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, access);
    }


    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        super.onDataPacket(net, pkt);
        NBTTagCompound access = pkt.func_148857_g();
        if (runTicks == 0 && access.getShort("runTicks") != 0) {
            runTicks = access.getShort("runTicks");
        }
        pushing = access.getBoolean("pushing");
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void writeToNBT(NBTTagCompound par1NBTTagCompound) {
        super.writeToNBT(par1NBTTagCompound);
        par1NBTTagCompound.setBoolean("pushing", pushing);
    }

    @Override
    public void readFromNBT(NBTTagCompound par1NBTTagCompound) {
        super.readFromNBT(par1NBTTagCompound);

        pushing = par1NBTTagCompound.getBoolean("pushing");
    }

    @Override
    public void updateEntity() {
        if (runTicks > 0) {
            runTicks--;
        }
        if (runTicks > 0 != lastRunning && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        lastRunning = runTicks > 0;
        super.updateEntity();

        if (!pushing || !canTransfer()) {
            return;
        }
        ForgeDirection dir = fluidSide();
        if (dir == ForgeDirection.UNKNOWN) {
            return;
        }
        TileEntity neighbor = SteamNetwork.getLoadedTile(worldObj,
            xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
        if (neighbor instanceof IFluidHandler && !neighbor.isInvalid()) {
            // Preserve pressure-based output, including the existing overpressure behavior.
            int maxDrain = (int) (PUSH_MAX * getNetwork().getPressure());
            FluidStack available = drain(dir, maxDrain, false);
            if (available != null) {
                int offered = available.amount;
                int accepted = ((IFluidHandler) neighbor).fill(dir.getOpposite(), available, true);
                int transferred = Math.max(0, Math.min(offered, accepted));
                if (transferred > 0) {
                    decrSteam(transferred);
                    transferredSteam();
                }
            }
        }
    }

    @Override
    public boolean onWrench(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float xO, float yO, float zO) {
        if (world.isRemote) {
            return false;
        }
        if (player.isSneaking()) {
            pushing = !pushing;
        } else {
            if (this.getNetwork() != null) {
                this.getNetwork().split(this, true);
            }
            this.setDistributionDirections(new ForgeDirection[]{ForgeDirection.getOrientation(this.worldObj.getBlockMetadata(xCoord, yCoord, zCoord)).getOpposite()});

            SteamNetwork.newOrJoin(this);
        }
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return true;
    }

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        if (!canTransfer() || resource == null || resource.amount <= 0 || !canFill(from, resource.getFluid())) {
            return 0;
        }
        // Steam networks can exceed nominal capacity, but their integer storage must not wrap.
        int accepted = Math.min(resource.amount, Integer.MAX_VALUE - getNetwork().getSteam());
        if (doFill && accepted > 0) {
            insertSteam(accepted, from);
            transferredSteam();
        }
        return accepted;
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        if (resource == null || resource.tag != null) {
            return null;
        }
        return drainSteam(from, resource.getFluid(), resource.amount, doDrain);
    }

    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        return drainSteam(from, FluidRegistry.getFluid("steam"), maxDrain, doDrain);
    }

    private FluidStack drainSteam(ForgeDirection from, Fluid fluid, int requested, boolean doDrain) {
        if (!canTransfer() || requested <= 0 || !canDrain(from, fluid)) {
            return null;
        }
        int drained = Math.min(requested, availableSteam());
        if (drained <= 0) {
            return null;
        }
        if (doDrain) {
            decrSteam(drained);
            transferredSteam();
        }
        return new FluidStack(fluid, drained);
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return !pushing && isFluidSide(from) && isSteam(fluid);
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return isFluidSide(from) && isSteam(fluid);
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        Fluid steam = FluidRegistry.getFluid("steam");
        if (!isFluidSide(from) || steam == null) {
            return new FluidTankInfo[0];
        }
        // Both steam fluid names represent the same store. Advertise that store only once.
        return new FluidTankInfo[]{new FluidTankInfo(new FluidStack(steam, availableSteam()), getCapacity())};
    }

    private int availableSteam() {
        SteamNetwork network = getNetwork();
        if (network == null || network.getCapacity() <= 0) {
            return 0;
        }
        // Integer arithmetic keeps the last mB accessible instead of rounding pressure down.
        return (int) Math.max(0, Math.min(network.getSteam(),
            (long) network.getSteam() * getCapacity() / network.getCapacity()));
    }

    private ForgeDirection fluidSide() {
        return worldObj == null ? ForgeDirection.UNKNOWN
            : ForgeDirection.getOrientation(worldObj.getBlockMetadata(xCoord, yCoord, zCoord));
    }

    private boolean isFluidSide(ForgeDirection from) {
        return from != null && from != ForgeDirection.UNKNOWN && from == fluidSide();
    }

    private boolean isSteam(Fluid fluid) {
        // Registry presence avoids an IC2 class dependency and tolerates missing optional fluids.
        return fluid != null && (fluid == FluidRegistry.getFluid("steam")
            || (Config.enableIC2Integration && fluid == FluidRegistry.getFluid("ic2steam")));
    }

    private boolean canTransfer() {
        return worldObj != null && !worldObj.isRemote && !isInvalid() && getNetwork() != null;
    }

    private void transferredSteam() {
        markDirty();
        if (runTicks == 0) {
            runTicks = 100;
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }
}
