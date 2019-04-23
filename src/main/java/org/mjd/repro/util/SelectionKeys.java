package org.mjd.repro.util;

import java.io.IOException;
import java.nio.channels.SelectionKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility functions related to the {@link SelectionKey} class.
 */
public final class SelectionKeys {
	private static final Logger LOG = LoggerFactory.getLogger(SelectionKey.class);

	private SelectionKeys() {
		// Utility functions
	}

	/**
	 * Closes the channel associated with the given {@link SelectionKey} and cancels the key.
	 *
	 * If there is an {@link IOException} the key is cancelled anyway.
	 *
	 * @param key the {@link SelectionKey} to close the channel for
	 */
	public static void closeChannel(final SelectionKey key) {
		try {
			LOG.debug("Closing channel for client '{}'", key.attachment());
			key.channel().close();
			key.cancel();
		}
		catch (final IOException e) {
			LOG.warn("Exception closing channel of cancelled client {}. The Key has been cancelled anyway",
					 key.attachment());
		}
	}
}
