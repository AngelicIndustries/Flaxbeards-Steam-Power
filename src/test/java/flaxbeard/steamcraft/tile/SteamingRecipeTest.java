package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.api.SteamcraftRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.*;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.MutablePair;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SteamingRecipeTest {
    private static final Item INPUT=new Item(),COOKED=new Item(),STEAMED=new Item(),WILDCARD=new Item();
    private HashMap<MutablePair<Item,Integer>,MutablePair<Item,Integer>> previous;
    private final ItemStack recipeInput=new ItemStack(INPUT);
    private TileEntitySteamFurnace furnace;
    private TileEntitySteamHeater heater;
    private World world;
    @Before public void setup(){
        previous=SteamcraftRegistry.steamingRecipes;SteamcraftRegistry.steamingRecipes=new HashMap<>();
        FurnaceRecipes.smelting().getSmeltingList().put(recipeInput,new ItemStack(COOKED,1,3));
        world=mock(World.class);when(world.blockExists(anyInt(),anyInt(),anyInt())).thenReturn(true);when(world.getBlock(anyInt(),anyInt(),anyInt())).thenReturn(mock(Block.class));
        furnace=new TileEntitySteamFurnace();furnace.setWorldObj(world);when(world.getTileEntity(0,0,0)).thenReturn(furnace);
        furnace.setInventorySlotContents(0,new ItemStack(INPUT,2));heater=new TileEntitySteamHeater();
    }
    @After public void cleanup(){SteamcraftRegistry.steamingRecipes=previous;FurnaceRecipes.smelting().getSmeltingList().remove(recipeInput);}
    private void exact(){SteamcraftRegistry.addSteamingRecipe(COOKED,3,STEAMED,2);}
    @Test public void fullSteamedOutputBlocksBothHeaterAndFurnace(){
        exact();furnace.setInventorySlotContents(2,new ItemStack(STEAMED,64,2));assertFalse(heater.canSmelt(furnace));assertFalse(furnace.canSmelt());
    }
    @Test public void exactRecipeWinsOverWildcardForBothChecksAndOutput(){
        exact();SteamcraftRegistry.addSteamingRecipe(COOKED,-1,WILDCARD,4);furnace.setInventorySlotContents(2,new ItemStack(STEAMED,1,2));
        assertTrue(heater.canSmelt(furnace));assertTrue(furnace.canSmelt());furnace.smeltItem();assertEquals(2,furnace.getStackInSlot(2).stackSize);assertEquals(1,furnace.getStackInSlot(0).stackSize);
    }
    @Test public void wildcardDoesNotChainIntoAnUnrelatedSecondRecipe(){
        SteamcraftRegistry.addSteamingRecipe(COOKED,-1,WILDCARD,4);SteamcraftRegistry.addSteamingRecipe(WILDCARD,4,STEAMED,2);
        furnace.setInventorySlotContents(2,new ItemStack(WILDCARD,1,4));assertTrue(heater.canSmelt(furnace));assertTrue(furnace.canSmelt());
    }
    @Test public void emptyInputCannotGenerateOutput(){
        furnace.setInventorySlotContents(0,new ItemStack(INPUT,0));assertFalse(heater.canSmelt(furnace));assertFalse(furnace.canSmelt());furnace.smeltItem();assertNull(furnace.getStackInSlot(2));
    }
    @Test public void oversizedRecipeCannotBeTruncatedIntoAnEmptySlot(){
        FurnaceRecipes.smelting().getSmeltingList().put(recipeInput,new ItemStack(COOKED,65,3));assertFalse(heater.canSmelt(furnace));assertFalse(furnace.canSmelt());
    }
    @Test public void outputTagsMustMatchBeforeMerging(){
        FurnaceRecipes.smelting().getSmeltingList().put(recipeInput,new ItemStack(COOKED,1,3).setStackDisplayName("result"));
        furnace.setInventorySlotContents(2,new ItemStack(COOKED,1,3).setStackDisplayName("other"));assertFalse(heater.canSmelt(furnace));assertFalse(furnace.canSmelt());
    }
    @Test public void clientCannotSmeltOrConsumeInputs(){
        world.isRemote=true;furnace.smeltItem();assertEquals(2,furnace.getStackInSlot(0).stackSize);assertNull(furnace.getStackInSlot(2));
    }
    @Test public void publicSmeltingSavesChangesAndKeepsRecipeTemplateIntact(){
        ItemStack template=new ItemStack(COOKED,3,3).setStackDisplayName("result");FurnaceRecipes.smelting().getSmeltingList().put(recipeInput,template);
        furnace.smeltItem();assertEquals(3,furnace.getStackInSlot(2).stackSize);assertEquals(1,furnace.getStackInSlot(0).stackSize);assertEquals(3,template.stackSize);
        assertNotSame(template,furnace.getStackInSlot(2));verify(world).markTileEntityChunkModified(0,0,0,furnace);
    }
}
