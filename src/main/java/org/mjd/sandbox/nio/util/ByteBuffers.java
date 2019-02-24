package org.mjd.sandbox.nio.util;

import java.nio.ByteBuffer;
import java.util.function.Function;

/**
 *
 */
public final class ByteBuffers {

	public static <T> T safe(final ByteBuffer buffer, Function<ByteBuffer, T> operation)
	{
		T result = operation.apply(buffer);
		buffer.flip();
		return result;
	}
}
