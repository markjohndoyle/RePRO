package org.mjd.sandbox.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.mjd.sandbox.nio.async.AsyncMessageJob;
import org.mjd.sandbox.nio.async.AsyncMessageJobExecutor;
import org.mjd.sandbox.nio.async.SequentialMessageJobExecutor;
import org.mjd.sandbox.nio.handlers.key.InvalidKeyHandler;
import org.mjd.sandbox.nio.handlers.key.KeyChannelCloser;
import org.mjd.sandbox.nio.handlers.message.AsyncMessageHandler;
import org.mjd.sandbox.nio.handlers.message.MessageHandler;
import org.mjd.sandbox.nio.handlers.message.MessageHandler.ConnectionContext;
import org.mjd.sandbox.nio.handlers.op.AcceptOpHandler;
import org.mjd.sandbox.nio.handlers.op.ReadOpHandler;
import org.mjd.sandbox.nio.handlers.op.RootMessageHandler;
import org.mjd.sandbox.nio.handlers.op.WriteOpHandler;
import org.mjd.sandbox.nio.handlers.response.ResponseRefiner;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.factory.MessageFactory;
import org.mjd.sandbox.nio.message.factory.MessageFactory.MessageCreationException;
import org.mjd.sandbox.nio.writers.SizeHeaderWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

/**
 * The main server class. This is a non-blocking selectoer based server that processes messages of type MsgType.
 *
 * You can provide handlers to do something with the messages once they are decoded. This means you message type
 * can be a wrapper for various Objects or primitives that your handler(s) can interpret.
 *
 * @param <MsgType> the type of message this server expects to receive.
 */
public final class Server<MsgType> implements RootMessageHandler<MsgType> {
	private static final Logger LOG = LoggerFactory.getLogger(Server.class);

	private ServerSocketChannel serverChannel;
	private Selector selector;
	private InvalidKeyHandler validityHandler;
	private final WriteOpHandler<MsgType> writeOpHandler;
	private final ReadOpHandler<MsgType> readOpHandler;
	private final AcceptOpHandler acceptOpHandler;
	private long conId;
	private MessageHandler<MsgType> msgHandler;
	private AsyncMessageHandler<MsgType> asyncMsgHandler;
	private List<ResponseRefiner<MsgType>> responseRefiners = new ArrayList<>();
	private final AsyncMessageJobExecutor<MsgType> asyncMsgJobExecutor;

	/**
	 * Creates a fully initialised single threaded non-blocking {@link Server}.
	 *
	 * The server is not started and will not accept connections until you call
	 * {@link #start()}
	 *
	 * @param messageFactory
	 */
	public Server(final MessageFactory<MsgType> messageFactory) {
		this(messageFactory, new KeyChannelCloser());
	}

	/**
	 * @param messageFactory
	 * @param invalidKeyHandler
	 */
	public Server(final MessageFactory<MsgType> messageFactory, final InvalidKeyHandler invalidKeyHandler) {
		this.validityHandler = invalidKeyHandler;
		this.acceptOpHandler = new AcceptOpHandler();
		this.readOpHandler = new ReadOpHandler<>(messageFactory, this);
		this.writeOpHandler = new WriteOpHandler<>(selector, responseRefiners);
		this.asyncMsgJobExecutor = new SequentialMessageJobExecutor<>(writeOpHandler);
	}

	/**
	 * Starts the listen loop with a blocking selector. Each key is handled in a
	 * non-blocking loop before returning to the selector.
	 */
	public void start() {
		LOG.info("Server starting..");
		setupNonblockingServer();
		enterBlockingServerLoop();
		closeDownServer();
	}

	/**
	 * Sets the {@link MessageHandler} this server will use to handle decoded messages.
	 *
	 * @param handler the {@link MessageHandler} this server should use to handle decoded messages
	 *
	 * @return This {@link Server} instance. Useful for chaining.
	 *
	 * @notThreadSafe
     */
	public Server<MsgType> addHandler(final MessageHandler<MsgType> handler) {
		this.msgHandler = handler;
		return this;
	}

	/**
	 * @param handler
	 * @return This {@link Server} instance. Useful for chaining.
	 */
	public Server<MsgType> addAsyncHandler(final AsyncMessageHandler<MsgType> handler) {
		this.asyncMsgHandler = handler;
		return this;
	}

	/**
	 *
	 * @param handler
	 * @return This {@link Server} instance. Useful for chaining.
	 *
	 * @notThreadSafe
	 */
	public Server<MsgType> addHandler(final ResponseRefiner<MsgType> handler) {
		this.responseRefiners.add(handler);
		return this;
	}

