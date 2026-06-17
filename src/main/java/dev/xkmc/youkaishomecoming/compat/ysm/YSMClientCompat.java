package dev.xkmc.youkaishomecoming.compat.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.xkmc.youkaishomecoming.content.entity.boss.BossYoukaiEntity;
import dev.xkmc.youkaishomecoming.content.entity.boss.RemiliaEntity;
import dev.xkmc.youkaishomecoming.content.entity.youkai.GeneralYoukaiEntity;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class YSMClientCompat {

	private static final String MOD_ID = "yes_steve_model";
	private static final String MODEL_REMILIA = "yh/remilia";
	private static final String TEXTURE_DEFAULT = "default";
	private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);

	private static Method renderMethod;
	private static boolean unavailable;

	public static boolean delegateRender(GeneralYoukaiEntity e, float yaw, float pTick, PoseStack pose, MultiBufferSource buffer, int light) {
		if (!LOADED || unavailable || !(e instanceof RemiliaEntity)) {
			return false;
		}
		Method method = getRenderMethod();
		if (method == null) {
			return false;
		}
		try {
			Object result = method.invoke(null, e, MODEL_REMILIA, TEXTURE_DEFAULT, selectAnimation(e), yaw, pTick, pose, buffer, light);
			return result instanceof Boolean value && value;
		} catch (IllegalAccessException | InvocationTargetException ex) {
			unavailable = true;
			YoukaisHomecoming.LOGGER.warn("Failed to delegate Remilia rendering to Yes Steve Model", ex);
			return false;
		}
	}

	private static Method getRenderMethod() {
		if (renderMethod != null) {
			return renderMethod;
		}
		try {
			renderMethod = Class.forName("rip.ysm.api.client.ExternalLivingRenderAPI").getMethod(
					"render",
					LivingEntity.class,
					String.class,
					String.class,
					String.class,
					float.class,
					float.class,
					PoseStack.class,
					MultiBufferSource.class,
					int.class
			);
			return renderMethod;
		} catch (ClassNotFoundException | NoSuchMethodException ex) {
			unavailable = true;
			YoukaisHomecoming.LOGGER.warn("Yes Steve Model external living renderer API is unavailable", ex);
			return null;
		}
	}

	private static String selectAnimation(GeneralYoukaiEntity e) {
		Vec3 motion = e.getDeltaMovement();
		double horizontalSpeedSqr = motion.x * motion.x + motion.z * motion.z;
		boolean flying = e.isFlying() || e.isNoGravity();
		if (flying) {
			return "fly";
		}
		if (!e.onGround()) {
			return null;
		}
		if (horizontalSpeedSqr > 0.0025) {
			return "walk";
		}
		if (e instanceof BossYoukaiEntity boss && boss.isChaotic()) {
			return "angry";
		}
		if (e.getTarget() != null) {
			return "angry";
		}
		return "calm";
	}
}
