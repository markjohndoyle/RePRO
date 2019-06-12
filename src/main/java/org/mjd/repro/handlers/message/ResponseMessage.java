package org.mjd.repro.handlers.message;

import java.io.Serializable;
import java.util.Optional;

/**
 * {@link ResponseMessage} is a simple container that can hold a value of type T or a Throwable.
 *
 * Servers can send this back to clients in cases where they need to handle exceptions.
 *
 * @param <T> the response {@link #value} type.
 */
public final class ResponseMessage<T> implements Serializable {
	private static final long serialVersionUID = 1L;
	private final String id;
	private Optional<T> value = Optional.empty();
	private Throwable exception;

	public ResponseMessage(final String id, final T value) {
		this.id = id;
		this.value = Optional.ofNullable(value);
	}

	public ResponseMessage(final String id, final Throwable ex) {
		this.id = id;
		this.exception = ex;
	}

	private ResponseMessage(final String id) {
		this.id = id;
	}

	public Optional<T> getValue() {
		return value;
	}

	public String getId() {
		return id;
	}

	public Optional<Throwable> getError() {
		return Optional.ofNullable(exception);
	}

	public boolean isError() {
		return exception != null;
	}

	/**
	 * Creates a "void" version of this {@link ResponseMessage}. The type will be {@link Void} and the {@link #value} will
	 * be {@link Optional#empty()} and {@link #isError()} will be false.
	 *
	 * @param id the ID this response is for
	 * @return new {@link ResponseMessage} representing {@link Void}
	 */
	public static ResponseMessage<Void> voidMsg(final String id) {
		return new ResponseMessage<>(id);
	}

	public static ResponseMessage<Object> error(final String id, final Exception ex) {
		return new ResponseMessage<>(id, ex);
	}

	@Override
	public String toString() {
		return "ResponseMessage [id=" + id + ", value=" + value + ", exception=" + exception + "]";
	}
}
