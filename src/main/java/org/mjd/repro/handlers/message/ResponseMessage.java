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

	/**
	 * Creates a "void" version of this {@link ResponseMessage}. The type will be {@link Void} and the {@link #value} will
	 * be {@link Optional#empty()} and {@link #isError()} will be false.
	 *
	 * @param id the ID this response is for
	 * @return new {@link ResponseMessage} representing {@link Void}
	 */
	public static ResponseMessage<Void> voidMsg(final long id) {
		return new ResponseMessage<>(id);
	}

	public ResponseMessage(final long id, final T value) {
		this.id = id;
		this.value = Optional.ofNullable(value);
	}

	public ResponseMessage(final long id, final Throwable ex) {
		this.id = id;
		this.exception = ex;
	}

	private ResponseMessage(final long id) {
		this.id = id;
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

	@Override
	public String toString() {
		return "ResponseMessage [id=" + id + ", value=" + value + ", exception=" + exception + "]";
	}

	public static final class ResponseMessageSerialiser extends Serializer<ResponseMessage<Object>> {
		public ResponseMessageSerialiser() {
			super(false);
		}

		@Override
		public void write(final Kryo kryo, final Output output, final ResponseMessage<Object> object) {
			output.writeLong(object.getId());
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
				final Class<? extends ResponseMessage<Object>> type) {
			final long readId = input.readLong();
			if (input.readBoolean()) {
				return new ResponseMessage<>(readId, kryo.readObject(input, HandlerException.class));
			}
			if (input.readBoolean()) {
				return new ResponseMessage<>(readId, kryo.readClassAndObject(input));
			}
			return new ResponseMessage<>(readId, null);

		}
	}
}
