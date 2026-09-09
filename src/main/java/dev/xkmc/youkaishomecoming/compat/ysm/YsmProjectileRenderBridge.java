package dev.xkmc.youkaishomecoming.compat.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.YHBaseLaserEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Client-only adapter for OpenYSM's projectile renderer. YH danmaku are virtual
 * projectiles, so OpenYSM cannot receive them directly. The adapter keeps a
 * render-only projectile proxy per visible danmaku and never adds that proxy to
 * the level. All physical state remains in {@link ItemDanmakuEntity}.
 */
public final class YsmProjectileRenderBridge {
	private static final Map<UUID, Proxy> PROXIES = new HashMap<>();
	private static final Set<String> WARNED = new HashSet<>();
	private static Method rendererMethod;
	private static Method capabilityGet;
	private static Method capabilityUpdateModel;
	private static Method capabilityTickModel;
	private static boolean probed;
	private static long frame;
	private static Level level;
	private static final Map<String, Integer> LASER_ORDINALS = new HashMap<>();

	private YsmProjectileRenderBridge() { }

	public static void beginFrame(Level current) {
		if (level != current) {
			PROXIES.clear();
			level = current;
		}
		frame++;
		LASER_ORDINALS.clear();
	}

	public static void endFrame() {
		PROXIES.entrySet().removeIf(entry -> entry.getValue().lastFrame < frame - 2);
	}

	/** Drop render-only proxy entities when the client changes level or logs out. */
	public static void clear() {
		PROXIES.clear();
		level = null;
		LASER_ORDINALS.clear();
	}

	/**
	 * Returns a per-render-pass ordinal for laser entities so the shared action
	 * max_instances cap works without a second entity scan.
	 */
	public static int nextLaserOrdinal(YHBaseLaserEntity laser) {
		String key = laser.ysmProjectileModel() + "|" + laser.ysmProjectileSlot()
				+ "|" + laser.ysmProjectileMaxInstances();
		int ordinal = LASER_ORDINALS.getOrDefault(key, 0);
		LASER_ORDINALS.put(key, ordinal + 1);
		return ordinal;
	}

	/** Returns true only when YSM consumed the draw call. */
	public static boolean render(ItemDanmakuEntity danmaku, PoseStack pose, MultiBufferSource buffer,
			int light, float partialTick, float x, float y, float z, int visibleOrdinal) {
		return render(danmaku, pose, buffer, light, partialTick, x, y, z, visibleOrdinal, 0xffffffff);
	}

	/** Render a model at a laser's beam origin. The regular beam remains visible. */
	public static boolean render(YHBaseLaserEntity laser, PoseStack pose, MultiBufferSource buffer,
			int light, float partialTick, int visibleOrdinal) {
		return render(laser, pose, buffer, light, partialTick, 0, 0, 0, visibleOrdinal,
				laser.ysmProjectileTint());
	}

	public static boolean render(YHBaseLaserEntity laser, PoseStack pose, MultiBufferSource buffer,
			int light, float partialTick, float x, float y, float z, int visibleOrdinal, int tint) {
		return render(target(laser), pose, buffer, light, partialTick, x, y, z, visibleOrdinal, tint);
	}

	/** Render with a YH ARGB tint applied to every YSM model vertex. */
	public static boolean render(ItemDanmakuEntity danmaku, PoseStack pose, MultiBufferSource buffer,
			int light, float partialTick, float x, float y, float z, int visibleOrdinal, int tint) {
		return render(target(danmaku), pose, buffer, light, partialTick, x, y, z, visibleOrdinal, tint);
	}

