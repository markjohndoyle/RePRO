package org.mjd.repro.message.factory;

/**
 * Creates {@link Message} instances of type T from a byte array.
 *
 * @param <T> the type of the Message.
 */
public interface MessageFactory<T> {
	class MessageCreationException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public MessageCreationException(final Throwable e) {
			super(e);
		}

	}

	T createMessage(byte[] bytesRead) throws MessageCreationException;
}
