package dev.xkmc.fastprojectileapi.render.virtual;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import dev.xkmc.fastprojectileapi.entity.AsyncProjectile;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.fastprojectileapi.render.core.DanmakuRenderStates;
import dev.xkmc.fastprojectileapi.render.core.GiantDanmakuScreenOverlay;
import dev.xkmc.fastprojectileapi.render.core.ProjTypeHolder;
import dev.xkmc.fastprojectileapi.render.core.ProjectileRenderer;
import dev.xkmc.fastprojectileapi.render.type.LayeredRotatingProjectileType;
import dev.xkmc.fastprojectileapi.render.type.RotatingProjectileType;
import dev.xkmc.fastprojectileapi.render.type.SimpleProjectileType;
import dev.xkmc.l2serial.util.Wrappers;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.ItemDanmakuRenderer;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuRenderer;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DanmakuItem;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

public class ClientDanmakuCache {

	private static final Logger LOGGER = LogUtils.getLogger();

	/**
	 * Minimum entity count to justify parallel tick.
	 * Below this, single-threaded is faster due to thread scheduling overhead.
	 */
	private static final int PARALLEL_TICK_THRESHOLD = 2000;
	private static final int MAX_TICK_THREADS = 4;

	private static ClientDanmakuCache CACHE = null;
	private static EntityRenderer[] RENDERERS;

	private static <T extends SimplifiedProjectile> EntityRenderer<T> getRenderer(EntityRenderDispatcher disp, T e) {
		int id = e.getTypeId();
		if (RENDERERS == null || RENDERERS.length <= id) {
			RENDERERS = new EntityRenderer[id + 1];
		}
		if (RENDERERS[id] == null) {
			RENDERERS[id] = disp.getRenderer(e);
		}
		return Wrappers.cast(RENDERERS[id]);
	}

	public static ClientDanmakuCache get(Level level) {
		if (CACHE == null || CACHE.level != level) {
			CACHE = new ClientDanmakuCache(level);
		}
		return CACHE;
	}

	private final Level level;
	private final LinkedList<SimplifiedProjectile> all = new LinkedList<>();
	private final Int2ObjectOpenHashMap<SimplifiedProjectile> map = new Int2ObjectOpenHashMap<>(2048);


	public ClientDanmakuCache(Level level) {
		this.level = level;
	}

	public void add(SimplifiedProjectile sp) {
		all.add(sp);
		map.put(sp.getId(), sp);
	}

	/**
	 * Snapshot of virtual client danmaku (not in world entity list).
	 * Used by player auto-dodge pilot threat scan.
	 */
	public List<SimplifiedProjectile> snapshot() {
		if (all.isEmpty()) return List.of();
		return new ArrayList<>(all);
	}

	public SimplifiedProjectile get(int id) {
		return map.get(id);
	}

	public int size() {
		return all.size();
	}

	public void erase(int id, boolean kill) {
		var e = map.get(id);
		if (e != null) {
			e.markErased(kill);
		}
	}

