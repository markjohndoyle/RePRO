package org.mjd.sandbox.nio.handlers.op;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Joiner;
import org.mjd.sandbox.nio.message.factory.MessageFactory;
import org.mjd.sandbox.nio.message.factory.MessageFactory.MessageCreationException;
import org.mjd.sandbox.nio.readers.MessageReader;
import org.mjd.sandbox.nio.readers.RequestReader;
import org.mjd.sandbox.nio.util.chain.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mjd.sandbox.nio.util.Mapper.findInMap;

public final class ReadOpHandler<MsgType, K extends SelectionKey> extends AbstractHandler<K> {
	private static final Logger LOG = LoggerFactory.getLogger(ReadOpHandler.class);

	private final ByteBuffer bodyBuffer = ByteBuffer.allocate(4096);
	private ByteBuffer headerBuffer;
	private final Map<Channel, MessageReader<MsgType>> readers = new HashMap<>();
	private final MessageFactory<MsgType> messageFactory;
	private RootMessageHandler<MsgType> rootHandler;

	public ReadOpHandler(MessageFactory<MsgType> messageFactory, RootMessageHandler<MsgType> rootHandler) {
		this.messageFactory = messageFactory;
		this.rootHandler = rootHandler;
	}

	@Override
	public void handle(final K key) {
		LOG.trace("[{}] - read op handler", key.attachment());
		if(key.isReadable() && key.isValid()) {
			final MessageReader<MsgType> reader = findInMap(readers, key.channel())
												  .or(() -> RequestReader.from(key, messageFactory));
			if(headerBuffer == null) {
				LOG.trace("Lazy header buffer allocation..");
				headerBuffer = ByteBuffer.allocate(reader.getHeaderSize());
			}
			clearReadBuffers();
			ByteBuffer[] unread;
			try {
				unread = reader.read(headerBuffer, bodyBuffer);
				processMessageReaderResults(key, reader);
				while (thereIsUnreadData(unread)) {
					unread = readUnreadData(key, unread);
					// ^ Can a client hog the server here? There is a max buffer read.
				}
			}
			catch (MessageCreationException | IOException e) {
				e.printStackTrace();
				return;
			}
		}
		passOnToNextHandler(key);
	}

	private void processMessageReaderResults(SelectionKey key, MessageReader<MsgType> reader) {
		if (reader.isEndOfStream()) {
			handleEndOfStream(key);
		}
		else if (reader.messageComplete()) {
			handleCompleteMsg(reader, key);
		}
	}

	private void handleCompleteMsg(MessageReader<MsgType> reader, SelectionKey key) {
		LOG.debug("Passing message {} to handlers.", reader.getMessage().get());
		rootHandler.handle(key, reader.getMessage().get());
		readers.remove(key.channel());
		LOG.trace("[{}] Reader is complete, removed it from reader jobs. " +
				  "There are {} read jobs remaining. {}", key.attachment(),
				  readers.size(), Joiner.on(",").withKeyValueSeparator("=").join(readers));
	}

	private ByteBuffer[] readUnreadData(SelectionKey key, ByteBuffer[] unread) throws IOException {
		ByteBuffer unreadHeader = unread[0];
		ByteBuffer unreadBody = unread[1];
		RequestReader<MsgType> followReader = RequestReader.from(key, messageFactory);
		ByteBuffer[] nextUnread = followReader.readPreloaded(unreadHeader, unreadBody);
		processMessageReaderResults(key, followReader);
		if(!followReader.messageComplete()) {
			readers.put(key.channel(), followReader);
		}
		return nextUnread;
	}

	private void handleEndOfStream(SelectionKey key) {
		LOG.debug("{} end of stream.", key.attachment());
		readers.remove(key.channel());
		cancelClient(key);
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

	private void clearReadBuffers() {
		headerBuffer.clear();
		bodyBuffer.clear();
	}

	/**
	 * If the unread header buffer has data then a reader returned unread data.
	 * @param unread the header body {@link ByteBuffer} array.
	 *
	 * @return true if there is unread data.
	 */
	private static boolean thereIsUnreadData(ByteBuffer[] unread) {
		return unread[0].capacity() > 0;
	}

}
