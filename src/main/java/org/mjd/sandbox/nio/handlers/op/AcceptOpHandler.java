package org.mjd.sandbox.nio.handlers.op;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AcceptOpHandler {
	private static final Logger LOG = LoggerFactory.getLogger(AcceptOpHandler.class);

	private long conId;

	public SocketChannel handle(SelectionKey key, ServerSocketChannel serverChannel) // throws IOException
	{
		LOG.trace("{} is acceptable, a client is connecting.", key.attachment());
		SocketChannel clientChannel;
		try {
			clientChannel = serverChannel.accept();
			if (clientChannel == null) {
				LOG.error("client channel is null after accept within acceptable key");
				return null;
			}
			LOG.trace("Socket accepted for client {}", conId);
			clientChannel.configureBlocking(false);
			conId++;
			return clientChannel;
		}
		catch (IOException e) {
			LOG.warn("Could not accept connection from client on {} due to {}", key.attachment(), e.toString(), e);
			return null;
		}
	}
}
