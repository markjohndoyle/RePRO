package org.mjd.repro;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.mjd.repro.async.AsyncMessageJobExecutor;
import org.mjd.repro.async.SequentialMessageJobExecutor;
import org.mjd.repro.handlers.message.MessageHandler;
import org.mjd.repro.handlers.op.AcceptProtocol;
import org.mjd.repro.handlers.op.ReadOpHandler;
import org.mjd.repro.handlers.op.WriteOpHandler;
import org.mjd.repro.handlers.response.ResponseRefiner;
import org.mjd.repro.handlers.routing.SuppliedMsgHandlerRouter;
import org.mjd.repro.message.factory.MessageFactory;
import org.mjd.repro.message.factory.MessageFactory.MessageCreationException;
import org.mjd.repro.util.chain.ProtocolChain;
import org.mjd.repro.writers.ChannelWriter;
import org.mjd.repro.writers.RefiningChannelWriter;
import org.mjd.repro.writers.SizeHeaderWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.channels.SelectionKey.OP_ACCEPT;

/**
 * The main server class. This is a non-blocking selectoer based server that processes messages of type MsgType.
 *
 * You can provide handlers to do something with the messages once they are decoded. This means you message type
 * can be a wrapper for various Objects or primitives that your handler(s) can interpret.
 *
 * @param <MsgType> the type of message this server expects to receive.
 */
public final class Server<MsgType> {
	private static final String DEFAULT_MSG_HANDLER_ID = "default";

	private static final Logger LOG = LoggerFactory.getLogger(Server.class);

	private final Map<String, MessageHandler<MsgType>> msgHandlers = new HashMap<>();
	private final List<ResponseRefiner<MsgType>> responseRefiners = new ArrayList<>();
	private final AsyncMessageJobExecutor<MsgType> asyncMsgJobExecutor;
	private final ProtocolChain<SelectionKey> keyProtocol;
	private ServerSocketChannel serverChannel;
	private Selector selector;
	private int port;



	/**
	 * Creates a fully initialised single threaded non-blocking {@link Server}.</br>
	 * The server is not started and will not accept connections until you call {@link #start()}.</br>
	 * The port will be assigned by the operating system.
	 *
	 * @param messageFactory {@link MessageFactory} used to decode messages exepcted by this server
	 */
	public Server(final MessageFactory<MsgType> messageFactory) {
		this(new InetSocketAddress(0), messageFactory);
	}

	/**
	 * Creates a fully initialised single threaded non-blocking {@link Server}.</br>
	 * The server is not started and will not accept connections until you call {@link #start()}.</br>
	 * The server will attempt to bind on the given {@link InetSocketAddress} {@code serverAddress}</br>
	 * All messages will be routed through the default message handler.
	 *
	 * @param serverAddress  the address this server shoudl bind to
	 * @param messageFactory {@link MessageFactory} used to decode messages exepcted by this server
	 */
	public Server(final InetSocketAddress serverAddress, final MessageFactory<MsgType> messageFactory) {
		this(serverAddress, messageFactory, (msg) -> DEFAULT_MSG_HANDLER_ID);
	}

	/**
	 * Creates a fully initialised single threaded non-blocking {@link Server}.</br>
	 * The server is not started and will not accept connections until you call {@link #start()}.</br>
	 * The server will bind on an available port provided by the OS. The port number is available via
	 * {@link #getPort()}</br>
	 * This server will route all messages based upon the given {@code handlerRouter}
	 *
	 * @param messageFactory {@link MessageFactory} used to decode messages exepcted by this server
	 * @param handlerRouter  a fucntional that accepts a message of type MsgType and returns a {@link MessageHandler} ID
	 *                       string.
	 */
	public Server(final MessageFactory<MsgType> messageFactory, final Function<MsgType, String> handlerRouter) {
		this(new InetSocketAddress(0), messageFactory, handlerRouter);
	}

