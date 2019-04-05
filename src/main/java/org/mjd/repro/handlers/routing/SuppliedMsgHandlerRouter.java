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

public class SuppliedMsgHandlerRouter<MsgType> implements MessageHandlerRouter<MsgType> {
	private static final Logger LOG = LoggerFactory.getLogger(SuppliedMsgHandlerRouter.class);
	private final Function<MsgType, String> handlerRouter;
	private final Map<String, MessageHandler<MsgType>> msgHandlers;
	private final ChannelWriter<MsgType, SelectionKey> channelWriter;
	private final AsyncMessageJobExecutor<MsgType> asyncMsgJobExecutor;

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
	public void routetoHandler(final SelectionKey key, final MsgType message) {
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
