package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Submit a background generation request; results are delivered through chat and clipboard. */
@OnlyIn(Dist.CLIENT)
public final class SpellAiPromptScreen extends Screen {
	private final SpellPreviewScreen parent;
	private final EditBox prompt;
	private String operation;
	private SpellCardType cardType;

	public SpellAiPromptScreen(SpellPreviewScreen parent) {
		this(parent, "modify");
	}

	SpellAiPromptScreen(SpellPreviewScreen parent, String operation) {
		super(Component.literal(SpellEditorLocalization.t("AI Spell")));
		this.parent = parent;
		this.operation = operation;
		this.cardType = parent.currentCardType();
		this.prompt = new EditBox(Minecraft.getInstance().font, 0, 0, 300, 20,
				Component.literal(SpellEditorLocalization.t("Custom requirement")));
		this.prompt.setMaxLength(4000);
	}

	@Override
	protected void init() {
		int left = width / 2 - 190;
		int top = height / 2 - 80;
		prompt.setX(left);
		prompt.setY(top + 42);
		prompt.setWidth(380);
		addRenderableWidget(prompt);
		addRenderableWidget(Button.builder(operationLabel(), button -> {
			operation = operation.equals("modify") ? "create" : "modify";
			button.setMessage(operationLabel());
		}).bounds(left, top + 70, 120, 20).build());
		addRenderableWidget(Button.builder(cardTypeLabel(), button -> {
			SpellCardType[] values = SpellCardType.values();
			cardType = values[(cardType.ordinal() + 1) % values.length];
			button.setMessage(cardTypeLabel());
		}).bounds(left + 130, top + 70, 120, 20).build());
		addRenderableWidget(Button.builder(Component.literal(SpellEditorLocalization.t("Generate draft")), button -> submit())
				.bounds(left + 260, top + 70, 120, 20).build());
		addRenderableWidget(Button.builder(Component.literal(SpellEditorLocalization.t("Cancel")), button -> onClose())
				.bounds(left, top + 98, 120, 20).build());
		setInitialFocus(prompt);
	}

	private void submit() {
		if (prompt.getValue().isBlank()) return;
		SpellEditorNetworkClient.requestAi(parent, prompt.getValue(), operation, cardType);
		var mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.displayClientMessage(Component.translatable(
				"youkaishomecoming.spell_editor.message.ai_requested"), false);
		onClose();
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}

	private Component operationLabel() {
		return Component.literal(SpellEditorLocalization.t(operation.equals("modify") ? "Modify current" : "Create new"));
	}

	private Component cardTypeLabel() {
		return Component.literal(SpellEditorLocalization.t("Type: " + cardType.getSerializedName()));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		int left = width / 2 - 190;
		int top = height / 2 - 80;
		graphics.drawCenteredString(font, title, width / 2, top - 4, 0xFFFFFF);
		graphics.drawString(font, SpellEditorLocalization.t("Describe the spell or requested changes:"), left, top + 28, 0xCCCCCC);
		graphics.drawWordWrap(font, Component.translatable("youkaishomecoming.spell_editor.ai.background_hint"), left, top + 128, 380, 0xAAAAAA);
		super.render(graphics, mouseX, mouseY, partialTick);
	}
}
