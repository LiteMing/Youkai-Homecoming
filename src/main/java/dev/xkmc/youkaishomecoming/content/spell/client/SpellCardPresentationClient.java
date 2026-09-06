package dev.xkmc.youkaishomecoming.content.spell.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.xkmc.youkaishomecoming.client.render.SpellCardItemRenderer;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.certification.SpellColorExtractor;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellRegistry;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.init.registrate.YHDanmaku;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Client-only world renderer for the server-authored spell-card presentation cue. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = YoukaisHomecoming.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpellCardPresentationClient {
	private static final Map<Integer, Entry> ACTIVE = new HashMap<>();
	private static final Map<Integer, Long> LAST_SEQUENCE = new HashMap<>();
	private static final float HELD_SCALE = 0.55f;
	private static final float DISPLAY_SCALE = 0.92f;

	private SpellCardPresentationClient() {
	}

	public static void accept(int entityId, String spellIdText, long startedAt,
			int holdTicks, int throwTicks, int floatTicks, boolean rightHand,
			double offsetRight, double offsetUp, double offsetForward, long sequence) {
		Long last = LAST_SEQUENCE.get(entityId);
		if (last != null && sequence <= last) return;
		LAST_SEQUENCE.put(entityId, sequence);
		int duration = holdTicks + throwTicks + floatTicks;
		if (spellIdText == null || spellIdText.isBlank() || duration <= 0) {
			ACTIVE.remove(entityId);
			return;
		}
		ResourceLocation spellId = ResourceLocation.tryParse(spellIdText);
		if (spellId == null) return;
		if (ACTIVE.size() >= 256) ACTIVE.clear();
		ACTIVE.put(entityId, new Entry(createStack(spellId), startedAt,
				Math.max(0, holdTicks), Math.max(0, throwTicks), Math.max(0, floatTicks),
				rightHand, offsetRight, offsetUp, offsetForward, sequence));
	}

	private static ItemStack createStack(ResourceLocation spellId) {
		ItemStack stack = DynamicSpellItem.createStack(YHDanmaku.DYNAMIC_SPELL.get(), spellId);
		var definition = SpellRegistry.get(spellId);
		if (definition == null) return stack;
		DynamicSpellItem.setCardType(stack, definition.itemForm.cardType());
		DynamicSpellItem.setExSpell(stack, definition.itemForm.exSpell());
		var color = SpellColorExtractor.extract(definition);
		return color == null ? stack : DynamicSpellItem.withColor(stack, color);
	}

	@SubscribeEvent
	public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
		ACTIVE.clear();
		LAST_SEQUENCE.clear();
	}

	@SubscribeEvent
	public static void render(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		float partialTick = event.getPartialTick();
		long gameTime = mc.level.getGameTime();
		Vec3 camera = event.getCamera().getPosition();
		PoseStack pose = event.getPoseStack();
		var buffers = mc.renderBuffers().bufferSource();
		boolean rendered = false;

		Iterator<Map.Entry<Integer, Entry>> iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			var mapEntry = iterator.next();
			Entry entry = mapEntry.getValue();
			float age = gameTime - entry.startedAt() + partialTick;
			if (age >= entry.duration()) {
				iterator.remove();
				continue;
			}
			if (age < 0) continue;
			Entity entity = mc.level.getEntity(mapEntry.getKey());
			if (!(entity instanceof LivingEntity living) || entity.isRemoved()) continue;
			if (isFirstPersonCameraEntity(mc, entity)) continue;
			renderCard(mc, pose, buffers, camera, living, entry, age, partialTick);
			rendered = true;
		}
		if (rendered) buffers.endBatch();
	}

	private static void renderCard(Minecraft mc, PoseStack pose,
			net.minecraft.client.renderer.MultiBufferSource buffers, Vec3 camera,
			LivingEntity entity, Entry entry, float age, float partialTick) {
		float heldEnd = entry.holdTicks();
		float throwEnd = heldEnd + entry.throwTicks();
		float side;
		float forward;
		float height;
		float rotation;
		float scale;
		float handSide = entry.rightHand() ? 1.0f : -1.0f;

		if (age < heldEnd) {
			float p = smooth(Mth.clamp(age / heldEnd, 0.0f, 1.0f));
			side = 0.32f * handSide;
			forward = 0.10f;
			height = 0.66f + Mth.sin(age * 0.35f) * 0.01f;
			rotation = Mth.lerp(p, -28.0f, -16.0f);
			scale = HELD_SCALE;
		} else if (age < throwEnd) {
			float p = smooth((age - heldEnd) / entry.throwTicks());
			side = Mth.lerp(p, 0.32f * handSide, 0.0f);
			forward = Mth.lerp(p, 0.10f, 0.72f);
			height = Mth.lerp(p, 0.66f, 0.82f) + Mth.sin(p * Mth.PI) * 0.08f;
			rotation = Mth.lerp(p, -16.0f, 344.0f);
			scale = Mth.lerp(p, HELD_SCALE, DISPLAY_SCALE);
		} else {
			float p = (age - throwEnd) / entry.floatTicks();
			side = 0.0f;
			forward = 0.72f + Mth.sin(p * Mth.TWO_PI) * 0.025f;
			height = 0.82f + Mth.sin(p * Mth.TWO_PI * 2.0f) * 0.035f;
			rotation = Mth.sin(p * Mth.TWO_PI) * 4.0f;
			scale = DISPLAY_SCALE;
		}

		float reveal = smooth(Mth.clamp(age / Math.min(4.0f, entry.duration()), 0.0f, 1.0f));
		float fadeTicks = Math.min(10.0f, entry.floatTicks());
		float fadeStart = entry.duration() - fadeTicks;
		float fade = fadeTicks <= 0 ? 1.0f : 1.0f - smooth(Mth.clamp((age - fadeStart) / fadeTicks, 0.0f, 1.0f));
		float alpha = reveal * fade;
		float yaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
		float yawRadians = yaw * Mth.DEG_TO_RAD;
		Vec3 facing = new Vec3(-Mth.sin(yawRadians), 0, Mth.cos(yawRadians));
		Vec3 right = new Vec3(-Mth.cos(yawRadians), 0, -Mth.sin(yawRadians));
		Vec3 origin = new Vec3(
				Mth.lerp(partialTick, entity.xOld, entity.getX()),
				Mth.lerp(partialTick, entity.yOld, entity.getY()),
				Mth.lerp(partialTick, entity.zOld, entity.getZ()));
		Vec3 position = origin.add(facing.scale(forward + entry.offsetForward()))
				.add(right.scale(side + entry.offsetRight()))
				.add(0, entity.getBbHeight() * height + entry.offsetUp(), 0);

		pose.pushPose();
		pose.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
		pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
		pose.mulPose(Axis.YP.rotationDegrees(rotation));
		pose.scale(scale, scale, scale);
		SpellCardItemRenderer.INSTANCE.renderPresentation(entry.stack(), pose, buffers, alpha);
		pose.popPose();
	}

	private static float smooth(float value) {
		return value * value * (3.0f - 2.0f * value);
	}

	private static boolean isFirstPersonCameraEntity(Minecraft mc, Entity entity) {
		return mc.options.getCameraType().isFirstPerson() && entity == mc.getCameraEntity();
	}

	private record Entry(ItemStack stack, long startedAt,
			int holdTicks, int throwTicks, int floatTicks, boolean rightHand,
			double offsetRight, double offsetUp, double offsetForward, long sequence) {
		private int duration() {
			return holdTicks + throwTicks + floatTicks;
		}
	}
}
