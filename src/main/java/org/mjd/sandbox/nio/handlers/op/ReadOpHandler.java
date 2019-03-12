package org.mjd.sandbox.nio.handlers.op;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Joiner;
import org.mjd.sandbox.nio.message.factory.MessageFactory;
import org.mjd.sandbox.nio.readers.MessageReader;
import org.mjd.sandbox.nio.readers.RequestReader;
import org.mjd.sandbox.nio.util.chain.AbstractHandler;
import org.mjd.sandbox.nio.util.chain.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mjd.sandbox.nio.util.Mapper.findInMap;

/**
 * A {@link ReadOpHandler} is a {@link SelectionKey} handler whose job is to handle keys in the readable operation
 * state. It is an {@link AbstractHandler} which means it forms part of a protocol chain by forwarding the key to the
 * next {@link Handler}.
 * </p>
 * The {@link ReadOpHandler} is able to read and decoded messages from the {@link Channel} associated with a
 * {@link SelectionKey}. It is able to do this over 1 to n reads, for example, in non-blocking I/O. Once a message has
 * been decoded, it is reported back to the {@link RootMessageHandler} given at construction.
 * </p>
 * Messages are decoded using the givein {@link MessageFactory}
 *
 * @param <MsgType> the type of messages this read decodes.
 * @param <K> the type of {@link SelectionKey}
 */
public final class ReadOpHandler<MsgType, K extends SelectionKey> extends AbstractHandler<K> {
	private static final Logger LOG = LoggerFactory.getLogger(ReadOpHandler.class);

	private final MessageFactory<MsgType> messageFactory;
	private final RootMessageHandler<MsgType> rootHandler;
	private final ByteBuffer bodyBuffer = ByteBuffer.allocate(4096);
	private final Map<Channel, MessageReader<MsgType>> readers = new HashMap<>();
	private ByteBuffer headerBuffer;

	/**
	 * Constructs a ready to use {@link ReadOpHandler} for message types MsgType.
	 *
	 * @param messageFactory the {@link MessageFactory} used to decode messages from bytes read off the {@link Channel}
	 * @param rootHandler    the {@link RootMessageHandler} decoded messages are forwarded to after decoding
	 */
	public ReadOpHandler(final MessageFactory<MsgType> messageFactory, final RootMessageHandler<MsgType> rootHandler) {
		this.messageFactory = messageFactory;
		this.rootHandler = rootHandler;
	}

	@Override
	public void handle(final K key) {
		LOG.trace("[{}] - read op handler", key.attachment());
		if (key.isReadable() && key.isValid()) {
			final MessageReader<MsgType> msgReader = findInMap(readers, key.channel())
														.or(() -> RequestReader.from(key, messageFactory));
			createHeaderBuffer(msgReader);
			clearReadBuffers();
			try {
				ByteBuffer[] unread = msgReader.read(headerBuffer, bodyBuffer);
				processMessageReaderResults(key, msgReader);
				while (thereIsUnreadData(unread)) {
					unread = readUnreadData(key, unread);
					// ^ Can a client hog the server here? There is a max buffer read.
				}
			}
			catch (final IOException e) {
				LOG.error("Error reading messages for {}.", key.attachment());
				return;
			}
		}
		passOnToNextHandler(key);
	}

	/**
	 * We lazily create the header buffer because it's size is given by {@link MessageReader#getHeaderSize()}
	 * which we don't know until runtime.
	 *
	 * @param msgReader a reader this handler uses to decode messages.
	 */
	private void createHeaderBuffer(final MessageReader<MsgType> msgReader) {
		if (headerBuffer == null) {
			headerBuffer = ByteBuffer.allocate(msgReader.getHeaderSize());
		}
	}

	private void processMessageReaderResults(final SelectionKey key, final MessageReader<MsgType> reader) {
		if (reader.isEndOfStream()) {
			handleEndOfStream(key);
		}
		else if (reader.messageComplete()) {
			handleCompleteMsg(reader, key);
		}
	}

	private void handleCompleteMsg(final MessageReader<MsgType> reader, final SelectionKey key) {
		LOG.debug("Passing message {} to handlers.", reader.getMessage().get());
		rootHandler.handle(key, reader.getMessage().get());
		readers.remove(key.channel());
		LOG.trace("[{}] Reader is complete, removed it from reader jobs. " + "There are {} read jobs remaining. {}",
				key.attachment(), readers.size(), Joiner.on(",").withKeyValueSeparator("=").join(readers));
	}

	private ByteBuffer[] readUnreadData(final SelectionKey key, final ByteBuffer[] unread) throws IOException {
		final ByteBuffer unreadHeader = unread[0];
		final ByteBuffer unreadBody = unread[1];
		final RequestReader<MsgType> followReader = RequestReader.from(key, messageFactory);
		final ByteBuffer[] nextUnread = followReader.readPreloaded(unreadHeader, unreadBody);
		processMessageReaderResults(key, followReader);
		if (!followReader.messageComplete()) {
			readers.put(key.channel(), followReader);
		}
		return nextUnread;
	}

	private void handleEndOfStream(final SelectionKey key) {
		LOG.debug("{} end of stream.", key.attachment());
		readers.remove(key.channel());
		cancelClient(key);
	}

	private static void cancelClient(final SelectionKey key) {
		try {
			LOG.debug("Closing channel for client '{}'", key.attachment());
			key.channel().close();
			key.cancel(); // necessary?
		}
		catch (final IOException e) {
			LOG.warn("Exception closing channel of cancelled client {}. Key is already invalid", key.attachment());
		}
	}

	private void clearReadBuffers() {
		headerBuffer.clear();
		bodyBuffer.clear();
	}

	/**
	 * If the unread header buffer has data then a reader returned unread data.
	 *
	 * @param unread the header body {@link ByteBuffer} array.
	 *
	 * @return true if there is unread data.
	 */
	private static boolean thereIsUnreadData(final ByteBuffer[] unread) {
		return unread[0].capacity() > 0;
	}

}
