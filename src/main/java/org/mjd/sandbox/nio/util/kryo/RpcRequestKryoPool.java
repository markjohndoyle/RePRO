package org.mjd.sandbox.nio.util.kryo;

import java.nio.ByteBuffer;
import java.util.Optional;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.esotericsoftware.kryo.util.Pool;
import org.mjd.sandbox.nio.handlers.message.MessageHandler.HandlerException;
import org.mjd.sandbox.nio.handlers.message.ResponseMessage;
import org.mjd.sandbox.nio.message.IdentifiableRequest;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.util.ArgumentValues;
import org.mjd.sandbox.nio.util.ArgumentValues.ArgumentValuePair;

public final class RpcRequestKryoPool extends Pool<Kryo> {

	public RpcRequestKryoPool(final boolean threadSafe, final boolean softReferences, final int maximumCapacity) {
		super(threadSafe, softReferences, maximumCapacity);
	}

	static final class ByteBufferSerializer extends Serializer<ByteBuffer> {
		@Override
		public void write(final Kryo kryo, final Output output, final ByteBuffer object) {
			output.writeInt(object.capacity());
			output.write(object.array());
		}

		@Override
		public ByteBuffer read(final Kryo kryo, final Input input, final Class<? extends ByteBuffer> type) {
			final int length = input.readInt();
			final byte[] buffer = new byte[length];
			input.read(buffer, 0, length);
			return ByteBuffer.wrap(buffer, 0, length);
		}
	}

	@Override
	protected Kryo create() {
		final Kryo kryo = new Kryo();
		kryo.addDefaultSerializer(java.lang.Throwable.class, new JavaSerializer());
		kryo.register(IdentifiableRequest.class);
		kryo.register(RpcRequest.class);
		kryo.register(ArgumentValues.class);
		kryo.register(ArgumentValuePair.class);
		kryo.register(ResponseMessage.class, new ResponseMessage.ResponseMessageSerialiser());
		kryo.register(HandlerException.class);
		kryo.register(Optional.class);
		kryo.register(ByteBuffer.allocate(0).getClass(), new ByteBufferSerializer());
		kryo.register(Object.class);
		return kryo;
	}
}