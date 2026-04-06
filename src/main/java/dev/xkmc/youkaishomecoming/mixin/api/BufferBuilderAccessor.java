package dev.xkmc.youkaishomecoming.mixin.api;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.ByteBuffer;

@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {

	@Accessor
	int getNextElementByte();

	@Accessor
	void setNextElementByte(int index);

	@Accessor
	int getVertices();

	@Accessor
	void setVertices(int vert);

	/**
	 * Expose the underlying ByteBuffer for bulk memcpy in BulkDataWriter.
	 * Field name: BufferBuilder.buffer (mapped name in 1.20.1)
	 */
	@Accessor
	ByteBuffer getBuffer();

}
