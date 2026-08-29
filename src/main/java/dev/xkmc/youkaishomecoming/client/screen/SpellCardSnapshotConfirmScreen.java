package dev.xkmc.youkaishomecoming.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import dev.xkmc.youkaishomecoming.content.spell.preview.SpellPreviewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;

/**
 * 认证与导出前的「符卡卡面快照确认弹窗」。
 * 展示 84x128 最终卡面立绘，供玩家确认构图与弹幕形态。
 * 确认后快照被锁定并传递给后续流程（认证或导出）。
 */
@OnlyIn(Dist.CLIENT)
public class SpellCardSnapshotConfirmScreen extends Screen {

	private final Screen parentScreen;
	private final byte[] snapshotPng;
	private final Runnable onConfirm;

	@Nullable
	private DynamicTexture texture;
	@Nullable
	private ResourceLocation textureLocation;

	public SpellCardSnapshotConfirmScreen(Screen parentScreen, byte[] snapshotPng, Runnable onConfirm) {
		super(Component.translatable("youkaishomecoming.spell.snapshot_confirm.title"));
		this.parentScreen = parentScreen;
		this.snapshotPng = snapshotPng;
		this.onConfirm = onConfirm;
	}

	@Override
	protected void init() {
		if (snapshotPng != null && snapshotPng.length > 0 && texture == null) {
			try {
				NativeImage img = NativeImage.read(new ByteArrayInputStream(snapshotPng));
				texture = new DynamicTexture(img);
				textureLocation = Minecraft.getInstance().getTextureManager().register("spell_card_confirm_preview", texture);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		int cx = this.width / 2;
		int cardW = 84 * 2;
		int cardH = 128 * 2;
		int topY = (this.height - cardH) / 2;

		// 确认与返回按钮 (位于卡牌右侧或下方)
		int btnX = cx + 20;
		int btnY = topY + cardH - 50;

		addRenderableWidget(Button.builder(Component.translatable("youkaishomecoming.spell.snapshot_confirm.confirm"), b -> {
			onClose();
			onConfirm.run();
		}).bounds(btnX, btnY, 140, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("youkaishomecoming.spell.snapshot_confirm.retake"), b -> {
			onClose();
		}).bounds(btnX, btnY + 24, 140, 20).build());
	}

	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
		renderBackground(gui);

		int cx = this.width / 2;
		int cardW = 84 * 2;
		int cardH = 128 * 2;
		int topY = (this.height - cardH) / 2;
		int cardX = cx - cardW - 10;

		// 绘制大尺寸卡面预览
		if (textureLocation != null) {
			gui.blit(textureLocation, cardX, topY, 0, 0, cardW, cardH, cardW, cardH);
		} else {
			gui.fill(cardX, topY, cardX + cardW, topY + cardH, 0xFF222222);
		}

		// 右侧说明文案
		int textX = cx + 20;
		int textY = topY + 20;
		gui.drawString(this.font, Component.translatable("youkaishomecoming.spell.snapshot_confirm.heading"), textX, textY, 0xFFFFD700, true);
		textY += 16;
		gui.drawString(this.font, Component.translatable("youkaishomecoming.spell.snapshot_confirm.desc1"), textX, textY, 0xFFE0E0E0, false);
		textY += 12;
		gui.drawString(this.font, Component.translatable("youkaishomecoming.spell.snapshot_confirm.desc2"), textX, textY, 0xFFA0A0A0, false);
		textY += 12;
		gui.drawString(this.font, Component.translatable("youkaishomecoming.spell.snapshot_confirm.desc3"), textX, textY, 0xFFA0A0A0, false);

		super.render(gui, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		if (texture != null) {
			texture.close();
			texture = null;
			textureLocation = null;
		}
		if (parentScreen instanceof SpellPreviewScreen previewScreen) {
			previewScreen.getViewport().setCardFrameGuideActive(true);
		}
		if (minecraft != null) {
			minecraft.setScreen(parentScreen);
		}
	}
}
