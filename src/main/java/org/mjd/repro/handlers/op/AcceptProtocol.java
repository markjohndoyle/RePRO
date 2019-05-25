package org.mjd.repro.handlers.op;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.mjd.repro.util.chain.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.channels.SelectionKey.OP_READ;

/**
 * The {@link AcceptProtocol} is an {@link AbstractHandler} for types that extends {@link SelectionKey}.
 *
 * It's job is to handle keys that are {@link SelectionKey#isAcceptable()}, that is, {@link SelectionKey#OP_ACCEPT}. If
 * the given key is not in the acceptable state and valid, it is passed on to the next handler. If it is acceptable and
 * valid a {@link SocketChannel} will be created by accepting the connection on the server socker provided at
 * construction time. This client {@link SocketChannel} will be in non-blocking mode and registered with the
 * {@link Selector} for {@link SelectionKey#OP_READ}. A unique ID is attached to the key associated with thie new
 * reigstration.
 *
 * @param <K> the type of {@link SelectionKey} this handler handles.
 */
public final class AcceptProtocol<K extends SelectionKey> extends AbstractHandler<K> {
	private static final Logger LOG = LoggerFactory.getLogger(AcceptProtocol.class);
	private final ServerSocketChannel serverChannel;
	private final Selector selector;
	private long conId;
	private Acceptor acceptor;

	@FunctionalInterface
	public interface Acceptor {
		SelectableChannel accept(ServerSocketChannel serverSocketChannel) throws IOException;
	}

	/**
	 * Constructs a fully initialised {@link AcceptProtocol} for the given {@link ServerSocketChannel} that has been
	 * registered with with given {@link Selector}.
	 *
	 * @param channel  server channel this handler accepts connections for.
	 * @param selector the {@link Selector} the clients will be registered with.
	 */
	public AcceptProtocol(final ServerSocketChannel channel, final Selector selector) {
		this(channel, selector, ssc -> ssc.accept());
	}

	public AcceptProtocol(final ServerSocketChannel channel, final Selector selector, final Acceptor acceptor) {
		this.serverChannel = channel;
		this.selector = selector;
		this.acceptor = acceptor;
	}

	@SuppressWarnings("resource") // SocketChannel is registered with selector
	@Override
	public void handle(final K key) {
		if (key.isAcceptable()) {
			LOG.trace("{} is acceptable, a client is connecting.", key.attachment());
			final SelectableChannel clientChannel;
			try {
				clientChannel = acceptor.accept(serverChannel);
				if (clientChannel != null) {
					clientChannel.configureBlocking(false);
					clientChannel.register(selector, OP_READ, "client " + conId);
					LOG.trace("Socket accepted for client {}", conId);
					conId++;
					return;
				}
				return;
			}
			catch (final IOException e) {
				LOG.warn("Could not accept connection from client on {} due to {}", key.attachment(), e.toString(), e);
				return;
			}
		}
		passOnToNextHandler(key);
	}
}
