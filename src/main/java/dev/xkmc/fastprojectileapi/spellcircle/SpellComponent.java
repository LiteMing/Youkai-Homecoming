package dev.xkmc.fastprojectileapi.spellcircle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.xkmc.l2serial.serialization.SerialClass;
import dev.xkmc.youkaishomecoming.init.YoukaisHomecoming;
import net.minecraft.client.Minecraft;
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
	public ArrayList<Layer> layers = new ArrayList<>();

	@OnlyIn(Dist.CLIENT)
	public void render(RenderHandle handle) {
		handle.matrix.pushPose();
		for (Stroke stroke : strokes) {
			stroke.render(handle);
		}
		for (ItemLayer item : items) {
			item.render(handle);
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
			for (int i = first; i < last; i++) {
				float left = Math.max(start, i / (float) vertex);
				float right = Math.min(end, (i + 1) / (float) vertex);
				if (right <= left) continue;
				float a = angle + (float) (Math.PI * 2) * left;
				float segmentDa = (float) (Math.PI * 2) * (right - left);
				float segmentWidth = width / (float) Math.cos(segmentDa / 2);
				rect(handle, a, segmentDa, radius, segmentWidth, z, col, i * du,
						rune == 0 ? 0 : (rune - 1) * dv, du, dv);
			}
		}

		@OnlyIn(Dist.CLIENT)
		private int getColor() {
			if (color == null) return -1;
			String str = color.trim();
			if (str.startsWith("#")) {
				str = str.substring(1);
			}
			if (str.startsWith("0x") || str.startsWith("0X")) {
				str = str.substring(2);
			}
			if (str.length() <= 6) {
				return 0xff000000 | Integer.parseUnsignedInt(str, 16);
			}
			return Integer.parseUnsignedInt(str, 16);
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


	@OnlyIn(Dist.CLIENT)
	public static class RenderHandle {

		public final PoseStack matrix;
		public final MultiBufferSource buffer;
		public final VertexConsumer builder;
		public final float tick;
		public final int light;

		public float alpha = 1;

		public RenderHandle(PoseStack matrix, MultiBufferSource buffer, VertexConsumer builder, float tick, int light) {
			this.matrix = matrix;
			this.buffer = buffer;
			this.builder = builder;
			this.tick = tick;
			this.light = light;
		}
	}

}
