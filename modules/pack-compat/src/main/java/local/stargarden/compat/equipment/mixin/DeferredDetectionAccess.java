package local.stargarden.compat.equipment.mixin;

import dev.ftb.mods.ftbquests.util.DeferredInventoryDetection;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = DeferredInventoryDetection.class, remap = false)
public interface DeferredDetectionAccess {
    @Invoker("scheduleInventoryCheck")
    static void stargarden$schedule(ServerPlayer player, int delay) { throw new AssertionError(); }
}
