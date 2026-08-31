package dev.xkmc.youkaishomecoming.content.spell.definition;

import net.minecraft.util.StringRepresentable;

/** Server-authoritative category of a spell-card definition. */
public enum SpellCardType implements StringRepresentable {
	NORMAL("normal", true, true),
	LAST_SPELL("last_spell", true, true),
	TIMEOUT_SPELL("timeout_spell", true, true),
	NON_SPELL("non_spell", false, false);

	private final String id;
	private final boolean requiresCertification;
	private final boolean consumesSpellResources;

	SpellCardType(String id, boolean requiresCertification, boolean consumesSpellResources) {
		this.id = id;
		this.requiresCertification = requiresCertification;
		this.consumesSpellResources = consumesSpellResources;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public boolean requiresCertification() {
		return requiresCertification;
	}

	public boolean consumesSpellResources() {
		return consumesSpellResources;
	}

	public boolean isNonSpell() {
		return this == NON_SPELL;
	}

	public static SpellCardType byName(String value) {
		if (value != null) {
			for (SpellCardType type : values()) {
				if (type.id.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) return type;
			}
		}
		return NORMAL;
	}
}
