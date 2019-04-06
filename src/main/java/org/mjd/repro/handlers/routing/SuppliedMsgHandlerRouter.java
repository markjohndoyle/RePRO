package org.mjd.repro.handlers.routing;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.mjd.repro.async.AsyncMessageJob;
import org.mjd.repro.async.AsyncMessageJobExecutor;
import org.mjd.repro.handlers.message.MessageHandler;
import org.mjd.repro.handlers.message.MessageHandler.ConnectionContext;
import org.mjd.repro.writers.ChannelWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SuppliedMsgHandlerRouter} is a {@link MessageHandlerRouter} that uses a {@link Function} to route
 * messages.  The returned {@link Future} from the {@link MessageHandler#handle(ConnectionContext, Object)} is then
 * handed off to an {@link AsyncMessageJobExecutor} for processing the result.
 *
 * It's is provided with a source of identifiable {@link MessageHandler}s, a {@link ChannelWriter}, and an
 * {@link AsyncMessageJobExecutor} at construction time.
 *
 * @param <MsgType> the type of message this router handles.
 *
 * @NotThreadSafe unless the supplied constructor objects, e.g., Map of handlers, are threadsafe.
 */
public final class SuppliedMsgHandlerRouter<MsgType> implements MessageHandlerRouter<MsgType> {
	private static final Logger LOG = LoggerFactory.getLogger(SuppliedMsgHandlerRouter.class);
	private final Function<MsgType, String> handlerRouter;
	private final Map<String, MessageHandler<MsgType>> msgHandlers;
	private final ChannelWriter<MsgType, SelectionKey> channelWriter;
	private final AsyncMessageJobExecutor<MsgType> asyncMsgJobExecutor;

	/**
	 * Constructs a fully initialised {@link SuppliedMsgHandlerRouter} for types MsgType.
	 *
	 * @param handlerRouter       the {@link Function} that returns the ID of a {@link MessageHandler} to route the
	 * 							  message to
	 * @param msgHandlers         a Map of Identifiable {@link MessageHandler} objects
	 * @param channelWriter       {@link ChannelWriter} that can respond to the message sender
	 * @param asyncMsgJobExecutor the {@link AsyncMessageJobExecutor} that processes handled messages
	 */
	public SuppliedMsgHandlerRouter(final Function<MsgType, String> handlerRouter,
									final Map<String, MessageHandler<MsgType>> msgHandlers,
									final ChannelWriter<MsgType, SelectionKey> channelWriter,
									final AsyncMessageJobExecutor<MsgType> asyncMsgJobExecutor) {
		this.handlerRouter = handlerRouter;
		this.msgHandlers = msgHandlers;
		this.channelWriter = channelWriter;
		this.asyncMsgJobExecutor = asyncMsgJobExecutor;
	}

	@Override
	public void routeToHandler(final SelectionKey key, final MsgType message) {
		if (msgHandlers.isEmpty()) {
			LOG.warn("No handlers for {}. Message will be discarded.", key.attachment());
			return;
		}
		LOG.trace("[{}] Using Async job {} for message {}", key.attachment(), message);
		final String handlerId = handlerRouter.apply(message);
		final MessageHandler<MsgType> msgHandler = msgHandlers.get(handlerId);
		final ConnectionContext<MsgType> connectionContext = new ConnectionContext<>(channelWriter, key);
		final Future<Optional<ByteBuffer>> handlingJob = msgHandler.handle(connectionContext, message);
		asyncMsgJobExecutor.add(AsyncMessageJob.from(key, message, handlingJob));
	}
}