	/**
	 * Creates a fully initialised single threaded non-blocking {@link Server}.</br>
	 * The server is not started and will not accept connections until you call {@link #start()}.</br>
	 * The server will attempt to bind on the given {@link InetSocketAddress} {@code serverAddress}</br>
	 * This server will route all messages based upon the given {@code handlerRouter}
	 *
	 * @param serverAddress  the address this server shoudl bind to
	 * @param messageFactory {@link MessageFactory} used to decode messages exepcted by this server
	 * @param handlerRouter  a fucntional that accepts a message of type MsgType and returns a {@link MessageHandler} ID
	 *                       string.
	 */
	public Server(final InetSocketAddress serverAddress, final MessageFactory<MsgType> messageFactory,
				  final Function<MsgType, String> handlerRouter) {
		setupNonblockingServer(serverAddress);
		final ChannelWriter<MsgType, SelectionKey> channelWriter =
				new RefiningChannelWriter<>(selector, responseRefiners, (k, b) -> SizeHeaderWriter.from(k, b));
		asyncMsgJobExecutor = new SequentialMessageJobExecutor<>(selector, channelWriter, true);

		final SuppliedMsgHandlerRouter<MsgType> msgRouter =
				new SuppliedMsgHandlerRouter<>(handlerRouter, msgHandlers, channelWriter, asyncMsgJobExecutor);
		keyProtocol = new ProtocolChain<SelectionKey>()
							.add(new AcceptProtocol<>(serverChannel, selector))
							.add(new ReadOpHandler<>(messageFactory, msgRouter))
							.add(new WriteOpHandler<>(channelWriter));
	}

	/**
	 * Starts the listen loop with a blocking selector. Each key is handled in a
	 * non-blocking loop before returning to the selector.
	 */
	public void start() {
		LOG.info("Server starting..");
		asyncMsgJobExecutor.start();
		enterBlockingServerLoop();
		closeDownServer();
	}

	/**
	 * Adds a {@link MessageHandler} this server can use to handle decoded messages. The given ID is used to
	 * identify the handler when routing messages.
	 *
	 * @param id the identifier of this handler.
	 * @param handler the {@link MessageHandler} this server should use to handle decoded messages
	 * @return This {@link Server} instance. Useful for chaining.
	 * @notThreadSafe
	 */
	public Server<MsgType> addHandler(final String id, final MessageHandler<MsgType> handler) {
		final MessageHandler<MsgType> old = msgHandlers.putIfAbsent(id, handler);
		if(old != null) {
			LOG.warn("Message Handler for ID '{}' already exists. Repeated attempts to add handlers will be ignored", id);
		}
		return this;
	}

	/**
	 * Adds a {@link MessageHandler} as the "default" message handler this server will use to handle decoded messages.
	 * Message routing functionals provided to this Server can also, if required, route to this handler. It's ID
	 * will be {@value #DEFAULT_MSG_HANDLER_ID}
	 *
	 * @param handler the {@link MessageHandler} this server should use to handle decoded messages
	 * @return This {@link Server} instance. Useful for chaining.
	 * @notThreadSafe
	 */
	public Server<MsgType> addHandler(final MessageHandler<MsgType> handler) {
		final MessageHandler<MsgType> old = msgHandlers.putIfAbsent(DEFAULT_MSG_HANDLER_ID, handler);
		if(old != null) {
			LOG.warn("Default Message Handler already exists. Repeated attempts to add handlers will be ignored");
		}
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
	 * Closes the selector which will pull the server out of the blocking loop.
	 */
	public void shutDown() {
		closeDownServer();
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

	/** @return the port this {@link Server} is bound to */
	public int getPort() {
		return port;
	}

	private void setupNonblockingServer(final InetSocketAddress address) {
		try {
			selector = Selector.open();
			serverChannel = ServerSocketChannel.open();
			serverChannel.bind(address);
			serverChannel.configureBlocking(false);
			serverChannel.register(selector, OP_ACCEPT, "The Director");
			port = ((InetSocketAddress)serverChannel.getLocalAddress()).getPort();
		}
		catch (final IOException e) {
			LOG.error("Fatal server setup up server channel: {}", e.toString());
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
		catch(final ClosedSelectorException e) {
			LOG.warn("Selector closed when getting keys; probably shutting down");
		}
		catch (final IOException e) {
			LOG.error("Fatal server error: {}", e.toString(), e);
		}
	}

	private void handleReadyKeys(final Iterator<SelectionKey> keyIterator) throws MessageCreationException {
		while (keyIterator.hasNext()) {
			keyProtocol.handle(keyIterator.next());
			keyIterator.remove();
		}
	}

	private void closeDownServer() {
		LOG.info("Server shutting down...");
		try {
			asyncMsgJobExecutor.stop();
			selector.close();
			serverChannel.close();
		}
		catch (final IOException e) {
			LOG.error("Error shutting down server: {}. We're going anyway ¯\\_(ツ)_/¯ ", e.toString());
		}
	}
}
