package dev.xkmc.fastprojectileapi.render.core;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

/**
 * Utility for parallel vertex buffer filling.
 * Splits a list of render instances across multiple threads,
 * each writing to an independent byte[] segment, then merges
 * results back into the BulkDataWriter on the render thread.
 * <p>
 * Thread safety: each thread writes to its own byte[] — no shared mutable state.
 */
public class ParallelBufferFiller {

	private static final Logger LOGGER = LogUtils.getLogger();

	/**
	 * Minimum instance count to justify parallel execution.
	 * Below this threshold, single-threaded is faster due to thread scheduling overhead.
	 */
	private static final int PARALLEL_THRESHOLD = 2000;

	/**
	 * Maximum number of worker threads to use.
	 */
	private static final int MAX_THREADS = 4;

	/**
	 * Reusable byte[] buffers per thread slot to avoid per-frame allocation.
	 * For 170k danmaku × 4 vertices × 24 bytes = 16MB/frame of byte[] — major GC source.
	 * Buffers grow as needed and are retained across frames.
	 */
	private static final byte[][] threadBuffers = new byte[MAX_THREADS][];
	/** Single-thread reusable buffer. */
	private static byte[] singleBuffer = null;

	private static byte[] ensureBuffer(byte[][] buffers, int index, int needed) {
		byte[] buf = buffers[index];
		if (buf == null || buf.length < needed) {
			buffers[index] = new byte[needed];
			return buffers[index];
		}
		return buf;
	}

	private static byte[] ensureSingleBuffer(int needed) {
		if (singleBuffer == null || singleBuffer.length < needed) {
			singleBuffer = new byte[needed];
		}
		return singleBuffer;
	}

	public static <T> void fill(BulkDataWriter vc, List<T> list, int verticesPerEntry, InstanceWriter<T> writer) {
		int size = list.size();
		if (size == 0) return;

		int bytesPerEntry = verticesPerEntry * BulkDataWriter.STRIDE;

		if (size < PARALLEL_THRESHOLD) {
			// Single-threaded: reuse buffer
			int needed = size * bytesPerEntry;
			byte[] buf = ensureSingleBuffer(needed);
			for (int i = 0; i < size; i++) {
				writer.write(buf, i * bytesPerEntry, list.get(i));
			}
			vc.bulkWrite(buf, size * verticesPerEntry);
			return;
		}

		// Parallel path
		int threads = Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors());
		if (threads <= 1) threads = 2; // at least 2 for parallelism
		int chunkSize = (size + threads - 1) / threads;

		int[] segVertices = new int[threads];
		int[] segBytes = new int[threads];

		try {
			ForkJoinTask<?>[] tasks = new ForkJoinTask<?>[threads];
			for (int t = 0; t < threads; t++) {
				int from = t * chunkSize;
				int to = Math.min(from + chunkSize, size);
				if (from >= to) {
					segVertices[t] = 0;
					segBytes[t] = 0;
					continue;
				}
				int count = to - from;
				int threadIndex = t;
				int needed = count * bytesPerEntry;
				segBytes[threadIndex] = needed;
				segVertices[threadIndex] = count * verticesPerEntry;
				// Ensure buffer is large enough (main thread, before submitting)
				ensureBuffer(threadBuffers, threadIndex, needed);
				tasks[t] = ForkJoinPool.commonPool().submit(() -> {
					byte[] seg = threadBuffers[threadIndex];
					for (int i = 0; i < count; i++) {
						writer.write(seg, i * bytesPerEntry, list.get(from + i));
					}
				});
			}

			// Wait for all tasks
			for (int t = 0; t < threads; t++) {
				if (tasks[t] != null) {
					tasks[t].join();
				}
			}
		} catch (Exception e) {
			// Parallel execution failed, fall back to single-threaded
			LOGGER.warn("Parallel buffer fill failed, falling back to single-threaded", e);
			int needed = size * bytesPerEntry;
			byte[] buf = ensureSingleBuffer(needed);
			for (int i = 0; i < size; i++) {
				writer.write(buf, i * bytesPerEntry, list.get(i));
			}
			vc.bulkWrite(buf, size * verticesPerEntry);
			return;
		}

		// Merge segments into BulkDataWriter on render thread
		for (int t = 0; t < threads; t++) {
			if (segVertices[t] > 0) {
				vc.bulkWrite(threadBuffers[t], segVertices[t]);
			}
		}
	}

	/**
	 * Functional interface for writing one instance's vertex data into a byte array.
	 */
	@FunctionalInterface
	public interface InstanceWriter<T> {
		/**
		 * Write vertex data for a single instance.
		 *
		 * @param buf        target byte array
		 * @param byteOffset starting byte offset in buf
		 * @param instance   the render instance to write
		 */
		void write(byte[] buf, int byteOffset, T instance);
	}

}
