package dev.xkmc.fastprojectileapi.spellcircle;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import dev.xkmc.youkaishomecoming.util.GlyphRuns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SerialClass
public class SpellComponent {

	@Nullable
	public static SpellComponent getFromConfig(String s) {
		return YoukaisHomecoming.SPELL.getMerged().map.get(s);
	}

	@SerialClass.SerialField
	public ArrayList<Stroke> strokes = new ArrayList<>();

	@SerialClass.SerialField
	public ArrayList<ItemLayer> items = new ArrayList<>();

	@SerialClass.SerialField
	public ArrayList<TextLayer> texts = new ArrayList<>();

	@SerialClass.SerialField
	public ArrayList<Layer> layers = new ArrayList<>();

	/** Optional live-resource slot layout used by the player STG circle renderer. */
	@Nullable
	@SerialClass.SerialField
	public ResourceLayout resource_layout;

	@OnlyIn(Dist.CLIENT)
	public void render(RenderHandle handle) {
		handle.matrix.pushPose();
		for (Stroke stroke : strokes) {
			stroke.render(handle);
		}
		// Item sprites and text glyphs draw with their own render types, which ends the
		// batch holding the circle's vertex consumer. Re-acquire before any further
		// stroke drawing (nested layers, child components, the progress ring).
		if (!items.isEmpty()) {
			for (ItemLayer item : items) {
				item.render(handle);
			}
			handle.reacquireBuilder();
		}
		if (!texts.isEmpty()) {
			for (TextLayer text : texts) {
				text.render(handle);
			}
			handle.reacquireBuilder();
		}
		for (Layer layer : layers) {
			layer.render(handle);
		}
		handle.matrix.popPose();
	}

	public void invalidateCache() {
		if (strokes == null) {
			strokes = new ArrayList<>();
		}
		if (items == null) {
			items = new ArrayList<>();
		}
		// Circles authored before text layers existed have no "texts" key at all.
		if (texts == null) {
			texts = new ArrayList<>();
		}
		if (layers == null) {
			layers = new ArrayList<>();
		}
		for (Layer layer : layers) {
			layer.invalidateCache();
		}
		for (ItemLayer item : items) {
			item.invalidateCache();
		}
	}

