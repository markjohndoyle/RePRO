package org.mjd.repro.message.factory;

import java.nio.channels.SocketChannel;

import org.mjd.repro.Server;

/**
 * Creates instances of type T from a byte array, essentially this is how you tell your RePro {@link Server} how to
 * create the messages you are going to send to it once it has extracted the raw bytes from the {@link SocketChannel}
 *
 * @param <T> the type of the Message.
 */
@FunctionalInterface
public interface MessageFactory<T> {
	/**
	 * Exception {@link MessageFactory} implementations can throw if there is a problem creating the message.
	 */
	class MessageCreationException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public MessageCreationException(final Throwable e) {
			super(e);
		}

	}

	/**
	 * Creates a mesage of type T from the given {@code bytes}.
	 *
	 * @param bytes the raw bytes to create the messsage of type T from
	 * @return message of type T
	 * @throws MessageCreationException if there is any issue creating the message
	 */
	T createMessage(byte[] bytes) throws MessageCreationException;
}
