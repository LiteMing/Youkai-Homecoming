package dev.xkmc.youkaishomecoming.content.spell.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.xkmc.fastprojectileapi.spellcircle.EntitySpellCircleManager;
import dev.xkmc.youkaishomecoming.content.spell.preview.PreviewCardHolder;
import dev.xkmc.youkaishomecoming.content.spell.runtime.SpellContext;
import net.minecraft.resources.ResourceLocation;

public record SetSpellCircleAction(Mode mode, ResourceLocation circle, float size) implements SpellAction {

	public enum Mode {
		SET,
		OFF,
		CLEAR
	}

	public static final Codec<Mode> MODE_CODEC = Codec.STRING.xmap(
			s -> {
				try {
					return Mode.valueOf(s.toUpperCase(java.util.Locale.ROOT));
				} catch (IllegalArgumentException ignored) {
					return Mode.SET;
				}
			},
			mode -> mode.name().toLowerCase(java.util.Locale.ROOT)
	);

	public static final Codec<SetSpellCircleAction> CODEC = RecordCodecBuilder.create(i -> i.group(
			MODE_CODEC.optionalFieldOf("mode", Mode.SET).forGetter(SetSpellCircleAction::mode),
			ResourceLocation.CODEC.optionalFieldOf("circle", new ResourceLocation("youkaishomecoming", "test_spell"))
					.forGetter(SetSpellCircleAction::circle),
			Codec.FLOAT.optionalFieldOf("size", 1.0f).forGetter(SetSpellCircleAction::size)
	).apply(i, SetSpellCircleAction::new));

	public SetSpellCircleAction {
		if (mode == null) {
			mode = Mode.SET;
		}
		if (circle == null) {
			circle = new ResourceLocation("youkaishomecoming", "test_spell");
		}
		if (!Float.isFinite(size)) {
			size = 1.0f;
		}
		size = Math.max(0.0f, Math.min(64.0f, size));
	}

	@Override
	public void execute(SpellContext ctx) {
		if (ctx.holder() instanceof PreviewCardHolder preview) {
			switch (mode) {
				case SET -> preview.setPreviewSpellCircle(circle, size);
				case OFF -> preview.hidePreviewSpellCircle();
				case CLEAR -> preview.clearPreviewSpellCircle();
			}
			return;
		}
		var host = ctx.host();
		if (host == null || ctx.self().level().isClientSide()) {
			return;
		}
		var display = host.spellCircleDisplayEntity();
		var source = host.spellCircleSourceEntity();
		switch (mode) {
			case SET -> EntitySpellCircleManager.setTemporaryOverride(display, source, circle, size);
			case OFF -> EntitySpellCircleManager.setTemporaryHidden(display, source);
			case CLEAR -> EntitySpellCircleManager.clearTemporaryOverride(display, source);
		}
	}

}
