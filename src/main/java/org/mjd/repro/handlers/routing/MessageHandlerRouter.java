package org.mjd.repro.handlers.routing;

import java.nio.channels.SelectionKey;

import org.mjd.repro.handlers.message.MessageHandler;

/**
 * {@link MessageHandlerRouter}s should route messages to {@link MessageHandler}s on demand.
 *
 * @param <MsgType> the type of message this router handles
 */
public interface MessageHandlerRouter<MsgType> {

	/**
	 * Route the given {@code message} and it's associated {@link SelectionKey} to the the necessary handler.
	 *
	 * @param key     the {@link SelectionKey} associated with the message, that is, the key this message originated from
	 * @param message the message to route
	 */
	void routeToHandler(SelectionKey key, MsgType message);
}
