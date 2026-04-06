package dev.xkmc.fastprojectileapi.render.type;

import dev.xkmc.fastprojectileapi.render.core.BulkDataWriter;
import dev.xkmc.fastprojectileapi.render.core.DisplayType;
import dev.xkmc.fastprojectileapi.render.core.ParallelBufferFiller;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public interface RenderableDanmakuType<T extends RenderableDanmakuType<T, I>, I> extends RenderableProjectileType<T, I> {

	@Override
	default int order() {
		return display().ordinal();
	}

	DisplayType display();

	ResourceLocation getTex();

	/**
	 * Write one instance's vertex data directly into a byte[] for parallel fill.
	 * Each type implements this by delegating to its Ins.texToArray().
	 */
	void writeToArray(I instance, byte[] buf, int offset);

	/**
	 * P5fix2: Submit parallel fill tasks without joining.
	 * Default implementation delegates to ParallelBufferFiller.submit().
	 * Types with special needs (e.g., Cross with 8 vertices/entry, Animated with extra param)
	 * should override this.
	 *
	 * @param vc   pre-constructed BulkDataWriter targeting this type's RenderType
	 * @param list instances to fill
	 * @return a FillContext to be joined later, or null if empty
	 */
	@SuppressWarnings("unchecked")
	default ParallelBufferFiller.FillContext submitFill(BulkDataWriter vc, List<?> list) {
		return ParallelBufferFiller.submit((List<I>) list, 4,
				(buf, off, ins) -> writeToArray(ins, buf, off));
	}

}
