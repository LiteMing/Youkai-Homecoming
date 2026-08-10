package dev.xkmc.fastprojectileapi.render.core;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.xkmc.fastprojectileapi.compat.oculus.OculusRenderCompat;
import dev.xkmc.fastprojectileapi.entity.SimplifiedProjectile;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.IYHDanmaku;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiFunction;
import java.util.function.Function;

public abstract class DanmakuRenderStates extends RenderType {


	public DanmakuRenderStates(String pName, VertexFormat pFormat, VertexFormat.Mode pMode, int pBufferSize, boolean pAffectsCrumbling, boolean pSortOnUpload, Runnable pSetupState, Runnable pClearState) {
		super(pName, pFormat, pMode, pBufferSize, pAffectsCrumbling, pSortOnUpload, pSetupState, pClearState);
	}

	protected static final ShaderStateShard DANMAKU_SHADER = new ShaderStateShard(GameRenderer::getPositionTexColorShader);
	protected static final ShaderStateShard DANMAKU_COLOR_SHADER = new ShaderStateShard(GameRenderer::getPositionColorShader);

	private static RenderType create(String name, ResourceLocation tex, boolean cull, DisplayType type, boolean noDepthWrite, boolean isLaserShell) {
		// sortOnUpload=false: skip per-frame quad distance sorting for danmaku.
		// Sorting 30,000 quads costs ~9% frame time (putSortedQuadIndices).
		// Danmaku are small particles where depth sort order is visually negligible.
		//
		// Laser rendering strategy (Oculus compatibility):
		// - Both shell and core: don't write depth (COLOR_WRITE)
		// - Both shell and core: normal depth test (LEQUAL_DEPTH_TEST)
		// - Core in DECAL bucket: render after shell
		//
		// This allows the core to render "inside" the shell (no depth conflict)
		// while both are properly occluded by solid blocks that wrote depth earlier.
		// The shell/core lose depth ordering with other transparent objects, but
		// this is acceptable for the laser visual effect.
		boolean opaque = type == DisplayType.SOLID;
		boolean noDepthWriteForLaser = isLaserShell || noDepthWrite; // shell or core
		return create(name,
				DefaultVertexFormat.POSITION_TEX_COLOR,
				VertexFormat.Mode.QUADS,
				256, true, false,
				CompositeState.builder()
						.setShaderState(DANMAKU_SHADER)
						.setTextureState(new TextureStateShard(tex, false, false))
						.setTransparencyState(switch (type) {
							case SOLID -> NO_TRANSPARENCY;
							case TRANSPARENT -> TRANSLUCENT_TRANSPARENCY;
							case ADDITIVE -> ADDITIVE_TRANSPARENCY;
						})
						.setWriteMaskState((opaque || !noDepthWriteForLaser) ? COLOR_DEPTH_WRITE : COLOR_WRITE)
						.setDepthTestState(LEQUAL_DEPTH_TEST)
						.setCullState(cull ? CULL : NO_CULL)
						.createCompositeState(false));
	}

	private static RenderType createColor(String name, boolean cull, DisplayType type, boolean noDepthWrite) {
		boolean opaque = type == DisplayType.SOLID;
		return create(name,
				DefaultVertexFormat.POSITION_COLOR,
				VertexFormat.Mode.QUADS,
				256, true, false,
				CompositeState.builder()
						.setShaderState(DANMAKU_COLOR_SHADER)
						.setTransparencyState(switch (type) {
							case SOLID -> NO_TRANSPARENCY;
							case TRANSPARENT -> TRANSLUCENT_TRANSPARENCY;
							case ADDITIVE -> ADDITIVE_TRANSPARENCY;
						})
						.setWriteMaskState((opaque || !noDepthWrite) ? COLOR_DEPTH_WRITE : COLOR_WRITE)
						.setDepthTestState(LEQUAL_DEPTH_TEST)
						.setCullState(cull ? CULL : NO_CULL)
						.createCompositeState(false));
	}

