package dev.xkmc.fastprojectileapi.render.core;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.function.ObjIntConsumer;

/**
 * Utility for parallel vertex buffer filling.
 * Splits a list of render instances across multiple threads,
 * each writing to an independent byte[] segment, then merges
 * results back into the BulkDataWriter on the render thread.
 * <p>
 * Thread safety: each thread writes to its own byte[] — no shared mutable state.
 */
public class ParallelBufferFiller {

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
	 * Fill vertex data in parallel and write to the BulkDataWriter.
	 *
	 * @param vc               the BulkDataWriter to write merged results into
	 * @param list             the list of render instances
	 * @param verticesPerEntry number of vertices each instance writes (e.g., 4 for a quad)
	 * @param writer           function that writes one instance's vertices into byte[] at given offset.
	 *                         Signature: (byte[] buf, int byteOffset, T instance) → void.
	 *                         The function must write exactly verticesPerEntry * 24 bytes starting at byteOffset.
	 * @param <T>              the render instance type
	 */
	public static <T> void fill(BulkDataWriter vc, List<T> list, int verticesPerEntry, InstanceWriter<T> writer) {
		int size = list.size();
		if (size == 0) return;

		int bytesPerEntry = verticesPerEntry * BulkDataWriter.STRIDE;

		if (size < PARALLEL_THRESHOLD) {
			// Single-threaded: write directly into one byte[]
			byte[] buf = new byte[size * bytesPerEntry];
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

		byte[][] segments = new byte[threads][];
		int[] segVertices = new int[threads];

		ForkJoinTask<?>[] tasks = new ForkJoinTask<?>[threads];
		for (int t = 0; t < threads; t++) {
			int from = t * chunkSize;
			int to = Math.min(from + chunkSize, size);
			if (from >= to) {
				segments[t] = new byte[0];
				segVertices[t] = 0;
				continue;
			}
			int count = to - from;
			int threadIndex = t;
			tasks[t] = ForkJoinPool.commonPool().submit(() -> {
				byte[] seg = new byte[count * bytesPerEntry];
				for (int i = 0; i < count; i++) {
					writer.write(seg, i * bytesPerEntry, list.get(from + i));
				}
				segments[threadIndex] = seg;
				segVertices[threadIndex] = count * verticesPerEntry;
			});
		}

		// Wait for all tasks
		for (int t = 0; t < threads; t++) {
			if (tasks[t] != null) {
				tasks[t].join();
			}
		}

		// Merge segments into BulkDataWriter on render thread
		for (int t = 0; t < threads; t++) {
			if (segVertices[t] > 0) {
				vc.bulkWrite(segments[t], segVertices[t]);
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
