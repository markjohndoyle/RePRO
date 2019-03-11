package org.mjd.sandbox.nio.handlers.op;

import java.nio.channels.SelectionKey;

import org.mjd.sandbox.nio.util.chain.AbstractHandler;
import org.mjd.sandbox.nio.writers.ChannelWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WriteOpHandler<MsgType, K extends SelectionKey> extends AbstractHandler<K> {

	private final ChannelWriter<MsgType, K> channelWriter;

	public WriteOpHandler(final ChannelWriter<MsgType, K> channelWriter) {
		this.channelWriter = channelWriter;
	}

	@Override
	public void handle(final K key) {
		if(key.isValid() && key.isWritable()) {
			channelWriter.write(key);
		}
		passOnToNextHandler(key);
	}
}
