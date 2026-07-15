package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.entity.youkai.YoukaiEntity;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;

/**
 * Data-driven action to set an entity flag on the caster.
 * <p>
 * Entity flags are bitfield values on {@link YoukaiEntity} that control visual states
 * and behavior modes. For example:
 * <ul>
 *   <li>Flag 4: Abyssal mode (used by Reimu — enables enhanced damage and visual effects)</li>
 *   <li>Flag 16: Feed cooldown visual</li>
 *   <li>Flag 32: Boss rage visual</li>
 * </ul>
 * <p>
 * JSON: {@code {"type": "set_entity_flag", "flag": 4, "enable": true}}
 */
public record SetEntityFlagAction(int flag, boolean enable) implements SpellAction {

	public static final Codec<SetEntityFlagAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("flag").forGetter(SetEntityFlagAction::flag),
			Codec.BOOL.optionalFieldOf("enable", true).forGetter(SetEntityFlagAction::enable)
	).apply(i, SetEntityFlagAction::new));

	@Override
	public void execute(SpellContext ctx) {
		if (ctx.self() instanceof YoukaiEntity youkai) {
			youkai.setFlag(flag, enable);
		}
	}
}
