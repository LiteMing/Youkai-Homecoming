package dev.xkmc.youkaishomecoming.content.client.beaten;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.xkmc.youkaishomecoming.content.entity.boss.BossYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuPoofParticleOptions;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.client.CameraShakeManager;
import dev.xkmc.youkaishomecoming.events.YoukaiBeatenPhaseEvent;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase-driven client visuals for danmaku defeat. The cadence follows Taisei's
 * open-source boss/player defeat sequences without copying its code or assets:
 * https://github.com/taisei-project/taisei/blob/717b7bf46ae5772ca28f2c34c699cf628874eefb/src/boss.c
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BeatenVisualManager {

	private static final int MAX_ACTIVE = 24;
	private static final List<BeatenVisual> ACTIVE = new ArrayList<>();
	private static final Vector3f PALE = new Vector3f(0.72f, 0.92f, 1f);
	private static final Vector3f BLUE = new Vector3f(0.28f, 0.48f, 1f);
	private static final Vector3f ROSE = new Vector3f(0.92f, 0.34f, 0.72f);

	private static ClientLevel activeLevel;
	private static int ticks;

	@SubscribeEvent
	public static void onPhaseChanged(YoukaiBeatenPhaseEvent event) {
		YoukaiEntity youkai = event.getYoukai();
		Minecraft mc = Minecraft.getInstance();
		if (!youkai.level().isClientSide() || mc.level != youkai.level()) return;
		if (event.getBeatenPhase() == YoukaiEntity.BEATEN_DEFEAT) {
			startDefeat(youkai, mc.level);
		} else if (event.getBeatenPhase() == YoukaiEntity.BEATEN_PRONE) {
			startLanding(youkai, mc.level);
		}
	}

	private static void startDefeat(YoukaiEntity youkai, ClientLevel level) {
		ensureLevel(level);
		float scale = Mth.clamp(youkai.getBbHeight() * 0.72f, 0.85f, 3.2f);
		if (youkai instanceof BossYoukaiEntity) scale *= 1.25f;
		Vec3 origin = youkai.position().add(0, youkai.getBbHeight() * 0.54f, 0);
		ACTIVE.removeIf(v -> v.entityId == youkai.getId() && v.kind == BeatenVisual.Kind.DEFEAT);
		add(new BeatenVisual(level, youkai.getId(), origin, scale,
				level.random.nextFloat() * Mth.TWO_PI, ticks, BeatenVisual.Kind.DEFEAT));
	}

	/** Player life-shard loss in danmaku combat: same defeat burst at the player. */
	public static void startPlayerHit(Player player) {
		if (!(player.level() instanceof ClientLevel level)) return;
		ensureLevel(level);
		float scale = Mth.clamp(player.getBbHeight() * 0.72f, 0.85f, 3.2f);
		Vec3 origin = player.position().add(0, player.getBbHeight() * 0.54f, 0);
		ACTIVE.removeIf(v -> v.entityId == player.getId() && v.kind == BeatenVisual.Kind.DEFEAT);
		add(new BeatenVisual(level, player.getId(), origin, scale,
				level.random.nextFloat() * Mth.TWO_PI, ticks, BeatenVisual.Kind.DEFEAT));
	}

	private static void startLanding(YoukaiEntity youkai, ClientLevel level) {
		ensureLevel(level);
		float scale = Mth.clamp(youkai.getBbWidth() * 1.15f, 0.65f, 2.4f);
		Vec3 origin = youkai.position().add(0, 0.05, 0);
		ACTIVE.removeIf(v -> v.entityId == youkai.getId() && v.kind == BeatenVisual.Kind.LANDING);
		BeatenVisual visual = new BeatenVisual(level, youkai.getId(), origin, scale,
				level.random.nextFloat() * Mth.TWO_PI, ticks, BeatenVisual.Kind.LANDING);
		add(visual);
		spawnLandingDust(visual);
	}

	private static void add(BeatenVisual visual) {
		ACTIVE.add(visual);
		while (ACTIVE.size() > MAX_ACTIVE) {
			ACTIVE.remove(0);
		}
	}

	private static void ensureLevel(ClientLevel level) {
		if (activeLevel != level) {
			clear(level);
		}
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			clear(null);
			return;
		}
		if (activeLevel != mc.level) {
			clear(mc.level);
		}
		if (mc.isPaused()) return;
		ticks++;
		for (BeatenVisual visual : ACTIVE) {
			int age = ticks - visual.startTick;
			if (visual.kind == BeatenVisual.Kind.DEFEAT) {
				if (age > 0 && age <= 11) {
					spawnChargeMotes(visual);
				}
				if (age == 4) {
					spawnRadialBurst(visual);
					visual.level.playLocalSound(visual.origin.x, visual.origin.y, visual.origin.z,
							SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 0.9f, 0.72f, false);
				}
			}
		}
		ACTIVE.removeIf(v -> v.level != mc.level || ticks - v.startTick > v.kind.lifetime);
	}

	private static void clear(ClientLevel level) {
		ACTIVE.clear();
		activeLevel = level;
		ticks = 0;
	}

	private static void spawnChargeMotes(BeatenVisual visual) {
		RandomSource random = visual.level.random;
		for (int i = 0; i < 2; i++) {
			float angle = visual.seed + random.nextFloat() * Mth.TWO_PI;
			double radius = visual.scale * (0.15 + random.nextDouble() * 0.28);
			double x = Mth.cos(angle) * radius;
			double y = Mth.sin(angle) * radius;
			Vec3 velocity = new Vec3(-y, x, (random.nextDouble() - 0.5) * 0.08).scale(0.06);
			visual.level.addParticle(new DanmakuPoofParticleOptions(i == 0 ? BLUE : ROSE, 0.65f),
					visual.origin.x + x, visual.origin.y + y, visual.origin.z,
					velocity.x, velocity.y, velocity.z);
		}
	}

	private static void spawnRadialBurst(BeatenVisual visual) {
		RandomSource random = visual.level.random;
		int count = visual.scale > 2.2f ? 36 : 28;
		float particleScale = Mth.clamp(visual.scale * 0.55f, 0.55f, 1.5f);
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double vertical = random.nextDouble() * 1.3 - 0.65;
			Vec3 direction = new Vec3(Math.cos(angle), vertical, Math.sin(angle)).normalize();
			double speed = (0.1 + random.nextDouble() * 0.16) * visual.scale;
			Vector3f color = switch (i % 3) {
				case 0 -> PALE;
				case 1 -> BLUE;
				default -> ROSE;
			};
			visual.level.addParticle(new DanmakuPoofParticleOptions(color, particleScale),
					visual.origin.x, visual.origin.y, visual.origin.z,
					direction.x * speed, direction.y * speed, direction.z * speed);
		}
	}

	private static void spawnLandingDust(BeatenVisual visual) {
		RandomSource random = visual.level.random;
		for (int i = 0; i < 12; i++) {
			double angle = visual.seed + i * Mth.TWO_PI / 12f;
			double speed = (0.045 + random.nextDouble() * 0.055) * visual.scale;
			visual.level.addParticle(new DanmakuPoofParticleOptions(PALE, 0.52f),
					visual.origin.x, visual.origin.y, visual.origin.z,
					Math.cos(angle) * speed, 0.025 + random.nextDouble() * 0.035, Math.sin(angle) * speed);
		}
	}

	@SubscribeEvent
	public static void render(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		Vec3 camera = event.getCamera().getPosition();
		PoseStack pose = event.getPoseStack();
		var buffers = mc.renderBuffers().bufferSource();

		VertexConsumer translucent = buffers.getBuffer(BeatenRenderStates.TRANSLUCENT);
		for (BeatenVisual visual : ACTIVE) {
			if (!visible(event, visual)) continue;
			float progress = Mth.clamp(visual.age(ticks, event.getPartialTick()) / visual.kind.lifetime, 0, 1);
			pose.pushPose();
			pose.translate(visual.origin.x - camera.x, visual.origin.y - camera.y, visual.origin.z - camera.z);
			pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
			BeatenVisualRenderer.renderTranslucent(pose, translucent, visual, progress);
			pose.popPose();
		}
		buffers.endBatch(BeatenRenderStates.TRANSLUCENT);

		VertexConsumer additive = buffers.getBuffer(BeatenRenderStates.ADDITIVE);
		for (BeatenVisual visual : ACTIVE) {
			if (!visible(event, visual)) continue;
			float progress = Mth.clamp(visual.age(ticks, event.getPartialTick()) / visual.kind.lifetime, 0, 1);
			pose.pushPose();
			pose.translate(visual.origin.x - camera.x, visual.origin.y - camera.y, visual.origin.z - camera.z);
			if (visual.kind == BeatenVisual.Kind.DEFEAT) {
				pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
			} else {
				pose.mulPose(Axis.XP.rotationDegrees(90));
			}
			BeatenVisualRenderer.renderAdditive(pose, additive, visual, progress);
			pose.popPose();
		}
		buffers.endBatch(BeatenRenderStates.ADDITIVE);
	}

	private static boolean visible(RenderLevelStageEvent event, BeatenVisual visual) {
		float reach = visual.scale * (visual.kind == BeatenVisual.Kind.DEFEAT ? 3.2f : 1.8f);
		Vec3 p = visual.origin;
		return visual.level == Minecraft.getInstance().level && event.getFrustum().isVisible(new AABB(
				p.x - reach, p.y - reach, p.z - reach,
				p.x + reach, p.y + reach, p.z + reach));
	}

	@SubscribeEvent
	public static void cameraShake(ViewportEvent.ComputeCameraAngles event) {
		if (ACTIVE.isEmpty() || !CameraShakeManager.isEnabled()) return;
		Vec3 camera = event.getCamera().getPosition();
		float yaw = 0, pitch = 0, roll = 0;
		for (BeatenVisual visual : ACTIVE) {
			if (visual.kind != BeatenVisual.Kind.DEFEAT) continue;
			float age = visual.age(ticks, (float) event.getPartialTick());
			float p = Mth.clamp((age - 3f) / 15f, 0f, 1f);
			if (p <= 0 || p >= 1) continue;
			float distance = (float) camera.distanceTo(visual.origin);
			float attenuation = Mth.clamp(1f - distance / 36f, 0f, 1f);
			float envelope = Mth.sin(Mth.PI * p) * attenuation * Mth.clamp(visual.scale * 0.6f, 0.65f, 1.35f);
			float phase = visual.seed + age * 2.65f;
			yaw += Mth.sin(phase) * 0.34f * envelope;
			pitch += Mth.cos(phase * 1.17f) * 0.24f * envelope;
			roll += Mth.sin(phase * 0.73f) * 0.42f * envelope;
		}
		CameraShakeManager.applyAngles(event, Mth.clamp(yaw, -1.25f, 1.25f),
				Mth.clamp(pitch, -0.9f, 0.9f), Mth.clamp(roll, -1.5f, 1.5f));
	}

	private BeatenVisualManager() {
	}
}
