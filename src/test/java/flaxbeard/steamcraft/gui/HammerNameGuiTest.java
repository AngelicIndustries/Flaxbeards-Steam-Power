package flaxbeard.steamcraft.gui;

import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import flaxbeard.steamcraft.Steamcraft;
import flaxbeard.steamcraft.network.ItemNamePacket;
import flaxbeard.steamcraft.tile.TileEntitySteamHammer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HammerNameGuiTest {
    private GuiSteamAnvil gui;
    private GuiTextField field;
    private TileEntitySteamHammer hammer;
    private ContainerSteamAnvil container;
    private SimpleNetworkWrapper savedChannel;

    private void field(String name, Object value) throws Exception {
        Field f = GuiSteamAnvil.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(gui, value);
    }

    @Before
    public void setUp() throws Exception {
        savedChannel = Steamcraft.channel;
        Steamcraft.channel = mock(SimpleNetworkWrapper.class);
        gui = mock(GuiSteamAnvil.class, CALLS_REAL_METHODS);
        field = mock(GuiTextField.class);
        hammer = new TileEntitySteamHammer();
        container = mock(ContainerSteamAnvil.class);
        Slot input = mock(Slot.class);
        when(container.getSlot(0)).thenReturn(input);
        when(input.getHasStack()).thenReturn(true);
        when(input.getStack()).thenReturn(new ItemStack(new Item()));
        field("field_147091_w", field);
        field("field_147092_v", container);
        field("hammer", hammer);
        field("canEdit", true);
        Minecraft minecraft = mock(Minecraft.class);
        minecraft.thePlayer = mock(EntityClientPlayerMP.class);
        when(minecraft.thePlayer.isEntityAlive()).thenReturn(true);
        Field mc = GuiScreen.class.getDeclaredField("mc");
        mc.setAccessible(true);
        mc.set(gui, minecraft);
    }

    @After
    public void restoreChannel() { Steamcraft.channel = savedChannel; }

    private void edit(String name) throws Exception {
        when(field.getText()).thenReturn(name);
        Method update = GuiSteamAnvil.class.getDeclaredMethod("func_147090_g");
        update.setAccessible(true);
        update.invoke(gui);
    }

    @Test
    public void clearingTheFieldSendsAnEmptyName() throws Exception {
        hammer.itemName = "Old name";
        edit("");
        ArgumentCaptor<ItemNamePacket> packet = ArgumentCaptor.forClass(ItemNamePacket.class);
        verify(Steamcraft.channel).sendToServer(packet.capture());
        assertEquals("", packet.getValue().name);
    }

    @Test
    public void otherPlayersNameChangesUpdateAnUnfocusedFieldWithoutSending() {
        hammer.itemName = "Shared name";
        gui.updateScreen();
        verify(field).setText("Shared name");
        hammer.itemName = "";
        gui.updateScreen();
        verify(field).setText("");
        verifyNoInteractions(Steamcraft.channel);
    }

    @Test
    public void incomingNamesDoNotOverwriteTypingButApplyAfterFocusLeaves() {
        when(field.isFocused()).thenReturn(true);
        hammer.itemName = "Another player's name";
        gui.updateScreen();
        verify(field, never()).setText(anyString());
        when(field.isFocused()).thenReturn(false);
        gui.updateScreen();
        verify(field).setText("Another player's name");
    }

    @Test
    public void slotPacketsPreserveTheRequestedNameWithoutSendingAnEdit() {
        hammer.itemName = "Saved name";
        when(field.getText()).thenReturn("Unrelated name");
        gui.sendSlotContents(container, 0, new ItemStack(new Item()));
        verify(field).setText("Saved name");
        verifyNoInteractions(Steamcraft.channel);
    }

    @Test
    public void initializationDoesNotSendAnEdit() throws Exception {
        field("canEdit", false);
        edit("Saved name");
        verifyNoInteractions(Steamcraft.channel);
    }
}
