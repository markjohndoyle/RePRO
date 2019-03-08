package org.mjd.sandbox.nio.handlers.message;

import java.io.Serializable;
import java.util.Optional;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.mjd.sandbox.nio.handlers.message.MessageHandler.HandlerException;
import org.mjd.sandbox.nio.handlers.message.ResponseMessage.ResponseMessageSerialiser;

@DefaultSerializer(ResponseMessageSerialiser.class)
public final class ResponseMessage<T> implements Serializable {
	private static final long serialVersionUID = 1L;
	private Optional<T> value;
	private HandlerException exception;

	public ResponseMessage(T value) {
		this.value = Optional.of(value);
	}

	public ResponseMessage(HandlerException ex) {
		this.exception = ex;
	}

	public Optional<T> getValue() {
		return value;
	}

	public Optional<HandlerException> getError() {
		return Optional.of(exception);
	}

	public boolean isError() {
		return exception != null;
	}


	public static final class ResponseMessageSerialiser extends Serializer<ResponseMessage<Object>> {
		public ResponseMessageSerialiser() {
			super(false);
		}

		@Override
		public void write(Kryo kryo, Output output, ResponseMessage<Object> object) {
			if(object.isError()) {
				output.writeBoolean(true);
				kryo.writeObject(output, object.getError().get());
			}
			else {
				output.writeBoolean(false);
				kryo.writeClassAndObject(output, object.getValue().get());
			}
		}

		@Override
		public ResponseMessage<Object> read(Kryo kryo, Input input, Class<? extends ResponseMessage<Object>> type) {
			if(input.readBoolean()) {
				return new ResponseMessage<>(kryo.readObject(input, HandlerException.class));
			}
			return new ResponseMessage<>(kryo.readClassAndObject(input));
		}}
}