	/**
	 * The server is considered available when the selector is open and the server
	 * socket channel is bound and listening.
	 *
	 * @return true of the server is ready to process clients.
	 */
	public boolean isAvailable() {
		return serverChannel.isOpen() && selector != null && selector.isOpen();
	}

	/**
	 * The server is considered shutown if it is not available.
	 * @return true if this {@link Server} is shutdown
	 */
	public boolean isShutdown() {
		return !isAvailable();
	}

	/**
	 * Closes the selector which will pull the server out of the blocking loop.
	 */
	public void shutDown() {
		try {
			selector.close();
		}
		catch (IOException e) {
			LOG.error("Error closing the selector when shutting down the server. Will interrupt this thread to pull "
					+ "selector out of the blocking select and then server out of it's event loop.", e);
			Thread.currentThread().interrupt();
		}
	}

	// TODO Will be moved.
	public void receive(final SelectionKey key, final MsgType subscriptionRequest, final Optional<ByteBuffer> notification) {
		LOG.trace("[{}] Refining notification {}.", key.attachment(), notification);
		final ByteBuffer bufferToWriteBack = refineResponse(subscriptionRequest, notification.get());
		if(key.isValid()) {
			writeOpHandler.add(key, SizeHeaderWriter.from(key, bufferToWriteBack));
			key.interestOps(key.interestOps() | OP_WRITE);
			selector.wakeup();
		}
		else {
			LOG.trace("[{}] Invalid key sent a notifification from subscription request {}. It will be ignored",
					key.attachment(), subscriptionRequest);
		}
	}

	@Override
	public void handle(final SelectionKey key, final Message<MsgType> message) {
		if (msgHandler == null && asyncMsgHandler == null) {
			LOG.warn("No handlers for {}. Message will be discarded.", key.attachment());
			return;
		}
		if(asyncMsgHandler != null) {
			LOG.trace("[{}] Using Async job {} for message {}", key.attachment(), message);
			asyncMsgJobExecutor.add(new AsyncMessageJob<>(key, message, asyncMsgHandler.handle(message)));
		}
		else if(msgHandler != null) {
			final Optional<ByteBuffer> resultToWrite = msgHandler.handle(new ConnectionContext<>(this, key), message);
			if (resultToWrite.isPresent()) {
				writeOpHandler.writeResult(key, message, resultToWrite.get());
				selector.wakeup();
			}
		}
	}

	private void enterBlockingServerLoop() {
		try {
			while (!Thread.interrupted()) {
				selector.select();
				final Set<SelectionKey> selectedKeys = selector.selectedKeys();
				handleReadyKeys(selectedKeys.iterator());
			}
		}
		catch (IOException e) {
			LOG.error("Fatal server error: {}", e.toString(), e);
		}
	}

	private void setupNonblockingServer() {
		try {
			selector = Selector.open();
			serverChannel = ServerSocketChannel.open();
			serverChannel.bind(new InetSocketAddress(12509));
			serverChannel.configureBlocking(false);
			serverChannel.register(selector, OP_ACCEPT, "The Director");
			asyncMsgJobExecutor.start(selector);
		}
		catch (IOException e) {
			LOG.error("Fatal server setup up server channel: {}", e.toString());
		}
	}

	private void handleReadyKeys(final Iterator<SelectionKey> iter) throws IOException, MessageCreationException {
		while (iter.hasNext()) {
			final SelectionKey key = iter.next();

			if (!key.isValid()) {
				validityHandler.handle(key);
				continue;
			}
			if (key.isAcceptable()) {
				acceptOpHandler.handle(key, serverChannel, conId++).register(selector, OP_READ, "client " + conId);
			}
			if (key.isReadable()) {
				readOpHandler.handle(key);
			}
			// client response, triggered by read.
			if (key.isValid() && key.isWritable()) {
				writeOpHandler.handle(key);
			}
			iter.remove();
		}
	}

	private ByteBuffer refineResponse(final MsgType message, final ByteBuffer resultToWrite) {
		ByteBuffer refinedBuffer = (ByteBuffer) resultToWrite.flip();
		for(final ResponseRefiner<MsgType> responseHandler : responseRefiners) {
			LOG.trace("Buffer post message handler pre response refininer {}", refinedBuffer);
			LOG.debug("Passing message value '{}' to response refiner", message);
			refinedBuffer = responseHandler.execute(message, refinedBuffer);
			refinedBuffer.flip();
		}
		return refinedBuffer;
	}

	private void closeDownServer() {
		LOG.info("Server shutting down...");
		try {
			selector.close();
			serverChannel.close();
		}
		catch (IOException e) {
			LOG.error("Error shutting down server: {}. We're going anyway ¯\\_(ツ)_/¯ ", e.toString());
		}
	}
}
