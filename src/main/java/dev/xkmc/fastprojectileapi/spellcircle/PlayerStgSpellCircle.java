package dev.xkmc.fastprojectileapi.spellcircle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xkmc.youkaishomecoming.content.capability.GrazeCapability;
import dev.xkmc.youkaishomecoming.content.spell.certification.network.CertificationClientHandler;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Player STG battle spell circle (design doc §17.2-17.3, D4).
 * <ul>
 *   <li>main ring from the {@code youkaishomecoming:player_stg} circle definition;</li>
 *   <li>bomb sub-circles rendered dynamically around the ring: one full sub-circle
 *   per whole bomb (RESOURCE_UNIT = 5 raw units), a partial sub-circle for the
 *   fractional remainder, capped at spellCircleMaxResourceSubCircles;</li>
 *   <li>global alpha fades continuously below spellCirclePlayerFadeStartLife
 *   (smoothstep), pinned to 1.0 while the player's own certification trial is
 *   active (D4);</li>
 *   <li>first-person rendering is intentionally not implemented (design §17.5:
 *   "若需显示" — third-person and other players see it).</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class PlayerStgSpellCircle {

	private static final ResourceLocation PLAYER_STG = new ResourceLocation("youkaishomecoming", "player_stg");
	private static final ResourceLocation SPELL_TEX = new ResourceLocation("youkaishomecoming", "textures/entities/spell_circle.png");
	private static final int SHARD = 5;

	private PlayerStgSpellCircle() {
	}

	public static void render(PoseStack pose, MultiBufferSource buffer, int light,
							  Player player, float pTick, @Nullable Quaternionf front) {
		GrazeCapability cap = GrazeCapability.HOLDER.get(player);
		if (cap == null || (!cap.isInDanmakuCombat() && !cap.isPlayerSpellActive())) return;
		SpellComponent component = SpellComponent.getFromConfig(PLAYER_STG.toString());
		if (component == null) return;

		float alpha = computeAlpha(cap);
		if (alpha <= 0.01f) return;

		pose.pushPose();
		pose.translate(0, player.getBbHeight() * 0.5f, 0);
		pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
		if (front != null) {
			pose.mulPose(front);
			pose.mulPose(new Quaternionf().rotationY((float) Math.PI));
		}
		VertexConsumer builder = buffer.getBuffer(SpellRenderState.getSpell(SPELL_TEX));
		SpellComponent.RenderHandle handle = new SpellComponent.RenderHandle(
				pose, buffer, builder, player.tickCount + pTick, light);
		handle.alpha = alpha;
		component.render(handle);
		SpellProgressCircleRenderer.render(pose, buffer, light, player, pTick, alpha);
		renderBombSubCircles(pose, buffer, light, player, pTick, alpha);
		pose.popPose();
	}

	private static float computeAlpha(GrazeCapability cap) {
		// D4: certification alpha fade disabled entirely
		if (CertificationClientHandler.inMyTrial()) {
			return 1.0f;
		}
		double life = cap.getLife() / (double) SHARD;
		double fadeStart = YHModConfig.COMMON.spellCirclePlayerFadeStartLife.get();
		if (life >= fadeStart) return 1.0f;
		double t = Math.max(0, life) / fadeStart;
		t = Math.min(1, Math.max(0, t));
		double smooth = t * t * (3 - 2 * t);
		double minAlpha = YHModConfig.COMMON.spellCirclePlayerMinAlpha.get();
		return (float) (minAlpha + (1 - minAlpha) * smooth);
	}

	private static void renderBombSubCircles(PoseStack pose, MultiBufferSource buffer, int light,
											 Player player, float pTick, float globalAlpha) {
		GrazeCapability cap = GrazeCapability.HOLDER.get(player);
		int raw = cap.getBomb();
		int whole = raw / SHARD;
		int remainder = raw % SHARD;
		int max = YHModConfig.COMMON.spellCircleMaxResourceSubCircles.get();
		int slots = Math.min(whole + (remainder > 0 ? 1 : 0), max);
		if (slots <= 0) return;
		VertexConsumer builder = buffer.getBuffer(SpellRenderState.getSpell(SPELL_TEX));
		SpellComponent.RenderHandle handle = new SpellComponent.RenderHandle(
				pose, buffer, builder, player.tickCount + pTick, light);
		for (int i = 0; i < slots; i++) {
			float slotAlpha = i < whole ? 1.0f : remainder / (float) SHARD;
			if (slotAlpha <= 0.01f) continue;
			float angle = i * 360f / slots;
			pose.pushPose();
			pose.mulPose(new Quaternionf().rotationY((float) Math.toRadians(angle)));
			pose.translate(44, 0, 0);
			pose.mulPose(new Quaternionf().rotationY((float) Math.toRadians(-angle)));
			handle.alpha = globalAlpha * slotAlpha;
			SpellComponent.Stroke sub = new SpellComponent.Stroke();
			sub.vertex = 24; sub.cycle = 1; sub.rune = 0; sub.color = "0x99FFFFFF";
			sub.width = 1.5f; sub.radius = 5f; sub.z = 0.02f; sub.angle = 0;
			sub.render(handle);
			pose.popPose();
		}
	}
}
