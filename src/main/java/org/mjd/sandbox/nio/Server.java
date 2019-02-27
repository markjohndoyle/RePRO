package org.mjd.sandbox.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Joiner;
import org.mjd.sandbox.nio.handlers.key.InvalidKeyHandler;
import org.mjd.sandbox.nio.handlers.key.KeyChannelCloser;
import org.mjd.sandbox.nio.message.factory.MessageFactory;
import org.mjd.sandbox.nio.message.factory.MessageFactory.MessageCreationException;
import org.mjd.sandbox.nio.readers.MessageReader;
import org.mjd.sandbox.nio.readers.RequestReader;
import org.mjd.sandbox.nio.writers.ByteBufferWriter;
import org.mjd.sandbox.nio.writers.Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import static org.mjd.sandbox.nio.util.Mapper.findInMap;

/**
 * The main server class. This is a non-blocking selectoer based server that
 * processes messages of type MsgType.
 *
 * You can provide handlers to do something with the messages once they are
 * decoded.
 *
 * @param <MsgType>
 */
public final class Server<MsgType> {
	private static final Logger LOG = LoggerFactory.getLogger(Server.class);

	private ServerSocketChannel serverChannel;
	private Selector selector;
	private long conId;

	private final Map<Channel, MessageReader<MsgType>> readers = new HashMap<>();
	private final Map<Channel, Writer> responseWriters = new HashMap<>();

	private final ByteBuffer bodyBuffer = ByteBuffer.allocate(1024);
	private final ByteBuffer headerBuffer;
	private ByteBuffer unread;

	private MessageFactory<MsgType> messageFactory;
	private RespondingMessageHandler<MsgType> handler;
	private InvalidKeyHandler validityHandler;

	/**
	 * Creates a fully initialised single threaded non-blocking {@link Server}.
	 *
	 * The server is not started and will not accept connections until you call
	 * {@link #start()}
	 *
	 * @param messageFactory
	 */
	public Server(MessageFactory<MsgType> messageFactory) {
		this(messageFactory, new KeyChannelCloser());
	}

	public Server(MessageFactory<MsgType> messageFactory, InvalidKeyHandler invalidKeyHandler) {
		this.messageFactory = messageFactory;
		this.validityHandler = invalidKeyHandler;
		headerBuffer = ByteBuffer.allocate(messageFactory.getHeaderSize());
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
	 * TODO Support multiple handlers using chain of command pattern
	 * @param handler
	 * @return
	 */
	public Server<MsgType> addHandler(RespondingMessageHandler<MsgType> handler) {
		this.handler = handler;
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

	private void enterBlockingServerLoop() {
		try {
			while (!Thread.interrupted()) {
				LOG.trace("Server blocking on select");
				selector.select();
				Set<SelectionKey> selectedKeys = selector.selectedKeys();
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
		}
		catch (IOException e) {
			LOG.error("Fatal server setup up server channel: {}", e.toString());
		}
	}

	private void handleReadyKeys(Iterator<SelectionKey> iter) throws IOException, MessageCreationException {
		while (iter.hasNext()) {
			SelectionKey key = iter.next();

			if (!key.isValid()) {
				validityHandler.handle(key);
				continue;
			}
			if (key.isAcceptable()) {
				acceptClient(key);
			}
			if (key.isReadable()) {
				readChannelFor(key);
			}
			// client response, triggered by read.
			if (key.isValid() && key.isWritable()) {
				Writer responseWriter = responseWriters.get(key.channel());
				responseWriter.write();
				if (responseWriter.isComplete()) {
					key.interestOps(OP_READ);
					responseWriters.remove(key.channel());
					LOG.trace("Writer {} is complete. Rest to read ops only. There are {} write jobs remaining.",
							key.attachment(), responseWriters.size());
				}
				else {
					LOG.trace("Writer for {} did not complete in one write", key.attachment());
				}
			}
			iter.remove();
		}
	}

	@SuppressWarnings("resource") // the client channel is registers and linked to a key via selector register.
	private void acceptClient(SelectionKey key) // throws IOException
	{
		LOG.trace("{} is acceptable, a client is connecting.", key.attachment());
		SocketChannel clientChannel;
		try {
			clientChannel = serverChannel.accept();
			if (clientChannel == null) {
				LOG.error("client channel is null after accept within acceptable key");
				return;
			}
			clientChannel.configureBlocking(false);
			clientChannel.register(selector, OP_READ, "client " + conId);
			conId++;
		}
		catch (IOException e) {
			LOG.warn("Could not accept connection from client on {} due to {}", key.attachment(), e.toString(), e);
		}
		LOG.trace("Socket accepted for client {}", conId);
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

	private void readChannelFor(SelectionKey key) throws MessageCreationException, IOException {
		MessageReader<MsgType> reader = findInMap(readers, key.channel()).or(() -> RequestReader.from(key, messageFactory));
		clearReadBuffers();
		unread = reader.read(headerBuffer, bodyBuffer);
		processMessageReaderResults(key, reader);
		clearReadBuffers();
		int count = 0;
		while (unread.hasRemaining()) {
//			// TODO early draft, probably full of bugs. Needs testing
//			// reader has finished but there are further messages in the buffer so we
//			// replace the reader with a new one
			readers.put(key.channel(), RequestReader.from(key, messageFactory));
			unread = reader.read(headerBuffer, bodyBuffer);
			processMessageReaderResults(key, reader);
			clearReadBuffers();
			System.err.println(count++ + " - looping on extra message bytes: " + unread);
		}
	}

	private void processMessageReaderResults(SelectionKey key, MessageReader<MsgType> reader) {
		if (reader.isEndOfStream()) {
			handleEndOfStream(key);
		}
		else if (reader.messageComplete()) {
			handleCompleteMsg(reader, key);
		}
	}

	private void handleEndOfStream(SelectionKey key) {
		LOG.debug("{} end of stream.", key.attachment());
		readers.remove(key.channel());
		cancelClient(key);
	}

	private void handleCompleteMsg(MessageReader<MsgType> reader, SelectionKey key) {
		if (handler == null) {
			LOG.warn("No handlers for {}. Message will be discarded.", key.attachment());
			return;
		}

		Optional<ByteBuffer> resultToWrite = handler.execute(reader.getMessage().get());
		if (resultToWrite.isPresent()) {
			responseWriters.put(key.channel(), ByteBufferWriter.from(key, resultToWrite.get()));
			key.interestOps(key.interestOps() | OP_WRITE);
		}
		readers.remove(key.channel());
		LOG.trace("[{}] Reader is complete, removed it reader jobs. There are {} read jobs remaining. {}", key.attachment(),
				readers.size(), Joiner.on(",").withKeyValueSeparator("=").join(readers));
	}

	private void clearReadBuffers() {
		headerBuffer.clear();
		bodyBuffer.clear();
	}

	private static void cancelClient(SelectionKey key) {
		try {
			LOG.debug("Closing channel for client '{}'", key.attachment());
			key.channel().close();
			key.cancel(); // necessary?
		}
		catch (IOException e) {
			LOG.warn("Exception closing channel of cancelled client {}. Key is already invalid", key.attachment());
		}
	}
}
