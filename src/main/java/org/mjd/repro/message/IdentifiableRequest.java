package org.mjd.repro.message;

import java.io.Serializable;

import org.mjd.repro.util.ArgumentValues;

public class IdentifiableRequest implements Serializable {
	private static final long serialVersionUID = 1L;

	private long id;
	private ArgumentValues argValues = new ArgumentValues();

	public IdentifiableRequest() {
		// TODO for kryo, create serialiser
	}

	public IdentifiableRequest(final long id) {
		this(id, ArgumentValues.none());
	}

	public IdentifiableRequest(final long id, final ArgumentValues argValues) {
		this.id = id;
		this.argValues = argValues;
	}

	/**
	 * @return the id of this request
	 */
	public final long getId() {
		return id;
	}

	/**
	 * @return the {@link ArgumentValues} of this request
	 */
	public final ArgumentValues getArgValues() {
		return argValues;
	}

	public final void setId(final long id) {
		this.id = id;
	}

	public final void setArgValues(final ArgumentValues argVals) {
		this.argValues = argVals;
	}

}
