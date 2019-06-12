package org.mjd.repro.support;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.mjd.repro.handlers.message.MessageHandler.HandlerException;
import org.mjd.repro.handlers.message.ResponseMessage;

public final class ResponseMessageSerialiser extends Serializer<ResponseMessage<Object>> {
	public ResponseMessageSerialiser() {
		super(false);
	}

	@Override
	public void write(final Kryo kryo, final Output output, final ResponseMessage<Object> object) {
		output.writeString(object.getId());
		if (object.isError()) {
			output.writeBoolean(true);
			kryo.writeObject(output, object.getError().get());
		}
		else {
			output.writeBoolean(false);
			if (object.getValue().isPresent()) {
				output.writeBoolean(true);
				kryo.writeClassAndObject(output, object.getValue().get());
			}
			else {
				output.writeBoolean(false);
			}
		}
	}

	@Override
	public ResponseMessage<Object> read(final Kryo kryo, final Input input,
			final Class<ResponseMessage<Object>> type) {
		final String readId = input.readString();
		if (input.readBoolean()) {
			return new ResponseMessage<>(readId, kryo.readObject(input, HandlerException.class));
		}
		if (input.readBoolean()) {
			return new ResponseMessage<>(readId, kryo.readClassAndObject(input));
		}
		return new ResponseMessage<>(readId, null);

	}
}