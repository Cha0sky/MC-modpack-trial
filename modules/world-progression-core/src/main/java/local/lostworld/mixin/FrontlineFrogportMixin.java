package local.lostworld.mixin;

import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import local.lostworld.FrontlineFreight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FrogportBlockEntity.class, remap = false)
abstract class FrontlineFrogportMixin {
    // Create emits package_received after inserting the parcel, before clearing animatedPackage.
    // An outgoing parcel or an incoming parcel dropped because the port was full never reaches this branch.
    @Inject(method = "tick()V",
            slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=package_received")),
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/compat/computercraft/AbstractComputerBehaviour;prepareComputerEvent(Lcom/simibubi/create/compat/computercraft/events/ComputerEvent;)V"),
            require = 1)
    private void lostworld$recordFrontlineReceipt(CallbackInfo callback) {
        FrontlineFreight.received((FrogportBlockEntity) (Object) this);
    }
}
