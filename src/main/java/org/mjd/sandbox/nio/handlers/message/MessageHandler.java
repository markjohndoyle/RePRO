package org.mjd.sandbox.nio.handlers.message;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.mjd.sandbox.nio.message.Message;

/**
 * Handlers determine how messages sent to the server are handler, that is, executed.
 *
 * Essentially, this is what happens server side when a message is received. You provide this when
 * setting up the server.
 *
 * @param <MsgType>
 */
public interface MessageHandler<MsgType> {

	public static final class HandlerException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public HandlerException(String message, Throwable cause) {
			super(message, cause);
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
    Optional<ByteBuffer> handle(Message<MsgType> message);
}
