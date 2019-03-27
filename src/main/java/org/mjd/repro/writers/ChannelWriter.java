package org.mjd.repro.writers;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public interface ChannelWriter<MsgType, K extends SelectionKey> {

	/**
	 * Buffer needs to be ready for reading
	 *
	 * @param key
	 * @param message
	 * @param resultToWrite
	 */
	void writeResult(SelectionKey key, MsgType message, ByteBuffer resultToWrite);

	void write(K key);

}