	public void tick() {
		int size = all.size();
		if (size == 0) return;

		if (size < PARALLEL_TICK_THRESHOLD) {
			// Single-threaded path: original behavior
			var itr = all.iterator();
			while (itr.hasNext()) {
				var e = itr.next();
				e.setOldPosAndRot();
				++e.tickCount;
				e.tick();
				if (!e.isValid()) {
					itr.remove();
					map.remove(e.getId());
				}
			}
			return;
		}

		// PE-1: Parallel tick for virtualized client danmaku.
		// These entities are NOT in the world (isAddedToWorld() == false),
		// so their tick() is purely self-contained:
		//   - No level.clip() interaction (onHit is no-op on client)
		//   - No entity collision (ServerLevel check skips it)
		//   - setPosRaw directly assigns position (no world index update)
		//   - Each entity's mover is its own instance (no cross-entity state)
		// Therefore the entire tick() can safely run in parallel.

		// Snapshot to ArrayList for indexed parallel access
		List<SimplifiedProjectile> snapshot = new ArrayList<>(all);
		for (SimplifiedProjectile projectile : snapshot) {
			if (projectile instanceof AsyncProjectile async) {
				async.prepareParallelTick();
			}
		}

		// Parallel: full tick for each entity
		try {
			int threads = Math.min(MAX_TICK_THREADS, Runtime.getRuntime().availableProcessors());
			if (threads <= 1) threads = 2;
			int chunkSize = (size + threads - 1) / threads;
			ForkJoinTask<?>[] tasks = new ForkJoinTask<?>[threads];

			for (int t = 0; t < threads; t++) {
				int from = t * chunkSize;
				int to = Math.min(from + chunkSize, size);
				if (from >= to) continue;
				tasks[t] = ForkJoinPool.commonPool().submit(() -> {
					for (int i = from; i < to; i++) {
						var e = snapshot.get(i);
						e.setOldPosAndRot();
						++e.tickCount;
						if (e instanceof AsyncProjectile async) async.tickAfterParallelPreparation();
						else e.tick();
					}
				});
			}
			for (var task : tasks) {
				if (task != null) task.join();
			}
		} catch (Exception ex) {
			LOGGER.warn("Parallel client tick failed, falling back to single-threaded", ex);
			for (var e : snapshot) {
				e.setOldPosAndRot();
				++e.tickCount;
				if (e instanceof AsyncProjectile async) async.tickAfterParallelPreparation();
				else e.tick();
			}
		}

		// Single-threaded: remove invalid entities
		var itr = all.iterator();
		while (itr.hasNext()) {
			var e = itr.next();
			if (!e.isValid()) {
				itr.remove();
				map.remove(e.getId());
			}
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public void renderAll(Camera cam, Frustum frustum, PoseStack pose, float pTick, MultiBufferSource.BufferSource buffer) {
		Vec3 vec3 = cam.getPosition();
		double camx = vec3.x();
		double camy = vec3.y();
		double camz = vec3.z();
		EntityRenderDispatcher disp = Minecraft.getInstance().getEntityRenderDispatcher();
		boolean renderHitBoxes = disp.shouldRenderHitBoxes() && !Minecraft.getInstance().showOnlyReducedInfo();

		// PE-2: Extract view matrix once for billboard fast path (same idea as PB3 for preview).
		// For billboard types, bypass PoseStack push/translate/scale/pop entirely.
		// Oculus-compatible: vertices ultimately flow through BulkDataWriter.bulkWrite which
		// uses vanilla's putBulkData(ByteBuffer) — Embeddium does not intercept that overload.
		// Skipping EntityRenderDispatcher.render also bypasses Iris's setCurrentEntity context,
		// which is harmless because POSITION_TEX_COLOR shader does not read iris_Entity.
		Matrix4f viewMat = pose.last().pose();
		float viewScale = (float) Math.cbrt(Math.abs(viewMat.determinant3x3()));

		// Cache danmaku renderer (all ItemDanmakuEntity share the same EntityType → same renderer)
		ItemDanmakuRenderer<?> cachedRenderer = null;

		for (var e : all) {
			// Billboard fast path for ItemDanmakuEntity
			if (e instanceof ItemDanmakuEntity danmaku) {
				DanmakuItem item = danmaku.getItem().getItem() instanceof DanmakuItem di ? di : null;
				if (item != null) {
					acceptGiantOverlay(danmaku, item, pTick, camx, camy, camz);
				}
				if (cachedRenderer == null) {
					var r = getRenderer(disp, e);
					if (r instanceof ItemDanmakuRenderer<?> dr) cachedRenderer = dr;
				}
				if (cachedRenderer != null) {
					if (!((EntityRenderer) cachedRenderer).shouldRender(danmaku, frustum, camx, camy, camz)) continue;
					if (item == null) continue;

					var typeHolder = item.getTypeForRender();
					var type = typeHolder.getType();

					// Camera-relative world position + render offset (bbHeight/2)
					float wx = (float) (Mth.lerp(pTick, danmaku.xOld, danmaku.getX()) - camx);
					float wy = (float) (Mth.lerp(pTick, danmaku.yOld, danmaku.getY()) - camy + danmaku.getBbHeight() / 2.0);
					float wz = (float) (Mth.lerp(pTick, danmaku.zOld, danmaku.getZ()) - camz);
					int renderColor = cachedRenderer.color(danmaku, pTick);

					if (type instanceof SimpleProjectileType st) {
						float vx = viewMat.m00() * wx + viewMat.m10() * wy + viewMat.m20() * wz + viewMat.m30();
						float vy = viewMat.m01() * wx + viewMat.m11() * wy + viewMat.m21() * wz + viewMat.m31();
						float vz = viewMat.m02() * wx + viewMat.m12() * wy + viewMat.m22() * wz + viewMat.m32();
						float scale = viewScale * danmaku.scale();
						int col = DanmakuRenderStates.fading(st.display(), renderColor, cachedRenderer, danmaku);
						((ProjTypeHolder) typeHolder).accept(new SimpleProjectileType.Ins(vx, vy, vz, scale, col));
						continue;
					} else if (type instanceof LayeredRotatingProjectileType lt) {
						float vx = viewMat.m00() * wx + viewMat.m10() * wy + viewMat.m20() * wz + viewMat.m30();
						float vy = viewMat.m01() * wx + viewMat.m11() * wy + viewMat.m21() * wz + viewMat.m31();
						float vz = viewMat.m02() * wx + viewMat.m12() * wy + viewMat.m22() * wz + viewMat.m32();
						float scale = viewScale * danmaku.scale();
						float zAngle = (float) Math.toRadians((danmaku.tickCount + pTick) * 360f / (float) lt.rot());
						int tint = DanmakuRenderStates.fading(lt.display(), renderColor, cachedRenderer, danmaku);
						int white = (tint & 0xff000000) | 0xffffff;
						((ProjTypeHolder) typeHolder).accept(new LayeredRotatingProjectileType.Ins(
								vx, vy, vz, scale, zAngle, tint, white));
						continue;
					} else if (type instanceof RotatingProjectileType rt) {
						float vx = viewMat.m00() * wx + viewMat.m10() * wy + viewMat.m20() * wz + viewMat.m30();
						float vy = viewMat.m01() * wx + viewMat.m11() * wy + viewMat.m21() * wz + viewMat.m31();
						float vz = viewMat.m02() * wx + viewMat.m12() * wy + viewMat.m22() * wz + viewMat.m32();
						float scale = viewScale * danmaku.scale();
						float zAngle = (float) Math.toRadians((danmaku.tickCount + pTick) * 360f / (float) rt.rot());
						int col = DanmakuRenderStates.fading(rt.display(), renderColor, cachedRenderer, danmaku);
						((ProjTypeHolder) typeHolder).accept(new RotatingProjectileType.Ins(vx, vy, vz, scale, zAngle, col));
						continue;
					}
					// Non-billboard types: fall through to standard path
				}
			}

			// Standard path: PoseStack-based rendering for non-billboard types and non-danmaku entities
			this.maybeRenderEntity(disp, frustum, e, camx, camy, camz, pTick, pose, buffer, renderHitBoxes);
		}
		if (renderHitBoxes && cam.getEntity() instanceof Player pl && !all.isEmpty() &&
				!Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
			var lineBuffers = MultiBufferSource.immediate(new BufferBuilder(RenderType.lines().bufferSize()));
			renderPlayerHitbox(pose, lineBuffers.getBuffer(RenderType.lines()), pl, camx, camy, camz, pTick);
			lineBuffers.endBatch(RenderType.lines());
		}
	}

	private static void acceptGiantOverlay(ItemDanmakuEntity danmaku, DanmakuItem item, float pTick,
										 double camx, double camy, double camz) {
		if (!item.isGiant()) return;
		double x = Mth.lerp(pTick, danmaku.xOld, danmaku.getX());
		double y = Mth.lerp(pTick, danmaku.yOld, danmaku.getY()) + danmaku.getBbHeight() / 2.0;
		double z = Mth.lerp(pTick, danmaku.zOld, danmaku.getZ());
		double radius = Math.max(0.0001, danmaku.scale() * 0.5);
		double dx = camx - x;
		double dy = camy - y;
		double dz = camz - z;
		float insideScore = (float) ((radius - Math.sqrt(dx * dx + dy * dy + dz * dz)) / radius);
		GiantDanmakuScreenOverlay.accept(item.giantOverlayTexture(), danmaku.getRenderTint(pTick), insideScore);
	}

	private <E extends SimplifiedProjectile> void maybeRenderEntity(
			EntityRenderDispatcher disp, Frustum frustum, E e,
			double camx, double camy, double camz, float pTick,
			PoseStack pose, MultiBufferSource.BufferSource buffer, boolean renderHitBoxes
	) {
		EntityRenderer<E> er = getRenderer(disp, e);
		if (!er.shouldRender(e, frustum, camx, camy, camz)) return;
		double dx = Mth.lerp(pTick, e.xOld, e.getX());
		double dy = Mth.lerp(pTick, e.yOld, e.getY());
		double dz = Mth.lerp(pTick, e.zOld, e.getZ());
		this.renderEntity(e, er, dx - camx, dy - camy, dz - camz, pTick, pose, buffer, renderHitBoxes);
	}

	public <E extends SimplifiedProjectile> void renderEntity(
			E e, EntityRenderer<E> er,
			double x, double y, double z, float pTick,
			PoseStack pose, MultiBufferSource.BufferSource buffer, boolean renderHitBoxes
	) {
		if (!(er instanceof ProjectileRenderer<?> pr)) return;
		ProjectileRenderer<E> r = Wrappers.cast(pr);
		Vec3 vec3 = er.getRenderOffset(e, pTick);
		double dx = x + vec3.x();
		double dy = y + vec3.y();
		double dz = z + vec3.z();
		pose.pushPose();
		pose.translate(dx, dy, dz);
		if (er instanceof TextDanmakuRenderer<?>) {
			er.render(e, e.getYRot(), pTick, pose, buffer, LightTexture.FULL_BRIGHT);
		} else {
			r.render(e, pTick, pose);
		}
		if (renderHitBoxes) {
			pose.translate(-vec3.x(), -vec3.y(), -vec3.z());
			var lineBuffers = MultiBufferSource.immediate(new BufferBuilder(RenderType.lines().bufferSize()));
			renderHitbox(pose, lineBuffers.getBuffer(RenderType.lines()), e, pTick);
			lineBuffers.endBatch(RenderType.lines());
		}
		pose.popPose();
	}

	public static void renderHitbox(PoseStack pose, VertexConsumer vc, Entity e, float pTick) {
		AABB aabb = e.getBoundingBox().move(-e.getX(), -e.getY(), -e.getZ());
		LevelRenderer.renderLineBox(pose, vc, aabb, 1.0F, 1.0F, 1.0F, 1.0F);
		Vec3 vec3 = e.getViewVector(pTick);
		Matrix4f mat4 = pose.last().pose();
		Matrix3f mat3 = pose.last().normal();
		vc.vertex(mat4, 0.0F, e.getEyeHeight(), 0.0F)
				.color(0, 0, 255, 255)
				.normal(mat3, (float) vec3.x, (float) vec3.y, (float) vec3.z)
				.endVertex();
		vc.vertex(mat4,
						(float) (vec3.x * 2.0D),
						(float) (e.getEyeHeight() + vec3.y * 2.0D),
						(float) (vec3.z * 2.0D)
				).color(0, 0, 255, 255)
				.normal(mat3, (float) vec3.x, (float) vec3.y, (float) vec3.z)
				.endVertex();
	}

	public static void renderPlayerHitbox(PoseStack pose, VertexConsumer vc, Player e, double camx, double camy, double camz, float pTick) {
		double dx = Mth.lerp(pTick, e.xOld, e.getX()) - camx - e.getX();
		double dy = Mth.lerp(pTick, e.yOld, e.getY()) - camy - e.getY();
		double dz = Mth.lerp(pTick, e.zOld, e.getZ()) - camz - e.getZ();
		if (e.isInvisible()) {
			AABB base = e.getBoundingBox().move(dx, dy, dz);
			AABB hit = IYHDanmaku.alterEntityHitBox(e, 0, 0).move(dx, dy, dz);
			LevelRenderer.renderLineBox(pose, vc, base, 1, 1, 1, 1);
			if (!base.equals(hit)) {
				LevelRenderer.renderLineBox(pose, vc, hit, 1, 0.25f, 0.25f, 1);
			}
		}
		AABB graze = IYHDanmaku.alterEntityHitBox(e, 0, IYHDanmaku.GRAZE_RANGE).move(dx, dy, dz);
		LevelRenderer.renderLineBox(pose, vc, graze, 0.25F, 1, 0, 1);
	}

}
