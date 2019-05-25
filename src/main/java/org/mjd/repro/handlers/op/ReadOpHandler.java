package org.mjd.repro.handlers.op;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Joiner;
import org.mjd.repro.handlers.routing.MessageHandlerRouter;
import org.mjd.repro.message.factory.MessageFactory;
import org.mjd.repro.readers.MessageReader;
import org.mjd.repro.readers.RequestReader;
import org.mjd.repro.util.chain.AbstractHandler;
import org.mjd.repro.util.chain.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mjd.repro.util.Mapper.findInMap;
import static org.mjd.repro.util.SelectionKeys.closeChannel;

/**
 * A {@link ReadOpHandler} is a {@link SelectionKey} handler whose job is to handle keys in the readable operation
 * state. It is an {@link AbstractHandler} which means it forms part of a protocol chain by forwarding the key to the
 * next {@link Handler}.
 * </p>
 * The {@link ReadOpHandler} is able to read and decoded messages from the {@link Channel} associated with a
 * {@link SelectionKey}. It is able to do this over 1 to n reads, for example, in non-blocking I/O. Once a message has
 * been decoded, it is reported back to the {@link MessageHandlerRouter} given at construction.
 * </p>
 * Messages are decoded using the givein {@link MessageFactory}
 *
 * @param <MsgType> the type of messages this read decodes.
 * @param <K> the type of {@link SelectionKey}
 */
public final class ReadOpHandler<MsgType, K extends SelectionKey> extends AbstractHandler<K> {
	private static final Logger LOG = LoggerFactory.getLogger(ReadOpHandler.class);

	private final MessageFactory<MsgType> messageFactory;
	private final MessageHandlerRouter<MsgType> msgHandler;
	private final ByteBuffer bodyBuffer = ByteBuffer.allocate(4096);
	private final Map<Channel, MessageReader<MsgType>> readers = new HashMap<>();
	private ByteBuffer headerBuffer;


	/**
	 * Constructs a ready to use {@link ReadOpHandler} for message types MsgType.
	 *
	 * @param messageFactory the {@link MessageFactory} used to decode messages from bytes read off the {@link Channel}
	 * @param msgRouter    	 the {@link MessageHandlerRouter} decoded messages are forwarded to for routing to handlers
	 */
	public ReadOpHandler(final MessageFactory<MsgType> messageFactory, final MessageHandlerRouter<MsgType> msgRouter) {
		this.messageFactory = messageFactory;
		this.msgHandler = msgRouter;
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
		msgHandler.routeToHandler(key, reader.getMessage().get());
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
		closeChannel(key);
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
