package dev.xkmc.youkaishomecoming.events;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.xkmc.l2damagetracker.contents.curios.AttrTooltip;
import dev.xkmc.youkaishomecoming.content.entity.youkai.CombatProgress;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.effect.BeatenEffect;
import dev.xkmc.youkaishomecoming.content.item.curio.hat.TouhouHatItem;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.registrate.YHEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEventHandlers {

	private static float oTilt, tilt;
	private static boolean beatenPoseForced;

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		oTilt = tilt;
		float lv = drunkLevel();
		tilt = Mth.lerp(0.03f, tilt, lv);
		if (event.phase == TickEvent.Phase.END) {
			syncBeatenPose();
			dev.xkmc.youkaishomecoming.compat.exposure.DanmakuPhotoOverlay.tick();
		}
	}

	private static void syncBeatenPose() {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			beatenPoseForced = false;
			return;
		}
		if (BeatenEffect.shouldForcePlayerCrawl(player)) {
			player.setForcedPose(Pose.SWIMMING);
			beatenPoseForced = true;
		} else if (beatenPoseForced) {
			player.setForcedPose(null);
			beatenPoseForced = false;
		}
	}

	@SubscribeEvent
	public static void onInput(MovementInputUpdateEvent event) {
		if (oTilt > 0.01) {
			float t = oTilt;
			float time = event.getEntity().tickCount;
			float movement = t * t * Mth.sin((float) (time / 60 * Math.PI * 2));
			float opt = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
			float scale = Math.abs(event.getInput().forwardImpulse) + 0.3f;
			float val = event.getInput().leftImpulse + (1.5f - opt) * movement * scale * 2;
			event.getInput().leftImpulse = Mth.clamp(val, -1, 1);
		}
	}

	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event) {
		if (event.getItemStack().getItem() instanceof TouhouHatItem hat) {
			AttrTooltip.modifyTooltip(event.getToolTip(), hat.getAttributeModifiersForDisplay(), false);
		}
	}

	private static float drunkLevel() {
		var cam = Minecraft.getInstance().getCameraEntity();
		if (!(cam instanceof Player player)) return 0;
		var ins = player.getEffect(YHEffects.DRUNK.get());
		if (ins == null) return 0;
		return Mth.clamp(ins.getAmplifier() * 0.25f, 0, 1);
	}

	public static void drunkView(PoseStack pose, float pTick) {
		var cam = Minecraft.getInstance().getCameraEntity();
		if (!(cam instanceof Player player)) return;
		float t = Mth.lerp(pTick, oTilt, tilt);
		if (t < 0.01) return;
		float time = pTick + player.tickCount;
		float angle = t * t * 45 * Mth.sin((float) (time / 60 * Math.PI * 2));
		float opt = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
		pose.mulPose(Axis.ZP.rotationDegrees(angle * opt));
	}

	public static void setProgress(int id, CombatProgress progress) {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		if (level.getEntity(id) instanceof YoukaiEntity e) {
			e.combatProgress.loadFrom(progress);
		}
	}

	public static void setSpellState(int entityId,
									 @org.jetbrains.annotations.Nullable ResourceLocation spellId,
									 @org.jetbrains.annotations.Nullable ResourceLocation phaseId,
									 int phaseTick,
									 boolean inDanmakuCombat,
									 int spellMaxHealth, int spellElapsedTicks, int spellDurationTicks) {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		if (level.getEntity(entityId) instanceof YoukaiEntity e) {
			e.clientSpellId = spellId;
			e.clientPhaseId = phaseId;
			e.clientPhaseTick = phaseTick;
			e.clientInDanmakuCombat = inDanmakuCombat;
			e.clientSpellMaxHealth = Math.max(0, spellMaxHealth);
			e.clientSpellElapsedTicks = Math.max(0, spellElapsedTicks);
			e.clientSpellDurationTicks = Math.max(0, spellDurationTicks);
			e.clientSpellStateReceivedTick = e.tickCount;
		}
	}
}
