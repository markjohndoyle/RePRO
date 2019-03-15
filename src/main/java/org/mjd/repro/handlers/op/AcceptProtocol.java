package org.mjd.repro.handlers.op;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.mjd.repro.util.chain.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.channels.SelectionKey.OP_READ;

public final class AcceptProtocol<K extends SelectionKey> extends AbstractHandler<K> {
	private static final Logger LOG = LoggerFactory.getLogger(AcceptProtocol.class);
	private final ServerSocketChannel serverChannel;
	private final Selector selector;
	private long conId;

	public AcceptProtocol(final ServerSocketChannel channel, final Selector selector) {
		this.serverChannel = channel;
		this.selector = selector;
	}

	@Override
	public void handle(final K key) {
		if(key.isAcceptable() && key.isValid()) {
			LOG.trace("{} is acceptable, a client is connecting.", key.attachment());
			SocketChannel clientChannel;
			try {
				clientChannel = serverChannel.accept();
				if (clientChannel != null) {
					clientChannel.configureBlocking(false);
					clientChannel.register(selector, OP_READ, "client " + conId);
					LOG.trace("Socket accepted for client {}", conId);
					conId++;
				}
			}
			catch (final IOException e) {
				LOG.warn("Could not accept connection from client on {} due to {}", key.attachment(), e.toString(), e);
			}
		}
		passOnToNextHandler(key);
	}
}