	private static RenderType createDepth(String name, ResourceLocation tex, boolean color) {
		var builder = CompositeState.builder()
				.setShaderState(color ? DANMAKU_COLOR_SHADER : DANMAKU_SHADER)
				.setTransparencyState(NO_TRANSPARENCY)
				.setWriteMaskState(DEPTH_WRITE)
				.setDepthTestState(LEQUAL_DEPTH_TEST)
				.setCullState(CULL);
		if (!color) {
			builder.setTextureState(new TextureStateShard(tex, false, false));
		}
		return create(name,
				color ? DefaultVertexFormat.POSITION_COLOR : DefaultVertexFormat.POSITION_TEX_COLOR,
				VertexFormat.Mode.QUADS,
				256, true, false,
				builder.createCompositeState(false));
	}

	private static final BiFunction<ResourceLocation, DisplayType, RenderType> DANMAKU =
			Util.memoize((rl, type) -> create("danmaku_" + type.getName(), rl, false, type, false, false));
	private static final BiFunction<ResourceLocation, DisplayType, RenderType> DANMAKU_SPHERE =
			Util.memoize((rl, type) -> create("danmaku_sphere_" + type.getName(), rl, true, type, true, false));
	private static final Function<ResourceLocation, RenderType> DANMAKU_SPHERE_DEPTH =
			Util.memoize(rl -> createDepth("danmaku_sphere_depth", rl, false));
	private static final Function<DisplayType, RenderType> DANMAKU_COLOR_SPHERE =
			Util.memoize(type -> createColor("danmaku_color_sphere_" + type.getName(), true, type, true));
	private static final RenderType DANMAKU_COLOR_SPHERE_DEPTH =
			createDepth("danmaku_color_sphere_depth", null, true);
	private static final BiFunction<ResourceLocation, DisplayType, RenderType> LASER =
			Util.memoize((rl, type) -> create("laser_" + type.getName(), rl, true, type, false, true));
	// Laser inner-core uses a separate RenderType so it can be routed into Oculus's DECAL
	// bucket — drawn AFTER the GENERAL_TRANSPARENT shell, so the translucent shell never
	// hides the core under Oculus's batched-entity-rendering pipeline. Vanilla just sees
	// two RenderTypes flushed in submission order, which is also correct.
	// Both shell and core don't write depth to allow the core to render through the shell.
	private static final BiFunction<ResourceLocation, DisplayType, RenderType> LASER_CORE =
			Util.memoize((rl, type) -> {
				RenderType rt = create("laser_core_" + type.getName(), rl, true, type, true, false);
				OculusRenderCompat.markAsDecal(rt);
				return rt;
			});

	public static RenderType danmaku(ResourceLocation rl, DisplayType type) {
		if (type == DisplayType.SOLID) type = DisplayType.TRANSPARENT;
		return DANMAKU.apply(rl, type);
	}

	public static RenderType danmakuSphere(ResourceLocation rl, DisplayType type) {
		if (type == DisplayType.SOLID) type = DisplayType.TRANSPARENT;
		return DANMAKU_SPHERE.apply(rl, type);
	}

	public static RenderType danmakuSphereDepth(ResourceLocation rl) {
		return DANMAKU_SPHERE_DEPTH.apply(rl);
	}

	public static RenderType danmakuColorSphere(DisplayType type) {
		if (type == DisplayType.SOLID) type = DisplayType.TRANSPARENT;
		return DANMAKU_COLOR_SPHERE.apply(type);
	}

	public static RenderType danmakuColorSphereDepth() {
		return DANMAKU_COLOR_SPHERE_DEPTH;
	}

	public static RenderType laser(ResourceLocation rl, DisplayType type) {
		return LASER.apply(rl, type);
	}

	public static RenderType laserCore(ResourceLocation rl, DisplayType type) {
		return LASER_CORE.apply(rl, type);
	}

	public static int fading(DisplayType display, int col, ProjectileRenderer<?> r, SimplifiedProjectile e) {
		double perc = r.fading(e);
		if (perc == 0) return col;
		int alpha = (int) ((col >>> 24) * perc);
		return (alpha << 24) | col & 0xffffff;
	}

	public static double localPlayerDamageVisibility(SimplifiedProjectile projectile) {
		var player = Minecraft.getInstance().player;
		if (player == null || !(projectile instanceof IYHDanmaku danmaku)
				|| !danmaku.hasHarmfulPlayerSnapshot() || danmaku.isHarmfulToPlayer(player.getUUID())) {
			return 1;
		}
		return YHModConfig.CLIENT.selfDanmakuFading.get();
	}

}
