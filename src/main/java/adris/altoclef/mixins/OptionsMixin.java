package adris.altoclef.mixins;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public class OptionsMixin {

    @Unique
    private Double altoclef$gammaBeforeSave;

    @Inject(method = "save", at = @At("HEAD"))
    private void clampGammaBeforeSave(CallbackInfo ci) {
        OptionInstance<Double> gamma = ((Options) (Object) this).gamma();
        double value = gamma.get();
        if (value >= 0.0 && value <= 1.0) {
            altoclef$gammaBeforeSave = null;
            return;
        }

        altoclef$gammaBeforeSave = value;
        gamma.set(Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 1.0);
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void restoreGammaAfterSave(CallbackInfo ci) {
        if (altoclef$gammaBeforeSave == null) return;

        ((Options) (Object) this).gamma().set(altoclef$gammaBeforeSave);
        altoclef$gammaBeforeSave = null;
    }
}
