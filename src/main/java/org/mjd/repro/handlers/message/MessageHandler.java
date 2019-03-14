package org.mjd.repro.handlers.message;

import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.util.Optional;
import java.util.concurrent.Future;

import org.mjd.repro.message.Message;
import org.mjd.repro.writers.ChannelWriter;

/**
 * Handlers determine how messages sent to the server are handler, that is, executed.
 *
 * Essentially, this is what happens server side when a message is received. You provide this when setting up the
 * server.
 *
 * @param <MsgType>
 */
public interface MessageHandler<MsgType> {

	/**
	 * Thrown by implementations of {@link MessageHandler} if there is a {@link RuntimeException} handling the message.
	 */
	final class HandlerException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public HandlerException(final String message, final Throwable cause) {
			super(message, cause);
		}
	}

	/**
	 * Connection specifics related to the connection the {@link Message} was decoded on. {@link MessageHandler}
	 * implementations are free to use this as required.
	 *
	 * @param <MsgType> the type of {@link Message} this {@link ConnectionContext} is related to
	 */
	final class ConnectionContext<MsgType> {
		/** {@link SelectionKey} the Messages was decoded for */
		private final SelectionKey key;
		/** The {@link ChannelWriter} associated with the {@link #key} */
		private final ChannelWriter<MsgType, SelectionKey> writer;

		/**
		 * Constructs a fully initialised {@link ConnectionContext}
		 *
		 * @param channelWriter the {@link ChannelWriter} associated with the {@link SelectionKey} and it's
		 * 						{@link Channel}
		 * @param key           the {@link SelectionKey} the {@link Message} was decoded for.
		 */
		public ConnectionContext(final ChannelWriter<MsgType, SelectionKey> channelWriter, final SelectionKey key) {
			this.writer = channelWriter;
			this.key = key;
		}

		/**
		 * @return {@link #key}
		 */
		public SelectionKey getKey() {
			return key;
		}

		/**
		 * @return {@link #writer}
		 */
		public ChannelWriter<MsgType, SelectionKey> getWriter() {
			return writer;
		}
	}

	/**
	 * Handles the given {@link Message}. {@link MessageHandler} implementations are free to do whatever they wish
	 * with the message.
	 *
	 * If there is a result to write back to the a client, it must be returned in a {@link Future} as a {@link Optional}
	 * {@link ByteBuffer}. If there is nothing to send back to the client return an {@link Optional#empty()}
	 *
	 * The {@link ByteBuffer} must be flipped to a readable state.
	 *
	 * @param connectionContext {@link ConnectionContext} associated with the given {@code message}
	 * @param message           the {@link Message} to handle
	 * @return a Future containing an optional return value to write to the client that sent the message
	 */
	Future<Optional<ByteBuffer>> handle(ConnectionContext<MsgType> connectionContext, Message<MsgType> message);
}
