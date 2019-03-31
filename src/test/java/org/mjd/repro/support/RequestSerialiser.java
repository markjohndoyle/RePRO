package org.mjd.repro.support;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.mjd.repro.message.Request;
import org.mjd.repro.message.RequestWithArgs;

public class RequestSerialiser extends Serializer<Request> {

	@Override
	public void write(final Kryo kryo, final Output output, final Request object) {
		output.writeLong(object.getId());
	}

	@Override
	public RequestWithArgs read(final Kryo kryo, final Input input, final Class<Request> type) {
		return new RequestWithArgs(input.readLong());
	}

}
