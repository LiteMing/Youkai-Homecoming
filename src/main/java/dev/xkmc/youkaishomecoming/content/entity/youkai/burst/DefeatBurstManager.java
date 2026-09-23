package dev.xkmc.youkaishomecoming.content.entity.youkai.burst;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xkmc.youkaishomecoming.content.entity.boss.BossYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only bookkeeping and rendering for defeat bursts. Bursts are spawned
 * from the existing combat progress sync (no extra packets or server work) and
 * rendered as camera-facing billboards on the same world stage as spell
 * circles. With no active burst both handlers return immediately.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DefeatBurstManager {

	private static final int LIFE = 40;
	private static final int MAX_ACTIVE = 16;

	private static final List<DefeatBurst> ACTIVE = new ArrayList<>();
	private static int ticks;

	/** Called when a tracked youkai's combat progress crosses zero (满身疮痍). */
	public static void add(YoukaiEntity e) {
		if (!YHModConfig.CLIENT.defeatBurstEnabled.get()) return;
		float scale = Mth.clamp(e.getBbHeight() * 0.9f, 1.2f, 5f);
		if (e instanceof BossYoukaiEntity) scale *= 1.6f;
		scale *= (float) (double) YHModConfig.CLIENT.defeatBurstScale.get();
		float seed = e.getRandom().nextFloat() * Mth.TWO_PI;
		ACTIVE.add(new DefeatBurst(e.level(), e.getX(), e.getY() + e.getBbHeight() * 0.5f, e.getZ(),
				scale, seed, ticks));
		while (ACTIVE.size() > MAX_ACTIVE) {
			ACTIVE.remove(0);
		}
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		var level = Minecraft.getInstance().level;
		if (level == null) {
			if (!ACTIVE.isEmpty()) ACTIVE.clear();
			return;
		}
		if (Minecraft.getInstance().isPaused()) return;
		ticks++;
		ACTIVE.removeIf(b -> b.level != level || ticks - b.start > LIFE);
	}

	@SubscribeEvent
	public static void render(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
		if (ACTIVE.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		Vec3 cam = event.getCamera().getPosition();
		float pTick = event.getPartialTick();
		PoseStack pose = event.getPoseStack();
		var buffer = mc.renderBuffers().bufferSource();
		Quaternionf facing = mc.getEntityRenderDispatcher().cameraOrientation();
		boolean rendered = false;
		// two passes: alpha-blended petals first, additive flash on top
		for (int pass = 0; pass < 2; pass++) {
			VertexConsumer vc = null;
			for (DefeatBurst b : ACTIVE) {
				if (b.level != mc.level) continue;
				float t = (ticks - b.start + pTick) / LIFE;
				if (t <= 0 || t >= 1) continue;
				float reach = b.scale * 3f;
				if (!event.getFrustum().isVisible(new AABB(
						b.x - reach, b.y - reach, b.z - reach,
						b.x + reach, b.y + reach, b.z + reach))) {
					continue;
				}
				if (vc == null) {
					vc = buffer.getBuffer(pass == 0 ? BurstRenderStates.TRANSLUCENT : BurstRenderStates.ADDITIVE);
				}
				pose.pushPose();
				pose.translate(b.x - cam.x, b.y - cam.y, b.z - cam.z);
				pose.mulPose(facing);
				if (pass == 0) {
					DefeatBurstRenderer.renderPetals(pose, vc, b, t);
				} else {
					DefeatBurstRenderer.renderFlash(pose, vc, b, t);
				}
				pose.popPose();
				rendered = true;
			}
		}
		if (rendered) {
			buffer.endLastBatch();
		}
	}

}
