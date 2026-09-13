package dev.xkmc.youkaishomecoming.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraftforge.client.ClientCommandHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Route spell AI chat links through the client commands under /yhspell ai. */
@Mixin(ClientPacketListener.class)
public abstract class SpellAiClientCommandMixin {

	@Inject(method = "sendUnsignedCommand", at = @At("HEAD"), cancellable = true)
	private void youkaishomecoming$runSpellAiCommand(String command, CallbackInfoReturnable<Boolean> callback) {
		// In 1.20.1, RUN_COMMAND clicks bypass Forge's hook in sendCommand.
		if (command.startsWith("yhspell ai ") && ClientCommandHandler.runCommand(command)) {
			callback.setReturnValue(true);
		}
	}
}
