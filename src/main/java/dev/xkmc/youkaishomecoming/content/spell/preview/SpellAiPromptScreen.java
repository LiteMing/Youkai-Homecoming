package dev.xkmc.youkaishomecoming.content.spell.preview;

import dev.xkmc.youkaishomecoming.content.spell.definition.SpellCardType;
import dev.xkmc.youkaishomecoming.init.data.YHModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Small editor modal for an LLM-generated spell draft. */
@OnlyIn(Dist.CLIENT)
public final class SpellAiPromptScreen extends Screen {
	private final SpellPreviewScreen parent;
	private final EditBox prompt;
	private String operation = "modify";
	private SpellCardType cardType;
	private Button operationButton;
	private Button cardTypeButton;
	private Button submitButton;
	private boolean waiting;
	private String status = "";

	public SpellAiPromptScreen(SpellPreviewScreen parent) {
		super(Component.literal(SpellEditorLocalization.t("AI Spell")));
		this.parent = parent;
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
		operationButton = addRenderableWidget(Button.builder(operationLabel(), button -> {
			operation = operation.equals("modify") ? "create" : "modify";
			button.setMessage(operationLabel());
		}).bounds(left, top + 70, 120, 20).build());
		cardTypeButton = addRenderableWidget(Button.builder(cardTypeLabel(), button -> {
			SpellCardType[] values = SpellCardType.values();
			cardType = values[(cardType.ordinal() + 1) % values.length];
			button.setMessage(cardTypeLabel());
		}).bounds(left + 130, top + 70, 120, 20).build());
		submitButton = addRenderableWidget(Button.builder(Component.literal(SpellEditorLocalization.t("Generate draft")), button -> submit())
				.bounds(left + 260, top + 70, 120, 20).build());
		addRenderableWidget(Button.builder(Component.literal(SpellEditorLocalization.t("Cancel")), button -> onClose())
				.bounds(left, top + 98, 120, 20).build());
		setInitialFocus(prompt);
	}

	private void submit() {
		if (waiting || prompt.getValue().isBlank()) return;
		waiting = true;
		status = SpellEditorLocalization.t("Sending request...");
		submitButton.active = false;
		SpellEditorNetworkClient.requestAi(parent, prompt.getValue(), operation, cardType);
	}

	void complete(SpellAiGenerateResultToClient result) {
		waiting = false;
		if (result.success) {
			if (parent.applyAiGenerationResult(result.json)) {
				Minecraft.getInstance().setScreen(parent);
				return;
			}
			status = SpellEditorLocalization.t("Server returned invalid spell JSON");
		} else {
			status = result.message == null || result.message.isBlank()
					? SpellEditorLocalization.t("LLM request failed") : result.message;
		}
		if (submitButton != null) submitButton.active = true;
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
		graphics.drawString(font, SpellEditorLocalization.t("Server certification and OP/EXP policy still apply."), left, top + 128, 0xAAAAAA);
		if (!YHModConfig.COMMON.spellAiHeaderPrompt.get().isBlank()) {
			graphics.drawString(font, SpellEditorLocalization.t("Configured server header prompt is active."), left, top + 140, 0x88CC88);
		}
		if (!status.isBlank()) graphics.drawString(font, status, left, top + 155, 0xFFCC66);
		super.render(graphics, mouseX, mouseY, partialTick);
	}
}
