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

	// ==================== Submit/Join split API (P5fix2) ====================

	/**
	 * Context returned by submit(), to be passed to joinAndMerge() later.
	 */
	public static class FillContext {
		final int verticesPerEntry;
		final List<TickTask> tasks;

		FillContext(int verticesPerEntry, List<TickTask> tasks) {
			this.verticesPerEntry = verticesPerEntry;
			this.tasks = tasks;
		}
	}

	/**
	 * Submit parallel fill tasks without joining.
	 * The caller can later call joinAndMerge() to wait and merge.
	 *
	 * @param list  instances to fill
	 * @param verticesPerEntry vertices per instance
	 * @param writer instance writer
	 * @return a FillContext, or null if list is empty or below threshold
	 */
	public static <T> FillContext submit(List<T> list, int verticesPerEntry, InstanceWriter<T> writer) {
		int size = list.size();
		if (size == 0 || size < PARALLEL_THRESHOLD) return null;

		int bytesPerEntry = verticesPerEntry * BulkDataWriter.STRIDE;
		int threads = Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors());
		if (threads <= 1) threads = 2;
		int chunkSize = (size + threads - 1) / threads;

		List<TickTask> tasks = new java.util.ArrayList<>(threads);

		for (int t = 0; t < threads; t++) {
			int from = t * chunkSize;
			int to = Math.min(from + chunkSize, size);
			if (from >= to) continue;
			int count = to - from;
			int threadIndex = t;
			int needed = count * bytesPerEntry;
			// Ensure buffer is large enough (main thread, before submitting)
			ensureBuffer(threadBuffers, threadIndex, needed);
			tasks.add(new TickTask(from, count, threadIndex, bytesPerEntry, needed, list, writer));
		}

		return new FillContext(verticesPerEntry, tasks);
	}

	/**
	 * Join all tasks in a single FillContext and merge results into a BulkDataWriter.
	 */
	public static void joinAndMerge(FillContext ctx, BulkDataWriter vc) {
		if (ctx == null) return;
		for (var task : ctx.tasks) {
			task.execute();
		}
		for (var task : ctx.tasks) {
			task.join();
		}
		for (var task : ctx.tasks) {
			if (task.vertexCount > 0) {
				vc.bulkWrite(threadBuffers[task.threadIndex], task.vertexCount);
			}
		}
	}

	/**
	 * Join and merge all FillContexts at once (global join point for P5fix2).
	 * All types' tasks are submitted before this call, so ForkJoinPool can
	 * interleave work across all types.
	 */
	public static void joinAndMergeAll(List<FillContext> contexts, BulkDataWriter vc) {
		// Submit all tasks
		List<TickTask> allTasks = new java.util.ArrayList<>();
		for (var ctx : contexts) {
			if (ctx == null) continue;
			for (var task : ctx.tasks) {
				task.execute();
				allTasks.add(task);
			}
		}
		// Join all
		for (var task : allTasks) {
			task.join();
		}
		// Merge all (order-preserving within each context)
		for (var ctx : contexts) {
			if (ctx == null) continue;
			for (var task : ctx.tasks) {
				if (task.vertexCount > 0) {
					vc.bulkWrite(threadBuffers[task.threadIndex], task.vertexCount);
				}
			}
		}
	}

	/**
	 * Single task unit for parallel fill.
	 */
	static class TickTask {
		final int from, count, threadIndex, bytesPerEntry, needed;
		final List<?> list;
		final InstanceWriter<?> writer;
		volatile ForkJoinTask<?> future;
		volatile int vertexCount;

		<T> TickTask(int from, int count, int threadIndex, int bytesPerEntry, int needed,
		             List<T> list, InstanceWriter<T> writer) {
			this.from = from;
			this.count = count;
			this.threadIndex = threadIndex;
			this.bytesPerEntry = bytesPerEntry;
			this.needed = needed;
			this.list = list;
			this.writer = writer;
		}

		@SuppressWarnings("unchecked")
		void execute() {
			vertexCount = count * (bytesPerEntry / BulkDataWriter.STRIDE);
			int fi = from, ci = count, bi = bytesPerEntry, ti = threadIndex;
			future = ForkJoinPool.commonPool().submit(() -> {
				byte[] seg = threadBuffers[ti];
				for (int i = 0; i < ci; i++) {
					((InstanceWriter<Object>) writer).write(seg, i * bi, list.get(fi + i));
				}
			});
		}

		void join() {
			if (future != null) future.join();
		}
	}

}
