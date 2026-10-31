package flaxbeard.steamcraft.test;

import cpw.mods.fml.common.Loader;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraftforge.fluids.*;
import java.lang.reflect.Field;
import static org.mockito.Mockito.mock;

public final class ForgeFluidTestSupport {
    private ForgeFluidTestSupport() {}
    public static void bootstrap() throws Exception {
        // Fluid registration only needs an active mod name. Avoid starting FML's LaunchWrapper loader.
        Field instance = Loader.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        // Vanilla block registration is also absent in the unit-test JVM.
        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        java.util.Map<Field, Object> replacedBlocks = new java.util.HashMap<>();
        for (String name : new String[]{"water", "lava"}) {
            Field field = Blocks.class.getField(name);
            modifiers.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            field.setAccessible(true);
            if (field.get(null) == null) {
                replacedBlocks.put(field, null);
                field.set(null, mock(Block.class));
            }
        }
        instance.set(null, mock(Loader.class));
        try {
            if (!FluidRegistry.isFluidRegistered("steam")) FluidRegistry.registerFluid(new Fluid("steam"));
            if (!FluidRegistry.isFluidRegistered("ic2steam")) FluidRegistry.registerFluid(new Fluid("ic2steam"));

        } finally {
            instance.set(null, previous);
            for (Field field : replacedBlocks.keySet()) field.set(null, replacedBlocks.get(field));
        }
    }
}
