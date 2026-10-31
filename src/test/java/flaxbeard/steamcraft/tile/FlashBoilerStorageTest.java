package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.SteamcraftBlocks;
import flaxbeard.steamcraft.block.BlockFlashBoiler;
import flaxbeard.steamcraft.test.ForgeFluidTestSupport;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FlashBoilerStorageTest {
    private static final Item ITEM = new Item();
    private World world;
    private Block oldBlock;
    private final TileEntityFlashBoiler[] boilers = new TileEntityFlashBoiler[8];
    private final int[] metadata = new int[8];
    private final List<ItemStack> dropped = new ArrayList<>();
    public static class Flash extends TileEntityFlashBoiler { @Override public void refresh() {} }

    @BeforeClass public static void bootstrap() throws Exception {
        ForgeFluidTestSupport.bootstrap();
        TileEntity.addMapping(Flash.class,"flash_storage:tile");
        java.lang.reflect.Method register=Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);
        register.setAccessible(true); register.invoke(Item.itemRegistry,30025,"flash_storage:item",ITEM);
    }
    private int index(Object[] args) {
        int x=(int)args[0],y=(int)args[1]-128,z=(int)args[2];
        return x>=0&&x<2&&y>=0&&y<2&&z>=0&&z<2 ? x+2*z+4*y : -1;
    }
    @Before public void setup() throws Exception {
        oldBlock=SteamcraftBlocks.flashBoiler; SteamcraftBlocks.flashBoiler=new BlockFlashBoiler();
        world=mock(World.class); Block air=mock(Block.class);
        java.lang.reflect.Field provider=World.class.getDeclaredField("provider"); provider.setAccessible(true);
        provider.set(world,mock(net.minecraft.world.WorldProvider.class));
        when(world.blockExists(anyInt(),anyInt(),anyInt())).thenReturn(true);
        when(world.getBlock(anyInt(),anyInt(),anyInt())).thenAnswer(i -> index(i.getArguments())<0?air:SteamcraftBlocks.flashBoiler);
        when(world.getTileEntity(anyInt(),anyInt(),anyInt())).thenAnswer(i -> index(i.getArguments())<0?null:boilers[index(i.getArguments())]);
        when(world.getBlockMetadata(anyInt(),anyInt(),anyInt())).thenAnswer(i -> index(i.getArguments())<0?0:metadata[index(i.getArguments())]);
        when(world.setBlockMetadataWithNotify(anyInt(),anyInt(),anyInt(),anyInt(),anyInt())).thenAnswer(i -> {
            int n=index(i.getArguments()); metadata[n]=i.getArgument(3); boilers[n].updateContainingBlockInfo(); return true;
        });
        when(world.spawnEntityInWorld(any())).thenAnswer(i -> { dropped.add(((EntityItem)i.getArgument(0)).getEntityItem().copy()); return true; });
        for(int i=0;i<8;i++) {
            boilers[i]=new Flash(); boilers[i].setWorldObj(world);
            boilers[i].xCoord=i%2; boilers[i].yCoord=128+i/4; boilers[i].zCoord=i/2%2; metadata[i]=i+1;
        }
    }
    @After public void cleanup() { SteamcraftBlocks.flashBoiler=oldBlock; }
    private ItemStack named(int amount,String name) { return new ItemStack(ITEM,amount).setStackDisplayName(name); }
    private void localContents(int i,ItemStack stack) {
        int old=metadata[i]; metadata[i]=1; boilers[i].setInventorySlotContents(0,stack); metadata[i]=old; boilers[i].updateContainingBlockInfo();
    }
    private void breakMember(int i) {
        TileEntityFlashBoiler b=boilers[i]; SteamcraftBlocks.flashBoiler.onBlockPreDestroy(world,b.xCoord,b.yCoord,b.zCoord,metadata[i]);
    }
    private int count(String name) { return dropped.stream().filter(s -> s.getDisplayName().equals(name)).mapToInt(s -> s.stackSize).sum(); }
    private NBTTagCompound save(int i) { NBTTagCompound n=new NBTTagCompound();boilers[i].writeToNBT(n);return n; }

    @Test public void breakingFormerPrimaryDropsStoredInventoryOnceWithTags() {
        localContents(0,named(37,"stored")); boilers[0].destroyMultiblock();
        breakMember(0); breakMember(0);
        assertEquals(37,count("stored")); assertEquals(0,save(0).getTagList("Items",10).tagCount());
    }
    @Test public void breakingSecondaryDropsPrimaryAndItsOwnLegacyContentsOnce() {
        localContents(0,named(13,"primary")); localContents(5,named(7,"secondary"));
        breakMember(5); breakMember(5); breakMember(0);
        assertEquals(13,count("primary")); assertEquals(7,count("secondary"));
        assertEquals(0,save(0).getTagList("Items",10).tagCount()); assertEquals(0,save(5).getTagList("Items",10).tagCount());
    }
    @Test public void missingPrimaryDoesNotPreventLocalDropsOrLoadPrimary() {
        localContents(5,named(7,"secondary")); when(world.blockExists(0,128,0)).thenReturn(false);
        clearInvocations(world); breakMember(5);
        assertEquals(7,count("secondary")); verify(world,never()).getTileEntity(0,128,0);
    }
    @Test public void clientBreakDoesNotDropOrClearInventory() {
        localContents(0,named(13,"primary")); world.isRemote=true;breakMember(0);
        assertEquals(0,dropped.size());assertEquals(13,boilers[0].getStackInSlot(0).stackSize);
    }
    @Test public void secondaryDoesNotBurnItsStrandedFuel() {
        boilers[5].furnaceBurnTime=50;boilers[5].currentItemBurnTime=80;
        boilers[5].updateEntity();boilers[5].updateEntity();
        assertEquals(50,boilers[5].furnaceBurnTime);assertEquals(80,boilers[5].currentItemBurnTime);
    }
    @Test public void detachedContentsSurviveSaveBeforeTheBlockIsBroken() {
        localContents(0,named(37,"stored")); boilers[0].destroyMultiblock();
        NBTTagCompound tag=save(0); Flash loaded=new Flash();loaded.setWorldObj(world);loaded.readFromNBT(tag);boilers[0]=loaded;
        breakMember(0);assertEquals(37,count("stored"));
    }
}
