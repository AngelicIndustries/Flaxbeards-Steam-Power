package flaxbeard.steamcraft.tile;

import net.minecraft.block.Block;
import net.minecraft.item.*;
import net.minecraft.tileentity.*;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SteamHeaterLifecycleTest {
    private World world;
    private final Map<String,TileEntity> tiles=new HashMap<>();
    private final Map<String,Integer> metadata=new HashMap<>();
    private static final Item ITEM=new Item();
    private static class Heater extends TileEntitySteamHeater {
        TileEntityFurnace checked;
        @Override public void refresh() {}
        @Override public int getSteamShare() { return 100; }
        @Override public boolean canSmelt(TileEntityFurnace furnace) { checked=furnace;return false; }
    }
    @Before public void setup() {
        world=mock(World.class);when(world.blockExists(anyInt(),anyInt(),anyInt())).thenReturn(true);
        when(world.getBlock(anyInt(),anyInt(),anyInt())).thenReturn(mock(Block.class));
        when(world.getTileEntity(anyInt(),anyInt(),anyInt())).thenAnswer(i -> tiles.get(key(i.getArgument(0),i.getArgument(1),i.getArgument(2))));
        when(world.getBlockMetadata(anyInt(),anyInt(),anyInt())).thenAnswer(i -> metadata.getOrDefault(key(i.getArgument(0),i.getArgument(1),i.getArgument(2)),0));
        doAnswer(i -> {int x=i.getArgument(0),y=i.getArgument(1),z=i.getArgument(2);TileEntity previous=tiles.get(key(x,y,z));if(previous!=null)previous.invalidate();place(i.getArgument(3),x,y,z);return null;}).when(world).setTileEntity(anyInt(),anyInt(),anyInt(),any());
    }
    private String key(int x,int y,int z){return x+","+y+","+z;}
    private <T extends TileEntity> T place(T tile,int x,int y,int z){tile.setWorldObj(world);tile.xCoord=x;tile.yCoord=y;tile.zCoord=z;tiles.put(key(x,y,z),tile);return tile;}
    private Heater heater(){Heater h=place(new Heater(),15,128,5);metadata.put(key(15,128,5),5);h.steamConsumption=20;return h;}
    private TileEntitySteamFurnace furnace(){return place(new TileEntitySteamFurnace(),16,128,5);}
    private void state(TileEntityFurnace f){f.func_145951_a("Named furnace");f.furnaceBurnTime=100000;f.currentItemBurnTime=120000;f.furnaceCookTime=80;f.setInventorySlotContents(2,new ItemStack(ITEM,7).setStackDisplayName("Stored output"));}

    @Test public void clientHeaterCannotReplaceFurnacesOrRegisterItselfAgain(){
        Heater h=heater();place(new TileEntityFurnace(),16,128,5);world.isRemote=true;h.updateEntity();
        verify(world,never()).setTileEntity(anyInt(),anyInt(),anyInt(),any());verify(world,never()).addTileEntity(any());
    }
    @Test public void clientSteamFurnaceDoesNotReplaceOrAdvanceCounters(){
        TileEntitySteamFurnace f=furnace();state(f);world.isRemote=true;f.updateEntity();
        verify(world,never()).setTileEntity(anyInt(),anyInt(),anyInt(),any());assertEquals(100000,f.furnaceBurnTime);assertEquals(80,f.furnaceCookTime);
    }
    @Test public void unloadedFurnaceIsNeverLookedUpByHeater(){
        Heater h=heater();when(world.blockExists(16,128,5)).thenReturn(false);h.updateEntity();verify(world,never()).getTileEntity(16,128,5);
    }
    @Test public void unloadedHeaterAreaPausesFurnaceWithoutLoadingOrReplacing(){
        TileEntitySteamFurnace f=furnace();state(f);when(world.blockExists(15,128,5)).thenReturn(false);when(world.blockExists(14,128,5)).thenReturn(false);f.updateEntity();
        verify(world,never()).getTileEntity(15,128,5);verify(world,never()).setTileEntity(anyInt(),anyInt(),anyInt(),any());assertEquals(100000,f.furnaceBurnTime);assertEquals(80,f.furnaceCookTime);
    }
    @Test public void revertingPreservesNamesStacksAndLongCountersWithoutSharedInventory(){
        TileEntitySteamFurnace f=furnace();state(f);TileEntitySteamHeater.replace(f);TileEntityFurnace replacement=(TileEntityFurnace)tiles.get(key(16,128,5));
        assertEquals(TileEntityFurnace.class,replacement.getClass());assertEquals("Named furnace",replacement.getInventoryName());assertEquals(100000,replacement.furnaceBurnTime);assertEquals(120000,replacement.currentItemBurnTime);assertEquals(80,replacement.furnaceCookTime);
        assertEquals(7,replacement.getStackInSlot(2).stackSize);assertEquals("Stored output",replacement.getStackInSlot(2).getDisplayName());assertNotSame(f.getStackInSlot(2),replacement.getStackInSlot(2));
        verify(world).markTileEntityChunkModified(16,128,5,replacement);
    }
    @Test public void replacedSteamFurnaceStopsTickingItsOldState(){
        TileEntitySteamFurnace f=furnace();state(f);f.updateEntity();assertEquals(100000,f.furnaceBurnTime);assertEquals(80,f.furnaceCookTime);
    }
    @Test public void replacementRejectsClientStaleInvalidAndUnloadedOwners(){
        TileEntitySteamFurnace f=furnace();world.isRemote=true;TileEntitySteamHeater.replace(f);world.isRemote=false;
        tiles.put(key(16,128,5),new TileEntityFurnace());TileEntitySteamHeater.replace(f);tiles.put(key(16,128,5),f);
        f.invalidate();TileEntitySteamHeater.replace(f);f.validate();when(world.blockExists(16,128,5)).thenReturn(false);TileEntitySteamHeater.replace(f);
        verify(world,never()).setTileEntity(anyInt(),anyInt(),anyInt(),any());
    }
    @Test public void upgradingUsesNewOwnerAndPreservesFurnaceName(){
        Heater h=heater();TileEntityFurnace f=place(new TileEntityFurnace(),16,128,5);f.func_145951_a("Named furnace");h.updateEntity();
        TileEntity replacement=tiles.get(key(16,128,5));assertTrue(replacement instanceof TileEntitySteamFurnace);assertSame(replacement,h.checked);assertEquals("Named furnace",((TileEntityFurnace)replacement).getInventoryName());
        verify(world,never()).addTileEntity(h);
    }
    @Test public void heaterConnectionSidesExcludeFrontAndUnknown(){
        Heater h=heater();h.updateEntity();assertEquals(5,h.getConnectionSides().size());assertFalse(h.getConnectionSides().contains(ForgeDirection.EAST));assertFalse(h.getConnectionSides().contains(ForgeDirection.UNKNOWN));
    }
    @Test public void missingTilesDoNotCauseDuplicateHeaterRegistration(){
        Heater h=heater();place(new TileEntityFurnace(),16,128,5);h.updateEntity();verify(world,never()).addTileEntity(h);
    }
    @Test public void activeFurnaceCounterChangesMarkTheCurrentOwnerDirty(){
        heater();TileEntitySteamFurnace f=furnace();state(f);clearInvocations(world);f.updateEntity();
        assertEquals(99999,f.furnaceBurnTime);verify(world).markTileEntityChunkModified(16,128,5,f);
    }

}
