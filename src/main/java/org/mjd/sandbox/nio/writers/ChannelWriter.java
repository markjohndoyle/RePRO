package org.mjd.sandbox.nio.writers;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import org.mjd.sandbox.nio.message.Message;

public interface ChannelWriter<MsgType, K extends SelectionKey> {

	void writeResult(SelectionKey key, Message<MsgType> message, ByteBuffer resultToWrite);

	void write(K key);

}
