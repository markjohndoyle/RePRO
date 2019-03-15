package org.mjd.repro.handlers.message;

import java.io.Serializable;
import java.util.Optional;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.mjd.repro.handlers.message.MessageHandler.HandlerException;
import org.mjd.repro.handlers.message.ResponseMessage.ResponseMessageSerialiser;

/**
 * {@link ResponseMessage} is a simple container that can hold a value of type T or a Throwable.
 *
 * Servers can send this back to clients in cases where they need to handle exceptions.
 *
 * @param <T> the response {@link #value} type.
 */
@DefaultSerializer(ResponseMessageSerialiser.class)
public final class ResponseMessage<T> implements Serializable {
	private static final long serialVersionUID = 1L;
	private final long id;
	private Optional<T> value = Optional.empty();
	private Throwable exception;

	public ResponseMessage(final long id, final T value) {
		this.id = id;
		this.value = Optional.of(value);
	}

	public ResponseMessage(final long id, final Throwable ex) {
		this.id = id;
		this.exception = ex;
	}

	public Optional<T> getValue() {
		return value;
	}

	public long getId() {
		return id;
	}

	public Optional<Throwable> getError() {
		return Optional.of(exception);
	}

	public boolean isError() {
		return exception != null;
	}

	public static ResponseMessage<Object> error(final long id, final Exception ex) {
		return new ResponseMessage<>(id, ex);
	}


	public static final class ResponseMessageSerialiser extends Serializer<ResponseMessage<Object>> {
		public ResponseMessageSerialiser() {
			super(false);
		}

		@Override
		public void write(final Kryo kryo, final Output output, final ResponseMessage<Object> object) {
			output.writeLong(object.getId());
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
		public ResponseMessage<Object>
		read(final Kryo kryo, final Input input, final Class<? extends ResponseMessage<Object>> type) {
			final long readId = input.readLong();
			if(input.readBoolean()) {
				return new ResponseMessage<>(readId, kryo.readObject(input, HandlerException.class));
			}
			return new ResponseMessage<>(readId, kryo.readClassAndObject(input));
		}}
}