	/**
	 * Shared colour-string parser for strokes and text layers.
	 * Accepts {@code #RRGGBB}, {@code 0xAARRGGBB} and bare hex; falls back to
	 * opaque white on malformed input rather than throwing inside the render loop.
	 */
	@OnlyIn(Dist.CLIENT)
	static int parseColor(@Nullable String color) {
		if (color == null) return -1;
		String str = color.trim();
		if (str.startsWith("#")) {
			str = str.substring(1);
		}
		if (str.startsWith("0x") || str.startsWith("0X")) {
			str = str.substring(2);
		}
		if (str.isEmpty()) return -1;
		try {
			if (str.length() <= 6) {
				return 0xff000000 | Integer.parseUnsignedInt(str, 16);
			}
			return Integer.parseUnsignedInt(str, 16);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** Scale a packed ARGB colour's alpha channel by {@code alpha}. */
	@OnlyIn(Dist.CLIENT)
	private static int withAlpha(int color, float alpha) {
		int a = (int) ((color >>> 24) * Math.max(0, Math.min(1, alpha)));
		return a << 24 | color & 0x00ffffff;
	}

	@SerialClass
	public static class Value {

		@SerialClass.SerialField
		public float value, delta, amplitude, period, dt;

		@OnlyIn(Dist.CLIENT)
		public float get(float tick) {
			float ans = value + delta * tick;
			if (period > 0) ans += amplitude * (float) Math.sin((tick - dt) * 2 * Math.PI / period);
			return ans;
		}

	}

	@SerialClass
	public static class ResourceLayout {

		/** Slot plane: {@code xy} for the circle plane, {@code xz} for the legacy Bomb orbit. */
		@SerialClass.SerialField
		public String plane = "xy";

		/** Animated orbit radius and group angle, in pixels and degrees respectively. */
		@Nullable
		@SerialClass.SerialField
		public Value radius, angle;

		/** Angular span occupied by the slots. 360 distributes them over a closed ring. */
		@SerialClass.SerialField
		public float arc = 360;

		/** Keep child circles facing the parent instead of rotating with their orbit. */
		@SerialClass.SerialField
		public boolean counter_rotate = true;

		@OnlyIn(Dist.CLIENT)
		public float radius(float tick, float fallback) {
			return radius == null ? fallback : radius.get(tick);
		}

		@OnlyIn(Dist.CLIENT)
		public float angle(float tick, float fallback) {
			return angle == null ? fallback : angle.get(tick);
		}
	}

	@SerialClass
	public static class Stroke {

		@SerialClass.SerialField
		public int vertex, cycle = 1, rune = 0;

		@SerialClass.SerialField
		public String color;

		@SerialClass.SerialField
		public float width, radius, z, angle;

		@OnlyIn(Dist.CLIENT)
		public void render(RenderHandle handle) {
			float da = (float) Math.PI * 2 * cycle / vertex;
			float a = angle;
			float w = width / (float) Math.cos(da / 2);
			int col = getColor();
			float dv = (rune > 0 ? 8 : 1) / 128f;
			float du = (int) (Math.PI * 2 * radius * cycle / width * 8) / 8f / vertex * dv;
			for (int i = 0; i < vertex; i++) {
				rect(handle, a + da * i, da, radius, w, z, col, i * du, rune == 0 ? 0 : (rune - 1) * dv, du, dv);
			}

		}

		/** Render only the leading fraction of this circular stroke. */
		@OnlyIn(Dist.CLIENT)
		public void renderProgress(RenderHandle handle, float progress) {
			if (vertex <= 0 || progress <= 0 || width <= 0 || radius <= 0) return;
			float da = (float) Math.PI * 2 * cycle / vertex;
			float a = angle;
			float w = width / (float) Math.cos(da / 2);
			int count = Math.min(vertex, Math.max(1, (int) Math.ceil(vertex * Math.min(1, progress))));
			int col = getColor();
			float dv = (rune > 0 ? 8 : 1) / 128f;
			float du = (int) (Math.PI * 2 * radius * cycle / width * 8) / 8f / vertex * dv;
			for (int i = 0; i < count; i++) {
				rect(handle, a + da * i, da, radius, w, z, col, i * du, rune == 0 ? 0 : (rune - 1) * dv, du, dv);
			}
		}

		/** Render a progress arc between normalized angles, without closing the ring. */
		@OnlyIn(Dist.CLIENT)
		public void renderProgressRange(RenderHandle handle, float start, float end) {
			if (vertex <= 0 || end <= start || width <= 0 || radius <= 0) return;
			start = Math.max(0, Math.min(1, start));
			end = Math.max(start, Math.min(1, end));
			int first = Math.max(0, (int) Math.floor(vertex * start));
			int last = Math.min(vertex, Math.max(first + 1, (int) Math.ceil(vertex * end)));
			int col = getColor();
			float dv = (rune > 0 ? 8 : 1) / 128f;
			float du = (int) (Math.PI * 2 * radius * cycle / width * 8) / 8f / vertex * dv;
			float direction = cycle < 0 ? -1 : 1;
			for (int i = first; i < last; i++) {
				float left = Math.max(start, i / (float) vertex);
				float right = Math.min(end, (i + 1) / (float) vertex);
				if (right <= left) continue;
				float a = angle + (float) (Math.PI * 2) * direction * left;
				float segmentDa = (float) (Math.PI * 2) * direction * (right - left);
				float segmentWidth = width / (float) Math.cos(segmentDa / 2);
				rect(handle, a, segmentDa, radius, segmentWidth, z, col, i * du,
						rune == 0 ? 0 : (rune - 1) * dv, du, dv);
			}
		}

		@OnlyIn(Dist.CLIENT)
		private int getColor() {
			return parseColor(color);
		}

		@OnlyIn(Dist.CLIENT)
		private static void rect(RenderHandle handle, float a, float da, float r, float w, float z, int col, float u, float v, float du, float dv) {
			float inner = r - w / 2;
			float outer = r + w / 2;
			vertex(handle, a, inner, z, col, u, v);
			vertex(handle, a, outer, z, col, u, v + dv);
			vertex(handle, a + da, outer, z, col, u + du, v + dv);
			vertex(handle, a + da, inner, z, col, u + du, v);
		}

		@OnlyIn(Dist.CLIENT)
		private static void vertex(RenderHandle handle, float a, float r, float z, int col, float u, float v) {
			int alp = (int) ((col >> 24 & 0xff) * handle.alpha);
			handle.builder.vertex(handle.matrix.last().pose(),
							r * (float) Math.cos(a),
							r * (float) Math.sin(a),
							z).color(
							col >> 16 & 0xff,
							col >> 8 & 0xff,
							col & 0xff,
							alp).uv(u, v)
					.endVertex();
		}

	}

	@SerialClass
	public static class Layer {

		@SerialClass.SerialField
		public ArrayList<String> children = new ArrayList<>();

		private transient List<SpellComponent> _children;

		@Nullable
		@SerialClass.SerialField
		public Value z_offset, scale, radius, rotation, alpha;

		@OnlyIn(Dist.CLIENT)
		public void render(RenderHandle handle) {
			if (children == null || children.isEmpty()) {
				return;
			}
			if (_children == null) {
				_children = children.stream().map(SpellComponent::getFromConfig).collect(Collectors.toList());
			}
			int n = _children.size();
			float z = get(z_offset, handle, 0);
			float s = get(scale, handle, 1);
			float a = get(rotation, handle, 0);
			double r = get(radius, handle, 0);
			float al = handle.alpha;
			if (alpha != null) {
				handle.alpha *= alpha.get(handle.tick);
			}
			handle.matrix.pushPose();
			handle.matrix.translate(0, 0, z);
			handle.matrix.scale(s, s, s);
			for (SpellComponent child : _children) {
				if (child == null) {
					a += 360f / n;
					continue;
				}
				handle.matrix.pushPose();
				handle.matrix.mulPose(Axis.ZP.rotationDegrees(a));
				handle.matrix.translate(r, 0, 0);
				child.render(handle);
				handle.matrix.popPose();
				a += 360f / n;
			}
			handle.matrix.popPose();
			handle.alpha = al;
		}

		public void invalidateCache() {
			if (children == null) {
				children = new ArrayList<>();
			}
			_children = null;
		}

		@OnlyIn(Dist.CLIENT)
		private float get(@Nullable Value val, RenderHandle handle, float def) {
			return val == null ? def : val.get(handle.tick);
		}


	}

	@SerialClass
	public static class ItemLayer {

		@SerialClass.SerialField
		public String item = "minecraft:air";

		@Nullable
		@SerialClass.SerialField
		public Value x_offset, y_offset, z_offset, scale, rotation, alpha;

		@Nullable
		private transient String _item;

		private transient ItemStack _stack = ItemStack.EMPTY;

		@OnlyIn(Dist.CLIENT)
		public void render(RenderHandle handle) {
			ItemStack stack = getStack();
			if (stack.isEmpty()) {
				return;
			}
			float x = get(x_offset, handle, 0);
			float y = get(y_offset, handle, 0);
			float z = get(z_offset, handle, 0);
			float s = get(scale, handle, 16);
			if (s <= 0) {
				return;
			}
			float a = handle.alpha * get(alpha, handle, 1);
			if (a <= 0) {
				return;
			}
			handle.matrix.pushPose();
			handle.matrix.translate(x, y, z);
			handle.matrix.mulPose(Axis.ZP.rotationDegrees(get(rotation, handle, 0)));
			handle.matrix.scale(s, s, s);
			Minecraft mc = Minecraft.getInstance();
			BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
			renderFlatSprite(handle, model.getParticleIcon(), Math.min(1.0f, a));
			handle.matrix.popPose();
		}

		@OnlyIn(Dist.CLIENT)
		private static void renderFlatSprite(RenderHandle handle, TextureAtlasSprite sprite, float alpha) {
			VertexConsumer builder = handle.buffer.getBuffer(RenderType.entityTranslucent(sprite.atlasLocation()));
			float u0 = sprite.getU0();
			float u1 = sprite.getU1();
			float v0 = sprite.getV0();
			float v1 = sprite.getV1();
			vertex(handle, builder, -0.5f, -0.5f, u0, v1, alpha);
			vertex(handle, builder, 0.5f, -0.5f, u1, v1, alpha);
			vertex(handle, builder, 0.5f, 0.5f, u1, v0, alpha);
			vertex(handle, builder, -0.5f, 0.5f, u0, v0, alpha);
		}

		@OnlyIn(Dist.CLIENT)
		private static void vertex(RenderHandle handle, VertexConsumer builder, float x, float y, float u, float v, float alpha) {
			int a = (int) (255 * alpha);
			PoseStack.Pose pose = handle.matrix.last();
			builder.vertex(pose.pose(), x, y, 0)
					.color(255, 255, 255, a)
					.uv(u, v)
					.overlayCoords(OverlayTexture.NO_OVERLAY)
					.uv2(handle.light)
					.normal(pose.normal(), 0, 0, 1)
					.endVertex();
		}

		@OnlyIn(Dist.CLIENT)
		private ItemStack getStack() {
			if (!Objects.equals(_item, item)) {
				_item = item;
				ResourceLocation id = ResourceLocation.tryParse(item);
				_stack = id == null ? ItemStack.EMPTY :
						BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
			}
			return _stack;
		}

		public void invalidateCache() {
			_item = null;
			_stack = ItemStack.EMPTY;
		}

		@OnlyIn(Dist.CLIENT)
		private float get(@Nullable Value val, RenderHandle handle, float def) {
			return val == null ? def : val.get(handle.tick);
		}

	}


	/**
	 * A run of text drawn into the circle, either straight along local +X or
	 * wrapped onto a ring. Mirrors {@link ItemLayer}'s animated {@link Value}
	 * fields so text can drift, spin and fade like every other element.
	 *
	 * <p>Glyphs are placed one slot at a time (same approach as the text danmaku
	 * renderer) so per-character spacing and arc placement stay under our control.
	 */
	@SerialClass
	public static class TextLayer {

		@SerialClass.SerialField
		public String text = "";

		@SerialClass.SerialField
		public String color;

		/** Extra advance inserted after every glyph, in circle units. */
		@SerialClass.SerialField
		public float char_spacing;

		/** 0 = straight run; greater than 0 = wrap the run onto a ring of this radius. */
		@SerialClass.SerialField
		public float radius;

		/** Ring mode only: total sweep in degrees. 0 = derive it from the run's own width. */
		@SerialClass.SerialField
		public float arc_span;

		/**
		 * Ring mode only. Default reads clockwise with glyph tops pointing outward
		 * (readable from outside the circle); flipped reads counter-clockwise with
		 * tops pointing inward.
		 */
		@SerialClass.SerialField
		public boolean flip;

		@Nullable
		@SerialClass.SerialField
		public Value x_offset, y_offset, z_offset, scale, rotation, alpha;

		@OnlyIn(Dist.CLIENT)
		public void render(RenderHandle handle) {
			if (text == null || text.isEmpty()) {
				return;
			}
			float s = get(scale, handle, 1);
			if (s <= 0) {
				return;
			}
			float a = handle.alpha * get(alpha, handle, 1);
			int col = withAlpha(parseColor(color), a);
			if ((col >>> 24) == 0) {
				return;
			}
			Font font = Minecraft.getInstance().font;
			String[] glyphs = GlyphRuns.split(text);
			if (glyphs.length == 0) {
				return;
			}
			handle.matrix.pushPose();
			handle.matrix.translate(get(x_offset, handle, 0), get(y_offset, handle, 0), get(z_offset, handle, 0));
			handle.matrix.mulPose(Axis.ZP.rotationDegrees(get(rotation, handle, 0)));
			if (radius > 0) {
				renderArc(handle, font, glyphs, s, col);
			} else {
				renderStraight(handle, font, glyphs, s, col);
			}
			handle.matrix.popPose();
		}

		@OnlyIn(Dist.CLIENT)
		private void renderStraight(RenderHandle handle, Font font, String[] glyphs, float s, int col) {
			float total = totalAdvance(font, glyphs, s);
			float x = -total / 2;
			for (String glyph : glyphs) {
				float advance = advance(font, glyph, s);
				handle.matrix.pushPose();
				handle.matrix.translate(x + advance / 2, 0, 0);
				drawGlyph(handle, font, glyph, s, col);
				handle.matrix.popPose();
				x += advance;
			}
		}

		@OnlyIn(Dist.CLIENT)
		private void renderArc(RenderHandle handle, Font font, String[] glyphs, float s, int col) {
			float total = totalAdvance(font, glyphs, s);
			if (total <= 0) {
				return;
			}
			// Auto span keeps the arc length equal to the straight run's width.
			float span = arc_span > 0 ? (float) Math.toRadians(arc_span) : total / radius;
			// Glyph "right" points clockwise in the unflipped frame, so angles must decrease.
			float direction = flip ? 1 : -1;
			float start = -direction * span / 2;
			float quarter = flip ? 90 : -90;
			float travelled = 0;
			for (String glyph : glyphs) {
				float advance = advance(font, glyph, s);
				float centre = (travelled + advance / 2) / total;
				handle.matrix.pushPose();
				handle.matrix.mulPose(Axis.ZP.rotation(start + direction * span * centre));
				handle.matrix.translate(radius, 0, 0);
				handle.matrix.mulPose(Axis.ZP.rotationDegrees(quarter));
				drawGlyph(handle, font, glyph, s, col);
				handle.matrix.popPose();
				travelled += advance;
			}
		}

		/**
		 * Draw one glyph centred on the current origin, front and back.
		 *
		 * <p>The negative Y scale converts the font's downward Y into the circle's upward
		 * Y; X keeps its sign, so the glyph is flipped, not mirrored.
		 *
		 * <p>Unlike the circle's own render type, {@code RenderType.text} has back-face
		 * culling enabled, so a single quad vanishes as soon as the circle is seen from
		 * behind. Draw the mirrored back face too — the same thing the text danmaku
		 * renderer does for its sign faces.
		 */
		@OnlyIn(Dist.CLIENT)
		private static void drawGlyph(RenderHandle handle, Font font, String glyph, float s, int col) {
			handle.matrix.pushPose();
			handle.matrix.scale(s, -s, s);
			font.drawInBatch(glyph, -font.width(glyph) / 2f, -font.lineHeight / 2f, col, false,
					handle.matrix.last().pose(), handle.buffer, Font.DisplayMode.NORMAL, 0, handle.light);
			handle.matrix.mulPose(Axis.YP.rotationDegrees(180));
			font.drawInBatch(glyph, -font.width(glyph) / 2f, -font.lineHeight / 2f, col, false,
					handle.matrix.last().pose(), handle.buffer, Font.DisplayMode.NORMAL, 0, handle.light);
			handle.matrix.popPose();
		}

		@OnlyIn(Dist.CLIENT)
		private float totalAdvance(Font font, String[] glyphs, float s) {
			float total = 0;
			for (String glyph : glyphs) {
				total += advance(font, glyph, s);
			}
			return total;
		}

		@OnlyIn(Dist.CLIENT)
		private float advance(Font font, String glyph, float s) {
			return font.width(glyph) * s + char_spacing;
		}

		@OnlyIn(Dist.CLIENT)
		private float get(@Nullable Value val, RenderHandle handle, float def) {
			return val == null ? def : val.get(handle.tick);
		}

	}

	@OnlyIn(Dist.CLIENT)
	public static class RenderHandle {

		public final PoseStack matrix;
		public final MultiBufferSource buffer;
		private final RenderType type;
		/**
		 * The circle's own vertex consumer. Not final: it must be re-acquired after
		 * anything draws with a different render type — see {@link #reacquireBuilder()}.
		 */
		public VertexConsumer builder;
		public final float tick;
		public final int light;

		public float alpha = 1;

		public RenderHandle(PoseStack matrix, MultiBufferSource buffer, RenderType type, float tick, int light) {
			this.matrix = matrix;
			this.buffer = buffer;
			this.type = type;
			this.builder = buffer.getBuffer(type);
			this.tick = tick;
			this.light = light;
		}

		/**
		 * Re-acquire the circle's vertex consumer.
		 *
		 * <p>{@link MultiBufferSource.BufferSource#getBuffer} ends the current batch when
		 * the render type changes. So as soon as an item sprite or a text glyph draws,
		 * the consumer held here has been flushed and is no longer building; writing to
		 * it again corrupts or kills the rest of the circle — nested layers, child
		 * component strokes, the progress ring. Call this after any such element.
		 */
		public void reacquireBuilder() {
			this.builder = buffer.getBuffer(type);
		}
	}

}
