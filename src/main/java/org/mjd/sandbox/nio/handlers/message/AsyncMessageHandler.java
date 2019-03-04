package org.mjd.sandbox.nio.handlers.message;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.Future;

import org.mjd.sandbox.nio.message.Message;

/**
 * Handlers determine how messages sent to the server are handler, that is, executed.
 *
 * Essentially, this is what happens server side when a message is received. You provide this when
 * setting up the server.
 *
 * @param <MsgType>
 */
public interface AsyncMessageHandler<MsgType> {
	Future<Optional<ByteBuffer>> handle(Message<MsgType> message);
}
