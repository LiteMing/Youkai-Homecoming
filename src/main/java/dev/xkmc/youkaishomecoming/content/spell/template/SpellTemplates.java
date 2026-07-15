package dev.xkmc.youkaishomecoming.content.spell.template;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.xkmc.youkaishomecoming.content.spell.definition.PhaseDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDefinition;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellDisplay;
import dev.xkmc.youkaishomecoming.content.spell.definition.SpellItemForm;
import dev.xkmc.youkaishomecoming.content.spell.difficulty.DifficultyProfile;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SpellTemplates {

	private static final Map<String, String> TEMPLATES = new LinkedHashMap<>();

	static {
		TEMPLATES.put("basic", """
				{
				  "id": "%1$s",
				  "display": {
				    "name": "youkaishomecoming.spell_template.basic.name",
				    "description": "youkaishomecoming.spell_template.basic.desc"
				  },
				  "entry_phase": "%2$s",
				  "phases": {
				    "%2$s": {
				      "id": "%2$s",
				      "on_tick": [
				        {
				          "type": "conditional",
				          "condition": { "type": "tick_interval", "interval": 20 },
				          "if_true": [
				            {
				              "type": "fire_danmaku",
				              "bullet": "ball",
				              "color": "cyan",
				              "count": 12,
				              "speed": 0.45,
				              "lifetime": 60,
				              "pattern": "ring",
				              "aim_mode": "direction_to_target"
				            }
				          ]
				        }
				      ]
				    }
				  },
				  "custom_names": {
				    "tick/0": "youkaishomecoming.spell_template.basic.node.interval",
				    "tick/0:true/0": "youkaishomecoming.spell_template.basic.node.fire_ring"
				  }
				}
				""");
		TEMPLATES.put("ring", """
				{
				  "id": "%1$s",
				  "display": {
				    "name": "youkaishomecoming.spell_template.ring.name",
				    "description": "youkaishomecoming.spell_template.ring.desc"
				  },
				  "entry_phase": "%2$s",
				  "phases": {
				    "%2$s": {
				      "id": "%2$s",
				      "on_tick": [
				        {
				          "type": "conditional",
				          "condition": { "type": "tick_interval", "interval": 6 },
				          "if_true": [
				            {
				              "type": "fire_danmaku",
				              "bullet": "spark",
				              "color": { "type": "cycle", "palette": ["red", "yellow", "white"], "interval": 20 },
				              "count": 24,
				              "speed": 0.35,
				              "lifetime": 90,
				              "angle_offset": "tick * 4",
				              "spread": 360,
				              "elevation": 0,
				              "pattern": "ring",
				              "aim_mode": "direction_to_target"
				            }
				          ]
				        }
				      ]
				    }
				  },
				  "custom_names": {
				    "tick/0": "youkaishomecoming.spell_template.ring.node.interval",
				    "tick/0:true/0": "youkaishomecoming.spell_template.ring.node.rotating_ring"
				  }
				}
				""");
		TEMPLATES.put("mover", """
				{
				  "id": "%1$s",
				  "display": {
				    "name": "youkaishomecoming.spell_template.mover.name",
				    "description": "youkaishomecoming.spell_template.mover.desc"
				  },
				  "entry_phase": "%2$s",
				  "phases": {
				    "%2$s": {
				      "id": "%2$s",
				      "on_tick": [
				        {
				          "type": "conditional",
				          "condition": { "type": "tick_interval", "interval": 8 },
				          "if_true": [
				            {
				              "type": "fire_danmaku",
				              "bullet": "talisman",
				              "color": "magenta",
				              "count": 10,
				              "speed": 0.25,
				              "lifetime": 100,
				              "angle_offset": "tick * 7",
				              "spread": 120,
				              "pattern": "line",
				              "aim_mode": "direction_to_target",
				              "mover": {
				                "type": "formula",
				                "x": "tick * 0.08",
				                "y": "1.6 * sin_rad(tick * 0.16)",
				                "z": "0",
				                "speed": 0.15
				              }
				            }
				          ]
				        }
				      ]
				    }
				  },
				  "custom_names": {
				    "tick/0": "youkaishomecoming.spell_template.mover.node.interval",
				    "tick/0:true/0": "youkaishomecoming.spell_template.mover.node.sine_wave"
				  }
				}
				""");
		TEMPLATES.put("shooter", """
				{
				  "id": "%1$s",
				  "display": {
				    "name": "youkaishomecoming.spell_template.shooter.name",
				    "description": "youkaishomecoming.spell_template.shooter.desc"
				  },
				  "entry_phase": "%2$s",
				  "phases": {
				    "%2$s": {
				      "id": "%2$s",
				      "on_tick": [
				        {
				          "type": "conditional",
				          "condition": { "type": "tick_interval", "interval": 40 },
				          "if_true": [
				            {
				              "type": "spawn_shooter",
				              "health": 20,
				              "damage": 4,
				              "lifetime": 120,
				              "count": 3,
				              "speed": 0.1,
				              "spread": 60,
				              "pattern": "line",
				              "aim_mode": "direction_to_target",
				              "mover": {
				                "type": "orbital",
				                "angular_speed": 4,
				                "radius": "1.5 + 0.4 * sin_rad(tick * 0.08)",
				                "drift": "tick * 0.01"
				              },
				              "body": [
				                {
				                  "type": "conditional",
				                  "condition": { "type": "tick_interval", "interval": 10 },
				                  "if_true": [
				                    {
				                      "type": "fire_danmaku",
				                      "bullet": "circle",
				                      "color": "purple",
				                      "count": 8,
				                      "speed": 0.35,
				                      "lifetime": 70,
				                      "pattern": "ring",
				                      "aim_mode": "direction_to_target"
				                    }
				                  ]
				                }
				              ]
				            }
				          ]
				        }
				      ]
				    }
				  },
				  "custom_names": {
				    "tick/0": "youkaishomecoming.spell_template.shooter.node.interval",
				    "tick/0:true/0": "youkaishomecoming.spell_template.shooter.node.spawn_shooter",
				    "tick/0:true/0:body/0": "youkaishomecoming.spell_template.shooter.node.shooter_tick",
				    "tick/0:true/0:body/0:true/0": "youkaishomecoming.spell_template.shooter.node.shooter_fire"
				  }
				}
				""");
		TEMPLATES.put("command", """
				{
				  "id": "%1$s",
				  "display": {
				    "name": "youkaishomecoming.spell_template.command.name",
				    "description": "youkaishomecoming.spell_template.command.desc"
				  },
				  "entry_phase": "%2$s",
				  "phases": {
				    "%2$s": {
				      "id": "%2$s",
				      "on_enter": [
				        {
				          "type": "run_command",
				          "mode": "as_caster",
				          "command": "particle minecraft:end_rod ~ ~1 ~ 0.2 0.2 0.2 0.01 16"
				        }
				      ],
				      "on_tick": [
				        {
				          "type": "conditional",
				          "condition": { "type": "tick_interval", "interval": 20 },
				          "if_true": [
				            {
				              "type": "fire_danmaku",
				              "bullet": "star",
				              "color": "light_blue",
				              "count": 16,
				              "speed": 0.4,
				              "lifetime": 60,
				              "pattern": "ring",
				              "aim_mode": "direction_to_target"
				            }
				          ]
				        }
				      ]
				    }
				  },
				  "custom_names": {
				    "enter/0": "youkaishomecoming.spell_template.command.node.run_command",
				    "tick/0": "youkaishomecoming.spell_template.command.node.interval",
				    "tick/0:true/0": "youkaishomecoming.spell_template.command.node.fire_ring"
				  }
				}
				""");
	}

	private SpellTemplates() {
	}

	public static Set<String> names() {
		return TEMPLATES.keySet();
	}

	public static boolean contains(String name) {
		return TEMPLATES.containsKey(name);
	}

	public static ResourceLocation defaultId(String name) {
		return YoukaisHomecoming.loc("template_" + name);
	}

	public static SpellDefinition empty(ResourceLocation spellId) {
		ResourceLocation phaseId = mainPhaseId(spellId);
		var phase = new PhaseDefinition(phaseId, List.of(), List.of(), List.of(), List.of(), List.of());
		return new SpellDefinition(
				spellId,
				new SpellDisplay(spellId.getPath(), "", java.util.Optional.empty(), java.util.Optional.empty()),
				SpellItemForm.NONE,
				phaseId,
				Map.of(phaseId, phase),
				DifficultyProfile.DEFAULT
		);
	}

	public static SpellDefinition create(ResourceLocation spellId, String templateName) {
		String template = TEMPLATES.get(templateName);
		if (template == null) {
			throw new IllegalArgumentException("Unknown spell template: " + templateName);
		}
		ResourceLocation phaseId = mainPhaseId(spellId);
		String json = template.formatted(spellId, phaseId);
		return SpellDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
				.getOrThrow(false, err -> {
				});
	}

	private static ResourceLocation mainPhaseId(ResourceLocation spellId) {
		return new ResourceLocation(spellId.getNamespace(), spellId.getPath() + "/main");
	}
}
