package org.mjd.repro.util.kryo;

import java.nio.ByteBuffer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;

public class RpcRequestKryoPool extends Pool<Kryo> {

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
	protected final Kryo create() {
		final Kryo kryo = new Kryo();
		preRegisterClasses(kryo);
		return RpcKryo.configure(kryo);
	}

	/**
	 * Hook called before Rpc related classes are registered with Kryo. Allows extenders to register their own classes
	 * first.
	 *
	 * @param kryo the kryo pool object that will be return on calls to {@link #obtain()}
	 */
	protected void preRegisterClasses(final Kryo kryo) {
		// extension point - no-op here
	}
}