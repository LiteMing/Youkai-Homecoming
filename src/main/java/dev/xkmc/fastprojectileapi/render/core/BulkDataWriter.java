package dev.xkmc.fastprojectileapi.render.core;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;

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

	/**
	 * Single-vertex write. Always routes through the VertexConsumer chain because
	 * per-vertex direct writes (putFloat/putByte + manual counter advance) bypass
	 * Embeddium's state machine under Oculus. The single-vertex path is not hot
	 * (bulk writes go through {@link #bulkWrite}), so the overhead is negligible.
	 */
	public void addVertex(float x, float y, float z, float u, float v, int col) {
		vc.vertex(x, y, z).uv(u, v).color(col).endVertex();
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
	 * <p>
	 * Uses vanilla's {@link BufferBuilder#putBulkData(ByteBuffer)} API for the
	 * direct path. This is BufferBuilder's own public bulk-write method —
	 * Embeddium / Oculus do not intercept it (they only override the 9-arg
	 * BakedQuad overload), so a single memcpy serves both vanilla and Oculus
	 * pipelines correctly.
	 *
	 * @param data         byte array containing packed vertex data (little-endian)
	 * @param vertexCount  number of vertices contained in data
	 */
	public void bulkWrite(byte[] data, int vertexCount) {
		if (direct == null) {
			// Fallback: VertexConsumer is not a BufferBuilder — decode and write through the chain
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
			// Vanilla putBulkData: ensureCapacity + buffer.put + counters advance + position(0) reset.
			// Single native memcpy under the hood, fully compatible with Embeddium because it
			// goes through BufferBuilder's own bookkeeping rather than bypassing it.
			int totalBytes = vertexCount * STRIDE;
			ByteBuffer src = ByteBuffer.wrap(data, 0, totalBytes);
			direct.putBulkData(src);
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
