package dev.xkmc.youkaishomecoming.content.spell.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.youkaishomecoming.content.spell.action.SpellAction;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class PhaseDefinition {

	public static final Codec<PhaseDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.fieldOf("id").forGetter(p -> p.id),
			SpellAction.CODEC.listOf().optionalFieldOf("on_enter", List.of()).forGetter(p -> p.onEnter),
			SpellAction.CODEC.listOf().optionalFieldOf("on_tick", List.of()).forGetter(p -> p.onTick),
			SpellAction.CODEC.listOf().optionalFieldOf("on_exit", List.of()).forGetter(p -> p.onExit),
			SpellAction.CODEC.listOf().optionalFieldOf("on_damage", List.of()).forGetter(p -> p.onDamage),
			Transition.CODEC.listOf().optionalFieldOf("transitions", List.of()).forGetter(p -> p.transitions)
	).apply(i, PhaseDefinition::new));

	public final ResourceLocation id;
	public final List<SpellAction> onEnter;
	public final List<SpellAction> onTick;
	public final List<SpellAction> onExit;
	public final List<SpellAction> onDamage;
	public final List<Transition> transitions;

	public PhaseDefinition(ResourceLocation id,
						   List<SpellAction> onEnter,
						   List<SpellAction> onTick,
						   List<SpellAction> onExit,
						   List<SpellAction> onDamage,
						   List<Transition> transitions) {
		this.id = id;
		this.onEnter = new ArrayList<>(onEnter);
		this.onTick = new ArrayList<>(onTick);
		this.onExit = new ArrayList<>(onExit);
		this.onDamage = new ArrayList<>(onDamage);
		this.transitions = new ArrayList<>(transitions);
	}

	public static PhaseDefinition singlePhase(ResourceLocation id, SpellAction tickAction) {
		return new PhaseDefinition(id, List.of(), List.of(tickAction), List.of(), List.of(), List.of());
	}
}
