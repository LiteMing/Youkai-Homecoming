package dev.xkmc.fastprojectileapi.entity;

import java.util.concurrent.atomic.AtomicLongArray;

final class AtomicBitSet {

	private final AtomicLongArray words;

	AtomicBitSet(int bitCount) {
		int wordCount = bitCount <= 0 ? 0 : (bitCount + Long.SIZE - 1) / Long.SIZE;
		words = new AtomicLongArray(wordCount);
	}

	void set(int index) {
		int wordIndex = index >>> 6;
		long bit = 1L << (index & 63);
		while (true) {
			long current = words.get(wordIndex);
			if ((current & bit) != 0L) {
				return;
			}
			if (words.compareAndSet(wordIndex, current, current | bit)) {
				return;
			}
		}
	}

	int nextSetBit(int fromIndex) {
		if (words.length() == 0) {
			return -1;
		}
		int wordIndex = fromIndex >>> 6;
		if (wordIndex >= words.length()) {
			return -1;
		}
		long word = words.get(wordIndex) & (-1L << (fromIndex & 63));
		while (true) {
			if (word != 0L) {
				return (wordIndex << 6) + Long.numberOfTrailingZeros(word);
			}
			wordIndex++;
			if (wordIndex >= words.length()) {
				return -1;
			}
			word = words.get(wordIndex);
		}
	}

}
