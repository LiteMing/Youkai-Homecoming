package dev.xkmc.youkaishomecoming.content.spell.client;

import dev.xkmc.l2serial.network.SerialPacketBase;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

/**
 * 服务端 -> 客户端：触发玩家施放/激活符卡时的图腾激活全屏动画特效
 */
@SerialClass
public class SpellCardActivationToClient extends SerialPacketBase {

	@SerialClass.SerialField
	public ItemStack stack = ItemStack.EMPTY;

	public SpellCardActivationToClient() {
	}

	public SpellCardActivationToClient(ItemStack stack) {
		this.stack = stack == null ? ItemStack.EMPTY : stack;
	}

	@Override
	public void handle(NetworkEvent.Context context) {
		context.enqueueWork(() -> {
			if (stack != null && !stack.isEmpty()) {
				triggerActivationEffect(stack);
			}
		});
		context.setPacketHandled(true);
	}

	@OnlyIn(Dist.CLIENT)
	private static void triggerActivationEffect(ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gameRenderer != null && YHModConfig.CLIENT.spellCardTotemAnimation.get()) {
			mc.gameRenderer.displayItemActivation(stack);
		}
	}
}
