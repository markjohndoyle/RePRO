package org.mjd.repro.util.kryo;

import java.nio.ByteBuffer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

final class ByteBufferSerializer extends Serializer<ByteBuffer> {
	@Override
	public void write(final Kryo kryo, final Output output, final ByteBuffer object) {
		output.writeInt(object.capacity());
		output.write(object.array());
	}

	@Override
	public ByteBuffer read(final Kryo kryo, final Input input, final Class<ByteBuffer> type) {
		final int length = input.readInt();
		final byte[] buffer = new byte[length];
		input.read(buffer, 0, length);
		return ByteBuffer.wrap(buffer, 0, length);
	}
}