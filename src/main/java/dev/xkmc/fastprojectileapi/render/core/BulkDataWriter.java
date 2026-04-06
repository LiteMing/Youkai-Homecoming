package dev.xkmc.fastprojectileapi.render.core;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xkmc.youkaishomecoming.mixin.api.BufferBuilderAccessor;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class BulkDataWriter {

	/**
	 * Stride per vertex in bytes: 3 floats pos + 2 floats uv + 4 bytes color = 24 bytes.
	 */
	public static final int STRIDE = 24;

	private final VertexConsumer vc;
	private final BufferBuilder direct;

	public BulkDataWriter(VertexConsumer vc, int size) {
		this.vc = vc;
		direct = vc instanceof BufferBuilder buf ? buf : null;
	}

	public void addVertex(Matrix4f m4, float x, float y, float z, float u, float v, int col) {
		var vec = new Vector4f(x, y, z, 1).mul(m4);
		addVertex(vec.x, vec.y, vec.z, u, v, col);
	}

	public void addVertex(float x, float y, float z, float u, float v, int col) {
		if (direct == null) {
			vc.vertex(x, y, z).uv(u, v).color(col).endVertex();
		} else {
			direct.putFloat(0, x);
			direct.putFloat(4, y);
			direct.putFloat(8, z);
			direct.putFloat(12, u);
			direct.putFloat(16, v);
			direct.putByte(20, (byte) FastColor.ARGB32.red(col));
			direct.putByte(21, (byte) FastColor.ARGB32.green(col));
			direct.putByte(22, (byte) FastColor.ARGB32.blue(col));
			direct.putByte(23, (byte) FastColor.ARGB32.alpha(col));
			((BufferBuilderAccessor) direct).setNextElementByte(((BufferBuilderAccessor) direct).getNextElementByte() + STRIDE);
			((BufferBuilderAccessor) direct).setVertices(((BufferBuilderAccessor) direct).getVertices() + 1);
		}
	}

	/**
	 * Write a vertex with matrix transform directly into a byte array.
	 * Thread-safe when different threads write to non-overlapping offset regions.
	 *
	 * @param buf    target byte array
	 * @param offset byte offset into buf to start writing (must have 24 bytes available)
	 * @param m4     transformation matrix
	 * @param x      local X position
	 * @param y      local Y position
	 * @param z      local Z position
	 * @param u      texture U coordinate
	 * @param v      texture V coordinate
	 * @param col    ARGB32 packed color
	 */
	public static void writeVertex(byte[] buf, int offset, Matrix4f m4, float x, float y, float z, float u, float v, int col) {
		var vec = new Vector4f(x, y, z, 1).mul(m4);
		writeVertex(buf, offset, vec.x, vec.y, vec.z, u, v, col);
	}

	/**
	 * Write a vertex (already transformed) directly into a byte array.
	 * Thread-safe when different threads write to non-overlapping offset regions.
	 */
	public static void writeVertex(byte[] buf, int offset, float x, float y, float z, float u, float v, int col) {
		putFloat(buf, offset, x);
		putFloat(buf, offset + 4, y);
		putFloat(buf, offset + 8, z);
		putFloat(buf, offset + 12, u);
		putFloat(buf, offset + 16, v);
		buf[offset + 20] = (byte) FastColor.ARGB32.red(col);
		buf[offset + 21] = (byte) FastColor.ARGB32.green(col);
		buf[offset + 22] = (byte) FastColor.ARGB32.blue(col);
		buf[offset + 23] = (byte) FastColor.ARGB32.alpha(col);
	}

	/**
	 * Bulk-copy a filled byte array into this writer's underlying BufferBuilder.
	 * Must be called from the render thread. Advances the internal counters.
	 *
	 * @param data         byte array containing packed vertex data
	 * @param vertexCount  number of vertices contained in data
	 */
	public void bulkWrite(byte[] data, int vertexCount) {
		if (direct == null) {
			// Fallback: decode and write through VertexConsumer
			for (int i = 0; i < vertexCount; i++) {
				int off = i * STRIDE;
				float x = getFloat(data, off);
				float y = getFloat(data, off + 4);
				float z = getFloat(data, off + 8);
				float u = getFloat(data, off + 12);
				float vv = getFloat(data, off + 16);
				int col = (data[off + 23] & 0xFF) << 24 | (data[off + 20] & 0xFF) << 16
						| (data[off + 21] & 0xFF) << 8 | (data[off + 22] & 0xFF);
				vc.vertex(x, y, z).uv(u, vv).color(col).endVertex();
			}
		} else {
			for (int i = 0; i < data.length; i++) {
				direct.putByte(i, data[i]);
			}
			var accessor = (BufferBuilderAccessor) direct;
			accessor.setNextElementByte(accessor.getNextElementByte() + data.length);
			accessor.setVertices(accessor.getVertices() + vertexCount);
		}
	}

	public void flush() {
	}

	private static void putFloat(byte[] buf, int offset, float value) {
		int bits = Float.floatToRawIntBits(value);
		buf[offset] = (byte) bits;
		buf[offset + 1] = (byte) (bits >> 8);
		buf[offset + 2] = (byte) (bits >> 16);
		buf[offset + 3] = (byte) (bits >> 24);
	}

	private static float getFloat(byte[] buf, int offset) {
		int bits = (buf[offset] & 0xFF) | ((buf[offset + 1] & 0xFF) << 8)
				| ((buf[offset + 2] & 0xFF) << 16) | ((buf[offset + 3] & 0xFF) << 24);
		return Float.intBitsToFloat(bits);
	}

}
