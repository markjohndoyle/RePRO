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
	private final long id;

	/**
	 * Constructs an initialised {@link Request} frmo ther given {@code id}
	 *
	 * @param id the id of this Request
	 */
	public Request(final long id) {
		this.id = id;
	}

	/**
	 * @return the ID of this request.
	 */
	public final long getId() {
		return id;
	}
}
