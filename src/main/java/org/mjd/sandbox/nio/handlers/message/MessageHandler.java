package org.mjd.sandbox.nio.handlers.message;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Optional;
import java.util.concurrent.Future;

import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.writers.ChannelWriter;

/**
 * Handlers determine how messages sent to the server are handler, that is, executed.
 *
 * Essentially, this is what happens server side when a message is received. You provide this when
 * setting up the server.
 *
 * @param <MsgType>
 */
public interface MessageHandler<MsgType> {

	final class HandlerException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public HandlerException(final String message, final Throwable cause) {
			super(message, cause);
		}
	}

	final class ConnectionContext<MsgType> {
		public final SelectionKey key;
		public final ChannelWriter<MsgType, SelectionKey> writer;

		public ConnectionContext(final ChannelWriter<MsgType, SelectionKey> server, final SelectionKey key) {
			this.writer = server;
			this.key = key;
		}
	}

    /**
     * Do your stuff, whatever that may be.
     * You'll get a message of type MsgType which is what the client sent to you. Do with that what you
     * will and then return something to write back!
     *
     * ByteBuffer returned must be readable, that is, flipped!
     * @param message
     * @return
     */
    Future<Optional<ByteBuffer>> handle(ConnectionContext<MsgType> connectionContext, Message<MsgType> message);
}
