package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.DanmakuHelper;
import dev.xkmc.youkaishomecoming.content.entity.danmaku.TextDanmakuEntity;
import dev.xkmc.youkaishomecoming.content.spell.definition.AimMode;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuDamageType;
import dev.xkmc.youkaishomecoming.content.spell.definition.MoverConfig;
import dev.xkmc.youkaishomecoming.content.spell.definition.NumberProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.OriginConfig;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import dev.xkmc.youkaishomecoming.content.spell.spellcard.CardHolder;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Data-driven action to fire a text danmaku — text is rendered along its flight direction.
 * <p>
 * When {@code per_char} is true, each character becomes its own danmaku entity so they can
 * scatter with independent movers.
 */
public record FireTextDanmakuAction(
		String text,
		int textColor,
		boolean perChar,
		NumberProvider lifetime,
		NumberProvider length,
		NumberProvider angleOffset,
		NumberProvider elevation,
		AimMode aimMode,
		OriginConfig origin,
		Optional<MoverConfig> mover,
		int setupPrepare,
		int setupStart,
		int setupEnd,
		Optional<DanmakuDamageType> damageType
) implements SpellAction {

	public static final Codec<FireTextDanmakuAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("text").forGetter(FireTextDanmakuAction::text),
			Codec.INT.optionalFieldOf("text_color", 0xFFFFFFFF).forGetter(FireTextDanmakuAction::textColor),
			Codec.BOOL.optionalFieldOf("per_char", false).forGetter(FireTextDanmakuAction::perChar),
			NumberProvider.CODEC.fieldOf("lifetime").forGetter(FireTextDanmakuAction::lifetime),
			NumberProvider.CODEC.optionalFieldOf("length", NumberProvider.constant(4)).forGetter(FireTextDanmakuAction::length),
			NumberProvider.CODEC.optionalFieldOf("angle_offset", NumberProvider.constant(0)).forGetter(FireTextDanmakuAction::angleOffset),
			NumberProvider.CODEC.optionalFieldOf("elevation", NumberProvider.constant(0)).forGetter(FireTextDanmakuAction::elevation),
			AimMode.CODEC.optionalFieldOf("aim_mode", new AimMode.AimModes.Target()).forGetter(FireTextDanmakuAction::aimMode),
			OriginConfig.CODEC.optionalFieldOf("origin", OriginConfig.caster()).forGetter(FireTextDanmakuAction::origin),
			MoverConfig.CODEC.optionalFieldOf("mover").forGetter(FireTextDanmakuAction::mover),
			Codec.INT.optionalFieldOf("setup_prepare", 0).forGetter(FireTextDanmakuAction::setupPrepare),
			Codec.INT.optionalFieldOf("setup_start", 0).forGetter(FireTextDanmakuAction::setupStart),
			Codec.INT.optionalFieldOf("setup_end", 0).forGetter(FireTextDanmakuAction::setupEnd),
			DanmakuDamageType.CODEC.optionalFieldOf("damage_type").forGetter(FireTextDanmakuAction::damageType)
	).apply(i, FireTextDanmakuAction::new));

	private FireTextDanmakuAction copy(String t, int tc, boolean pc, NumberProvider lt, NumberProvider ln, NumberProvider ao, NumberProvider el, AimMode am, OriginConfig o, Optional<MoverConfig> m, int sp, int ss, int se, Optional<DanmakuDamageType> dt) {
		return new FireTextDanmakuAction(t, tc, pc, lt, ln, ao, el, am, o, m, sp, ss, se, dt);
	}
	public FireTextDanmakuAction withText(String v) { return copy(v, textColor, perChar, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, damageType); }
	public FireTextDanmakuAction withTextColor(int v) { return copy(text, v, perChar, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, damageType); }
	public FireTextDanmakuAction withPerChar(boolean v) { return copy(text, textColor, v, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, damageType); }
	public FireTextDanmakuAction withLifetime(NumberProvider v) { return copy(text, textColor, perChar, v, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, damageType); }
	public FireTextDanmakuAction withLength(NumberProvider v) { return copy(text, textColor, perChar, lifetime, v, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, damageType); }
	public FireTextDanmakuAction withAngleOffset(NumberProvider v) { return copy(text, textColor, perChar, lifetime, length, v, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, damageType); }
	public FireTextDanmakuAction withElevation(NumberProvider v) { return copy(text, textColor, perChar, lifetime, length, angleOffset, v, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, damageType); }
	public FireTextDanmakuAction withAimMode(AimMode v) { return copy(text, textColor, perChar, lifetime, length, angleOffset, elevation, v, origin, mover, setupPrepare, setupStart, setupEnd, damageType); }
	public FireTextDanmakuAction withOrigin(OriginConfig v) { return copy(text, textColor, perChar, lifetime, length, angleOffset, elevation, aimMode, v, mover, setupPrepare, setupStart, setupEnd, damageType); }
	public FireTextDanmakuAction withMover(Optional<MoverConfig> v) { return copy(text, textColor, perChar, lifetime, length, angleOffset, elevation, aimMode, origin, v, setupPrepare, setupStart, setupEnd, damageType); }
	public FireTextDanmakuAction withSetupPrepare(int v) { return copy(text, textColor, perChar, lifetime, length, angleOffset, elevation, aimMode, origin, mover, v, setupStart, setupEnd, damageType); }
	public FireTextDanmakuAction withSetupStart(int v) { return copy(text, textColor, perChar, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, v, setupEnd, damageType); }
	public FireTextDanmakuAction withSetupEnd(int v) { return copy(text, textColor, perChar, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, v, damageType); }
	public FireTextDanmakuAction withDamageType(Optional<DanmakuDamageType> v) { return copy(text, textColor, perChar, lifetime, length, angleOffset, elevation, aimMode, origin, mover, setupPrepare, setupStart, setupEnd, v); }

	@Override
	public void execute(SpellContext ctx) {
		if (text == null || text.isEmpty()) return;

		CardHolder holder = ctx.holder();
		int life = (int) lifetime.get(ctx);
		float len = (float) length.get(ctx);
		double angle = angleOffset.get(ctx);
		double elev = elevation.get(ctx);

		Vec3 originPos = origin.resolve(ctx);
		Vec3 baseDir = aimMode.getBaseDirection(ctx, originPos);

		double originRot = origin.rotation().get(ctx);
		if (originRot != 0) {
			double rad = Math.toRadians(originRot);
			double cos = Math.cos(rad), sin = Math.sin(rad);
			baseDir = new Vec3(
					baseDir.x * cos - baseDir.z * sin,
					baseDir.y,
					baseDir.x * sin + baseDir.z * cos
			);
		}

		Vec3 dir;
		if (angle != 0 || elev != 0) {
			var ori = DanmakuHelper.getOrientation(baseDir);
			dir = ori.rotateDegrees(angle, elev);
		} else {
			dir = baseDir;
		}

		// Resolve text placeholders
		String resolvedText = resolveTextPlaceholders(text, ctx);

		if (perChar) {
			int n = resolvedText.codePointCount(0, resolvedText.length());
			if (n == 0) return;
			float segLen = len / n;
			int idx = 0;
			int cp;
			for (int pos = 0; pos < resolvedText.length(); pos += Character.charCount(cp)) {
				cp = resolvedText.codePointAt(pos);
				String ch = new String(Character.toChars(cp));
				// Place each character at its own spawn offset along the flight direction.
				// Use (n - idx - 0.5) to reverse the order so first character is at the front
				Vec3 charPos = originPos.add(dir.scale(segLen * (n - idx - 0.5)));
				spawn(holder, life, charPos, dir, segLen, ch);
				idx++;
			}
		} else {
			spawn(holder, life, originPos, dir, len, resolvedText);
		}
	}

	/**
	 * Resolve text placeholders like {spell_name}, {caster_name}, etc.
	 */
	private String resolveTextPlaceholders(String text, SpellContext ctx) {
		String result = text;
		
		// {spell_id} - spell card definition ID
		if (result.contains("{spell_id}") && ctx.definition() != null) {
			String spellId = ctx.definition().id.toString();
			result = result.replace("{spell_id}", spellId);
		}
		
		// {caster_name} - caster entity name
		if (result.contains("{caster_name}")) {
			String casterName = ctx.holder().self().getName().getString();
			result = result.replace("{caster_name}", casterName);
		}
		
		return result;
	}

	private void spawn(CardHolder holder, int life, Vec3 pos, Vec3 dir, float len, String str) {
		TextDanmakuEntity e = holder.prepareTextDanmaku(life, pos, dir, len, str, textColor);
		e.perChar = perChar;
		if (damageType.isPresent()) {
			e.damageTypeOverride = damageType.get();
		}
		if (setupPrepare > 0 || setupStart > 0 || setupEnd > 0) {
			e.setupTime(setupPrepare, setupStart, life, setupEnd);
		}
		if (mover.isPresent()) {
			e.mover = mover.get().create(pos, dir);
		}
		holder.shoot(e);
	}

}
