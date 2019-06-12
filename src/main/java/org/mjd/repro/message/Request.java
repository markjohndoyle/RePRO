package org.mjd.repro.message;

import java.io.Serializable;

/**
 * Minimal request object that has an identifier and nothing else.
 *
 * @Immutable
 * @ThreadSafe
 */
public class Request implements Serializable {
	private static final long serialVersionUID = 1L;
	private final String id;

	/**
	 * Constructs an initialised {@link Request} from their given {@code id}
	 *
	 * @param id the id of this Request
	 */
	public Request(final String id) {
		this.id = id;
	}

	/**
	 * @return the ID of this request.
	 */
	public final String getId() {
		return id;
	}
}
