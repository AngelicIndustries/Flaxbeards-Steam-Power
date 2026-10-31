package flaxbeard.steamcraft.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class HammerPersistenceTest {
    @BeforeClass
    public static void registerSaveType() {
        TileEntity.addMapping(TileEntitySteamHammer.class,"packet_test:hammer");
    }

    @Test
    public void requestedNameSurvivesReload() {
        TileEntitySteamHammer hammer = new TileEntitySteamHammer();
        hammer.itemName = "Checked Ingot";
        NBTTagCompound save = new NBTTagCompound();
        hammer.writeToNBT(save);
        TileEntitySteamHammer restored = new TileEntitySteamHammer();
        restored.readFromNBT(save);
        assertEquals("Checked Ingot",restored.itemName);
    }

    @Test
    public void olderSavesWithoutNameLoadWithEmptyName() {
        NBTTagCompound save = new NBTTagCompound();
        save.setTag("Items",new NBTTagList());
        TileEntitySteamHammer hammer = new TileEntitySteamHammer();
        hammer.itemName = "Previous contents";
        hammer.readFromNBT(save);
        assertEquals("",hammer.itemName);
    }
    @Test
    public void descriptionPacketsSynchronizeAndClearRequestedName() {
        TileEntitySteamHammer server = new TileEntitySteamHammer();
        TileEntitySteamHammer client = new TileEntitySteamHammer();
        client.setWorldObj(org.mockito.Mockito.mock(net.minecraft.world.World.class));
        server.itemName = "Checked Ingot";
        client.onDataPacket(null,(net.minecraft.network.play.server.S35PacketUpdateTileEntity)server.getDescriptionPacket());
        assertEquals("Checked Ingot",client.itemName);
        server.itemName = "";
        client.onDataPacket(null,(net.minecraft.network.play.server.S35PacketUpdateTileEntity)server.getDescriptionPacket());
        assertEquals("",client.itemName);
    }
}
