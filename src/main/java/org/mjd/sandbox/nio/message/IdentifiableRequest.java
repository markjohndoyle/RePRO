package org.mjd.sandbox.nio.message;

import java.io.Serializable;

import org.mjd.sandbox.nio.util.ArgumentValues;

public class IdentifiableRequest implements Serializable {
	private static final long serialVersionUID = 1L;

	private long id;
	private ArgumentValues argValues = new ArgumentValues();

	public IdentifiableRequest() {
		// TODO for kryo, create serialiser
	}

	public IdentifiableRequest(long id) {
		this(id, ArgumentValues.none());
	}

	public IdentifiableRequest(long id, ArgumentValues argValues) {
		this.id = id;
		this.argValues = argValues;
	}

	public long getId() {
		return id;
	}

	public ArgumentValues getArgValues() {
		return argValues;
	}

	public void setId(long id) {
		this.id = id;
	}

	public void setArgValues(ArgumentValues argVals) {
		this.argValues = argVals;
	}

}