	private static boolean render(Target danmaku, PoseStack pose, MultiBufferSource buffer,
			int light, float partialTick, float x, float y, float z, int visibleOrdinal, int tint) {
		if (!danmaku.hasYsmProjectile()) return false;
		if (!YSMClientCompat.isLoaded()) {
			warnOnce("not_installed", "YSM projectile presentation requested but OpenYSM is not installed; using YH fallback");
			return false;
		}
		String slot = danmaku.slot();
		if (visibleOrdinal >= danmaku.maxInstances()) {
			warnOnce("limit:" + danmaku.model() + ":" + slot,
					"YSM projectile instance limit reached for model '" + danmaku.model()
							+ "'; using YH fallback for additional projectiles");
			return false;
		}
		if (!probe()) return false;
		try {
			Proxy proxy = PROXIES.get(danmaku.id());
			if (proxy == null || !proxy.slot.equals(slot)) {
				proxy = createProxy(slot);
				if (proxy != null) PROXIES.put(danmaku.id(), proxy);
			}
			if (proxy == null || proxy.entity == null) {
				warnOnce("slot:" + slot, "YSM projectile slot '" + slot
						+ "' is not a registered Projectile entity; using YH fallback");
				return false;
			}
			proxy.lastFrame = frame;
			configure(proxy, danmaku);
			pose.pushPose();
			try {
				float scale = danmaku.baseScale() * danmaku.modelScale();
				Vec3 offset = localOffset(danmaku, partialTick, scale);
				pose.translate(x + offset.x, y + offset.y, z + offset.z);
				pose.scale(scale, scale, scale);
				if (danmaku.tiltOffset() != 0) {
					Vec3 axis = danmaku.forward(partialTick).normalize();
					if (axis.lengthSqr() > 1.0e-8) {
						pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(danmaku.tiltOffset()),
								(float) axis.x, (float) axis.y, (float) axis.z));
					}
				}
				Object result = rendererMethod.invoke(null, proxy.entity, 0f, partialTick, pose,
						tint == 0xffffffff ? buffer : new TintingBufferSource(buffer, tint), light);
				// OpenYSM returns true when its dispatcher wants vanilla fallback.
				boolean consumed = result instanceof Boolean bool && !bool;
				if (!consumed) {
					warnOnce("model:" + danmaku.model() + ":" + slot,
							"OpenYSM model '" + danmaku.model() + "' is not ready or does not provide projectile slot '"
									+ slot + "'; using YH fallback until available");
				}
				return consumed;
			} finally {
				pose.popPose();
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
			warnOnce("render", "OpenYSM projectile rendering failed; using YH fallback: " + ex.getClass().getSimpleName());
			return false;
		}
	}

	private static Proxy createProxy(String slot) {
		Level current = Minecraft.getInstance().level;
		if (current == null) return null;
		ResourceLocation typeId = projectileTypeId(slot);
		EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(typeId);
		if (type == null) return null;
		Entity value = type.create(current);
		return value instanceof Projectile projectile ? new Proxy(projectile, slot) : null;
	}

	private static void configure(Proxy proxy, Target danmaku) throws ReflectiveOperationException {
		Projectile entity = proxy.entity;
		Entity source = danmaku.source();
		entity.setPos(source.getX(), source.getY(), source.getZ());
		// Keep movement in sync as well as rotation. OpenYSM's projectile animation
		// predicates read delta movement even though the dispatcher only uses yaw/pitch
		// for the actual orientation.
		entity.setDeltaMovement(source.getDeltaMovement());
		entity.xOld = source.xOld;
		entity.yOld = source.yOld;
		entity.zOld = source.zOld;
		// OpenYSM's projectile renderer applies the vanilla `yaw - 90` correction to
		// models authored along +X. YH's local renderers use the opposite yaw and
		// pitch signs, so copying either angle directly mirrors that axis (for
		// example, +X or +Y is rendered toward the negative direction). Mirror
		// both angles only in the render-only proxy; the YH entity and its physical
		// trajectory remain untouched.
		entity.setYRot(-source.getYRot() + danmaku.yawOffset());
		entity.setXRot(-source.getXRot() + danmaku.pitchOffset());
		entity.yRotO = -source.yRotO + danmaku.yawOffset();
		entity.xRotO = -source.xRotO + danmaku.pitchOffset();
		entity.tickCount = source.tickCount;
		Object optional = capabilityGet.invoke(null, entity);
		if (optional instanceof Optional<?> cap && cap.isPresent()) {
			if (!danmaku.model().equals(proxy.model)) {
				capabilityUpdateModel.invoke(cap.get(), danmaku.model());
				proxy.model = danmaku.model();
			}
			if (capabilityTickModel != null && proxy.lastTick != source.tickCount) {
				capabilityTickModel.invoke(cap.get());
				proxy.lastTick = source.tickCount;
			}
		} else {
			warnOnce("capability", "OpenYSM did not attach a projectile capability; using YH fallback");
		}
	}

	private static ResourceLocation projectileTypeId(String slot) {
		if (slot == null || slot.isBlank()) return new ResourceLocation("minecraft", "arrow");
		ResourceLocation parsed = ResourceLocation.tryParse(slot);
		return parsed != null ? parsed : new ResourceLocation("minecraft", slot);
	}

	/**
	 * Convert the action's renderer-local (forward, right, up) offset into world
	 * coordinates. YH and vanilla projectiles use the same yaw convention
	 * ({@code -atan2(x, z)}); OpenYSM's internal {@code yaw - 90} is the model-axis
	 * correction for its projectile models and must not be compensated by negating Z here.
	 */
	private static Vec3 localOffset(Target danmaku, float partialTick, float scale) {
		float forwardAmount = danmaku.offsetForward();
		float rightAmount = danmaku.offsetRight();
		float upAmount = danmaku.offsetUp();
		if (forwardAmount == 0 && rightAmount == 0 && upAmount == 0) return Vec3.ZERO;
		Vec3 forward = danmaku.forward(partialTick);
		if (forward.lengthSqr() < 1.0e-8) forward = new Vec3(0, 0, 1);
		else forward = forward.normalize();
		// Keep a stable roll for vertical shots by switching the reference axis when
		// the usual world-up cross product becomes degenerate.
		Vec3 referenceUp = Math.abs(forward.y) > 0.999 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
		Vec3 right = referenceUp.cross(forward).normalize();
		Vec3 up = forward.cross(right).normalize();
		return forward.scale(forwardAmount * scale)
				.add(right.scale(rightAmount * scale))
				.add(up.scale(upAmount * scale));
	}

	private static Target target(ItemDanmakuEntity danmaku) {
		return new Target() {
			@Override public Entity source() { return danmaku; }
			@Override public boolean hasYsmProjectile() { return danmaku.hasYsmProjectile(); }
			@Override public String model() { return danmaku.ysmProjectileModel(); }
			@Override public String slot() { return danmaku.ysmProjectileSlot(); }
			@Override public float modelScale() { return danmaku.ysmProjectileModelScale(); }
			@Override public int maxInstances() { return danmaku.ysmProjectileMaxInstances(); }
			@Override public float baseScale() { return danmaku.scale(); }
			@Override public float offsetForward() { return danmaku.ysmProjectileOffsetForward(); }
			@Override public float offsetRight() { return danmaku.ysmProjectileOffsetRight(); }
			@Override public float offsetUp() { return danmaku.ysmProjectileOffsetUp(); }
			@Override public float pitchOffset() { return danmaku.ysmProjectilePitchOffset(); }
			@Override public float yawOffset() { return danmaku.ysmProjectileYawOffset(); }
			@Override public float tiltOffset() { return danmaku.ysmProjectileTiltOffset(); }
			@Override public Vec3 forward(float partialTick) { return danmaku.getViewVector(partialTick); }
			@Override public UUID id() { return danmaku.getUUID(); }
		};
	}

	private static Target target(YHBaseLaserEntity laser) {
		return new Target() {
			@Override public Entity source() { return laser; }
			@Override public boolean hasYsmProjectile() { return laser.hasYsmProjectile(); }
			@Override public String model() { return laser.ysmProjectileModel(); }
			@Override public String slot() { return laser.ysmProjectileSlot(); }
			@Override public float modelScale() { return laser.ysmProjectileModelScale(); }
			@Override public int maxInstances() { return laser.ysmProjectileMaxInstances(); }
			@Override public float baseScale() { return laser.ysmProjectileBaseScale(); }
			@Override public float offsetForward() { return laser.ysmProjectileOffsetForward(); }
			@Override public float offsetRight() { return laser.ysmProjectileOffsetRight(); }
			@Override public float offsetUp() { return laser.ysmProjectileOffsetUp(); }
			@Override public float pitchOffset() { return laser.ysmProjectilePitchOffset(); }
			@Override public float yawOffset() { return laser.ysmProjectileYawOffset(); }
			@Override public float tiltOffset() { return laser.ysmProjectileTiltOffset(); }
			@Override public Vec3 forward(float partialTick) {
				float pitch = Mth.lerp(partialTick, laser.xRotO, laser.getXRot());
				float yaw = Mth.lerp(partialTick, laser.yRotO, laser.getYRot());
				return Vec3.directionFromRotation(pitch, yaw);
			}
			@Override public UUID id() { return laser.getUUID(); }
		};
	}

	private interface Target {
		Entity source();
		boolean hasYsmProjectile();
		String model();
		String slot();
		float modelScale();
		int maxInstances();
		float baseScale();
		float offsetForward();
		float offsetRight();
		float offsetUp();
		float pitchOffset();
		float yawOffset();
		float tiltOffset();
		Vec3 forward(float partialTick);
		UUID id();
	}

	private static boolean probe() {
		if (probed) return rendererMethod != null && capabilityGet != null && capabilityUpdateModel != null;
		probed = true;
		try {
			Class<?> renderer = Class.forName("com.elfmcys.yesstevemodel.client.renderer.CustomProjectileRenderer");
			rendererMethod = renderer.getMethod("renderProjectile", Projectile.class, float.class, float.class,
				PoseStack.class, MultiBufferSource.class, int.class);
			Class<?> capability = Class.forName("com.elfmcys.yesstevemodel.capability.ProjectileCapability");
			capabilityGet = capability.getMethod("get", Projectile.class);
			capabilityUpdateModel = capability.getMethod("updateModelId", String.class);
			try {
				capabilityTickModel = capability.getMethod("tickModel");
			} catch (NoSuchMethodException ignored) {
				// Older OpenYSM builds refresh the projectile model from updateModelId.
				capabilityTickModel = null;
			}
			return true;
		} catch (ReflectiveOperationException | LinkageError ex) {
			warnOnce("api", "OpenYSM projectile API is unavailable; using YH fallback");
			return false;
		}
	}

	private static void warnOnce(String key, String message) {
		if (WARNED.add(key)) {
			dev.xkmc.youkaishomecoming.init.YoukaisHomecoming.LOGGER.warn(message);
		}
	}

	private static final class Proxy {
		private final Projectile entity;
		private final String slot;
		private long lastFrame;
		private int lastTick = Integer.MIN_VALUE;
		private String model = "";
		private Proxy(Projectile entity, String slot) {
			this.entity = entity;
			this.slot = slot;
		}
	}

	private record TintingBufferSource(MultiBufferSource delegate, int tint) implements MultiBufferSource {
		@Override
		public VertexConsumer getBuffer(RenderType renderType) {
			return new TintingVertexConsumer(delegate.getBuffer(renderType), tint);
		}
	}

	/** Minimal Forge 1.20 VertexConsumer decorator; default interface methods remain delegated. */
	private static final class TintingVertexConsumer implements VertexConsumer {
		private final VertexConsumer delegate;
		private final int tint;

		private TintingVertexConsumer(VertexConsumer delegate, int tint) {
			this.delegate = delegate;
			this.tint = tint;
		}

		@Override
		public VertexConsumer vertex(double x, double y, double z) {
			delegate.vertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			delegate.color(multiply(red, tint >> 16), multiply(green, tint >> 8),
					multiply(blue, tint), multiply(alpha, tint >> 24));
			return this;
		}

		@Override
		public VertexConsumer uv(float u, float v) {
			delegate.uv(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlayCoords(int u, int v) {
			delegate.overlayCoords(u, v);
			return this;
		}

		@Override
		public VertexConsumer uv2(int u, int v) {
			delegate.uv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			delegate.normal(x, y, z);
			return this;
		}

		@Override
		public void endVertex() {
			delegate.endVertex();
		}

		@Override
		public void defaultColor(int red, int green, int blue, int alpha) {
			delegate.defaultColor(multiply(red, tint >> 16), multiply(green, tint >> 8),
					multiply(blue, tint), multiply(alpha, tint >> 24));
		}

		@Override
		public void unsetDefaultColor() {
			delegate.unsetDefaultColor();
		}

		private static int multiply(int value, int tintChannel) {
			return Math.max(0, Math.min(255, value)) * (tintChannel & 0xff) / 255;
		}
	}
}
