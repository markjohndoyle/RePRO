package org.mjd.repro.support;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.mjd.repro.message.RpcRequest;

/**
 * Kryo {@link Serializer} for the {@link RpcRequest} class.
 */
public final class RpcRequestSerialiser extends Serializer<RpcRequest> {

	@Override
	public void write(final Kryo kryo, final Output output, final RpcRequest object) {
		output.writeLong(object.getId());
		output.writeString(object.getMethod());
		final Object[] argValues = object.getArgValues();
		output.writeInt(argValues.length);
		for(final Object arg : argValues) {
			kryo.writeClassAndObject(output, arg);
		}
	}

	@Override
	public RpcRequest read(final Kryo kryo, final Input input, final Class<RpcRequest> type) {
		final long id = input.readLong();
		final String method = input.readString();
		final int numArgs = input.readInt();
		if(numArgs > 0) {
			final Object[] argVals = new Object[numArgs];
			for(int i = 0; i < numArgs; i++) {
				argVals[i] = kryo.readClassAndObject(input);
			}
			return new RpcRequest(id, method, argVals);
		}
		return new RpcRequest(id, method);
	}
}
