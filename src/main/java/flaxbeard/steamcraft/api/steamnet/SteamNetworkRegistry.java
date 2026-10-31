package flaxbeard.steamcraft.api.steamnet;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import flaxbeard.steamcraft.Steamcraft;
import flaxbeard.steamcraft.api.ISteamTransporter;
import flaxbeard.steamcraft.api.steamnet.data.SteamNetworkData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class SteamNetworkRegistry {
    private static boolean loaderRegistered = false;

    private static SteamNetworkRegistry INSTANCE = new SteamNetworkRegistry();
    private HashSet<Integer> initialized = new HashSet<>();
    private HashMap<Integer, HashMap<String, SteamNetwork>> networks = new HashMap<>();


    public static SteamNetworkRegistry getInstance() {
        return INSTANCE;
    }

    public static void initialize() {
        if (!loaderRegistered) {
            loaderRegistered = true;

            MinecraftForge.EVENT_BUS.register(new NetworkLoader());
            FMLCommonHandler.instance().bus().register(INSTANCE);
        }
    }

    public static void markDirty(SteamNetwork network) {
        World world = network.getWorld();
        if (world != null) {
            SteamNetworkData.get(world).markDirty();
        }
    }

    public NBTTagCompound writeToNBT(NBTTagCompound nbt, int dimID) {
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt, int dimID) {
        initialized.add(dimID);
    }

    public boolean isInitialized(int dim) {
        return initialized.contains(dim);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        // Refreshes and explosions can replace networks while this tick is in progress.
        for (HashMap<String, SteamNetwork> dimension : new ArrayList<>(networks.values())) {
            for (SteamNetwork net : new ArrayList<>(dimension.values())) {
                if (dimension.get(net.getName()) == net && !net.tick()) {
                    dimension.remove(net.getName());
                }
            }
        }
    }

    public SteamNetwork getNewNetwork() {
        //////Steamcraft.log.debug("Returning new network");
        SteamNetwork net = new SteamNetwork();
        String name = UUID.randomUUID().toString();
        //////Steamcraft.log.debug(name);
        net.setName(name);
        return net;
    }

    public void add(SteamNetwork network) {
        if (!networks.containsKey(network.getDimension())) {
            networks.put(network.getDimension(), new HashMap<String, SteamNetwork>());
        }
        HashMap<String, SteamNetwork> dimension = networks.get(network.getDimension());
        dimension.put(network.getName(), network);
        World world = network.getWorld();
        if (world != null) {
            SteamNetworkData.get(world).markDirty();
        }
        //printNetworks(network.getDimension());
    }

    public void remove(SteamNetwork network) {
        if (networks.containsKey(network.getDimension())) {
            HashMap<String, SteamNetwork> dimension = networks.get(network.getDimension());
            dimension.remove(network.getName());
            World world = network.getWorld();
            if (world != null) {
                SteamNetworkData.get(world).markDirty();
            }

        }
    }

    public SteamNetwork getNetwork(String name, ISteamTransporter trans) {
        int d = trans.getDimension();
        if (networks.containsKey(d)) {
            HashMap<String, SteamNetwork> dimension = networks.get(trans.getDimension());
            if (dimension.containsKey(name)) {
                return dimension.get(name);
            }
        }

        // else
        return SteamNetwork.newOrJoin(trans);


    }

    public void printNetworks(int dim) {
        for (SteamNetwork net : networks.get(dim).values()) {
            Steamcraft.log.debug(net.getName());
        }
    }

    public void newDimension(int dimensionId) {
        initialized.add(dimensionId);
    }

    public void clear() {
        networks.clear();
        initialized.clear();
    }

    public static class NetworkLoader {
        @SubscribeEvent
        public void onChunkUnload(ChunkEvent.Unload event) {
            if (event.world.isRemote) {
                return;
            }
            HashMap<SteamNetwork, HashSet<ISteamTransporter>> departing = new HashMap<>();
            for (Object tile : event.getChunk().chunkTileEntityMap.values()) {
                if (tile instanceof ISteamTransporter) {
                    ISteamTransporter trans = (ISteamTransporter) tile;
                    if (trans.getNetwork() != null) {
                        departing.computeIfAbsent(trans.getNetwork(), key -> new HashSet<>()).add(trans);
                    }
                }
            }
            for (SteamNetwork network : departing.keySet()) {
                network.unloadTransporters(departing.get(network));
            }
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload event) {
            if (!event.world.isRemote) {
                int dimension = event.world.provider.dimensionId;
                INSTANCE.networks.remove(dimension);
                INSTANCE.initialized.remove(dimension);
            }
        }
    }
}
