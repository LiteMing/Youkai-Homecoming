package dev.xkmc.youkaishomecoming.content.spell.preview.dock;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

import static dev.xkmc.youkaishomecoming.content.spell.preview.YsmEditorController.text;

public final class YsmPreviewDockPanel implements DockPanel {
	private final YsmEditorController editor;
	private int x, y, w, h;
	private float yaw = 180, pitch, zoom = 1;
	private float panX, panY;
	private boolean faceView;
	private boolean dragging;
	private final List<Button> buttons = new ArrayList<>();
	public YsmPreviewDockPanel(YsmEditorController editor) { this.editor = editor; }
	@Override public String dockId() { return "ysm_preview"; }
	@Override public String dockTitle() { return text("preview").getString(); }
	@Override public int getX() { return x; }
	@Override public int getY() { return y; }
	@Override public int getWidth() { return w; }
	@Override public int getHeight() { return h; }
	@Override public void setBounds(int x, int y, int w, int h) {
		this.x = x; this.y = y; this.w = w; this.h = h;
		buttons.clear();
		add("preview_play", editor::previewDraft);
		add("preview_stop", editor::stopPreview);
		add("preview_pause", editor::togglePause);
		add("preview_reset", editor::resetPreview);
		add("preview_face", () -> { faceView = !faceView; zoom = 1; panX = panY = pitch = 0; yaw = 180; });
		add("save_preview", editor::savePreview);
	}
	private void add(String key, Runnable action) {
		int index = buttons.size(), width = Math.max(18, (w - 16) / 2);
		buttons.add(Button.builder(text(key), ignored -> editor.attempt(action))
				.bounds(x + 6 + index % 2 * (width + 2), y + 6 + index / 2 * 22, width, 20).build());
	}
	@Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(x, y, x + w, y + h, 0xff121b23);
		if (w < 30 || h < 80) return;
		graphics.enableScissor(x, y, x + w, y + h);
		buttons.get(4).setMessage(text(faceView ? "preview_full" : "preview_face"));
		buttons.get(5).active = editor.profile() != null && editor.mayWriteWorld() && !editor.waiting();
		buttons.forEach(button -> button.render(graphics, mouseX, mouseY, partialTick));
		var font = Minecraft.getInstance().font;
		int footer = Math.max(y + 78, y + h - 70);
		int size = Math.max(0, footer - y - 80);
		var holder = editor.preview();
		if (holder != null && !editor.model().isEmpty() && size > 30) {
			int scale = Math.max(1, (int) (Math.min(w * .36, size * .43) * zoom * (faceView ? 2 : 1)));
			renderModel(graphics, holder.getFakeCaster(), x + 1, y + 74, w - 2, footer - y - 74, scale, yaw, pitch,
					faceView, panX, panY);
		}
		int lineY = footer + 3;
		for (Component line : List.of(text("preview_state", text("trigger." + editor.previewState().id()), editor.paused() ? "||" : ">"), text("preview_gestures"), editor.status())) {
			for (var part : font.split(line, Math.max(20, w - 14))) {
				if (lineY < y + h - 10) graphics.drawString(font, part, x + 7, lineY, 0xffc6d4df, false);
				lineY += 11;
			}
		}
		graphics.disableScissor();
	}

	/** Shared by the main viewport and the model picker's isolated hover preview. */
	static void renderModel(GuiGraphics graphics, LivingEntity entity, int left, int top, int width, int height,
			int scale, float yaw, float pitch) {
		renderModel(graphics, entity, left, top, width, height, scale, yaw, pitch, false, 0, 0);
	}
	private static void renderModel(GuiGraphics graphics, LivingEntity entity, int left, int top, int width, int height,
			int scale, float yaw, float pitch, boolean face, float panX, float panY) {
		entity.yBodyRotO = entity.yBodyRot = yaw;
		entity.yHeadRotO = entity.yHeadRot = yaw;
		entity.setYRot(yaw);
		graphics.enableScissor(left, top, left + width, top + height);
		Quaternionf tilt = new Quaternionf().rotationX((float) Math.toRadians(pitch));
		// The inventory helper uses z=50. A tilted/scaled model can cross the GUI
		// background's z=0 plane. Clear depth only inside this viewport, retaining
		// its color; clear again afterwards so the model cannot occlude later UI.
		graphics.flush();
		boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		var camera = new Quaternionf(dispatcher.cameraOrientation());
		try {
			RenderSystem.depthMask(true);
			RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
			int centerX = left + width / 2 + Math.round(panX);
			int originY = top + (face ? height / 2 + Math.round(entity.getEyeHeight() * scale) : height - 8) + Math.round(panY);
			InventoryScreen.renderEntityInInventory(graphics, centerX, originY, scale,
					new Quaternionf().rotationZ((float) Math.PI).mul(tilt), tilt, entity);
		} finally {
			graphics.flush();
			RenderSystem.depthMask(true);
			RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
			RenderSystem.depthMask(depthWrite);
			if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
			dispatcher.overrideCameraOrientation(camera);
			graphics.disableScissor();
		}
	}
	@Override public boolean mouseClicked(double mx, double my, int button) {
		if (!isMouseOver(mx, my)) return false;
		for (Button control : buttons) if (control.mouseClicked(mx, my, button)) return true;
		dragging = button == 0 || button == 1;
		return true;
	}
	@Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (!dragging) return false;
		if (button == 1) { panX += (float) dx; panY += (float) dy; }
		else { yaw += (float) dx; pitch = Mth.clamp(pitch + (float) dy, -60, 60); }
		return true;
	}
	@Override public boolean mouseReleased(double mx, double my, int button) { boolean wasDragging = dragging; dragging = false; return wasDragging; }
	@Override public boolean mouseScrolled(double mx, double my, double amount) {
		if (!isMouseOver(mx, my)) return false;
		zoom = Mth.clamp(zoom + (float) amount * .1f, .25f, 3f); return true;
	}
	@Override public void onDeactivated() { dragging = false; }
}
