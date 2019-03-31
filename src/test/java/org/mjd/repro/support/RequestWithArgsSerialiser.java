package org.mjd.repro.support;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.mjd.repro.message.RequestWithArgs;

public class RequestWithArgsSerialiser extends Serializer<RequestWithArgs> {

	@Override
	public void write(final Kryo kryo, final Output output, final RequestWithArgs object) {
		output.writeLong(object.getId());
		final Object[] argValues = object.getArgValues();
		output.writeInt(argValues.length);
		for(final Object arg : argValues) {
			kryo.writeClassAndObject(output, arg);
		}
	}

	@Override
	public RequestWithArgs read(final Kryo kryo, final Input input, final Class<RequestWithArgs> type) {
		final long id = input.readLong();
		final int numArgs = input.readInt();
		if(numArgs > 0) {
			final Object[] argVals = new Object[numArgs];
			for(int i = 0; i < numArgs; i++) {
				argVals[i] = kryo.readClassAndObject(input);
			}
			return new RequestWithArgs(id, argVals);
		}
		return new RequestWithArgs(id);
	}

}
