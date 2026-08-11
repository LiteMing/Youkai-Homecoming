package dev.xkmc.youkaishomecoming.content.spell.certification;

import dev.xkmc.youkaishomecoming.content.spell.action.BurstAction;
import dev.xkmc.youkaishomecoming.content.spell.action.DelayAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireLaserAction;
import dev.xkmc.youkaishomecoming.content.spell.action.FireTextDanmakuAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpawnShooterAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellActions;
import dev.xkmc.youkaishomecoming.content.item.danmaku.DynamicSpellItem;
import dev.xkmc.youkaishomecoming.content.spell.definition.ColorProvider;
import dev.xkmc.youkaishomecoming.content.spell.definition.DanmakuColor;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives the certified spell item color from the danmaku colors used inside the
 * spell definition: every fire/laser/text color contributes to a blended average,
 * then a small per-channel jitter is applied (Phase 7, user balance request).
 * Falls back to a fully random color when the spell carries no readable colors.
 */
public final class SpellColorExtractor {

	private SpellColorExtractor() {
	}

	@Nullable
	public static DanmakuColor extract(SpellDefinition def) {
		List<int[]> rgb = new ArrayList<>();
		collect(def, rgb);
		if (rgb.isEmpty()) return null;
		long r = 0, g = 0, b = 0;
		for (int[] c : rgb) {
			r += c[0];
			g += c[1];
			b += c[2];
		}
		int n = rgb.size();
		return rgb((int) (r / n), (int) (g / n), (int) (b / n));
	}

	/** Blended color with a small per-channel jitter (±JITTER), or null. */
	public static DanmakuColor extractWithJitter(SpellDefinition def, RandomSource random) {
		DanmakuColor base = extract(def);
		if (base == null) return null;
		int r = clamp(channel(base, 16) + random.nextInt(-JITTER, JITTER + 1));
		int g = clamp(channel(base, 8) + random.nextInt(-JITTER, JITTER + 1));
		int b = clamp(channel(base, 0) + random.nextInt(-JITTER, JITTER + 1));
		return rgb(r, g, b);
	}

	/** Applies the shared definition-derived color used by complete spell items. */
	public static ItemStack applyToStack(ItemStack stack, SpellDefinition def, RandomSource random) {
		DanmakuColor color = extractWithJitter(def, random);
		return color == null
				? DynamicSpellItem.withRandomColor(stack, random)
				: DynamicSpellItem.withColor(stack, color);
	}

	private static final int JITTER = 24;

	private static int channel(DanmakuColor color, int shift) {
		return (color.argb() >> shift) & 0xFF;
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	private static DanmakuColor rgb(int r, int g, int b) {
		return new DanmakuColor(0xFF000000 | (r << 16) | (g << 8) | b);
	}

	// ------------------------------------------------------------ collection

	private static void collect(SpellDefinition def, List<int[]> out) {
		for (PhaseDefinition phase : def.phases.values()) {
			collect(phase.onEnter, out);
			collect(phase.onTick, out);
			collect(phase.onExit, out);
			collect(phase.onDamage, out);
		}
	}

	private static void collect(List<SpellAction> actions, List<int[]> out) {
		for (SpellAction action : actions) {
			collect(action, out);
		}
	}

	private static void collect(SpellAction action, List<int[]> out) {
		if (action instanceof FireDanmakuAction fire) {
			addColorProvider(fire.color(), out);
			collectHook(fire.onExpiry(), out);
			collectHook(fire.onTrail(), out);
			collectHook(fire.onHitEntity(), out);
			collectHook(fire.onHitBlock(), out);
			return;
		}
		if (action instanceof FireLaserAction laser) {
			out.add(dyeRgb(laser.color()));
			collectHook(laser.onExpiry(), out);
			collectHook(laser.onTrail(), out);
			collectHook(laser.onHitEntity(), out);
			collectHook(laser.onHitBlock(), out);
			return;
		}
		if (action instanceof FireTextDanmakuAction text) {
			int argb = text.textColor();
			out.add(new int[]{(argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF});
			return;
		}
		if (action instanceof SpellActions.ConditionalAction cond) {
			collect(cond.ifTrue(), out);
			collect(cond.ifFalse(), out);
		} else if (action instanceof SpellActions.SequenceAction seq) {
			collect(seq.actions(), out);
		} else if (action instanceof SpellActions.RepeatAction rep) {
			collect(rep.body(), out);
		} else if (action instanceof DelayAction delay) {
			collect(delay.body(), out);
		} else if (action instanceof BurstAction burst) {
			collect(burst.body(), out);
		} else if (action instanceof SpawnShooterAction shooter) {
			collect(shooter.body(), out);
		}
	}

	private static void collectHook(java.util.Optional<List<SpellAction>> hook, List<int[]> out) {
		hook.ifPresent(list -> collect(list, out));
	}

	private static void addColorProvider(ColorProvider provider, List<int[]> out) {
		if (provider instanceof ColorProvider.Constant constant) {
			DanmakuColor color = constant.color();
			out.add(new int[]{channel(color, 16), channel(color, 8), channel(color, 0)});
		}
		// dynamic providers are skipped: their colors cannot be read statically
	}

	private static int[] dyeRgb(net.minecraft.world.item.DyeColor dye) {
		int argb = DanmakuColor.of(dye).argb();
		return new int[]{(argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF};
	}
}
