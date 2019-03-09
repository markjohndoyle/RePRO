package org.mjd.sandbox.nio.message;

import org.mjd.sandbox.nio.util.ArgumentValues;

/**
 * @author mark
 *
 */
public final class RpcRequest extends IdentifiableRequest {
	private static final long serialVersionUID = 3320910799863854768L;

	// TEMP USE CASE for development - reposnse object are not yet implemented.
	public static final IdentifiableRequest ERROR = new RpcRequest(-1, "error");

	private String method;

	public RpcRequest() {
		// TODO Auto-generated constructor stub
		// TODO For Kryo, will create serialisers
	}

	public RpcRequest(long id, String method) {
		this(id, method, ArgumentValues.none());
	}

	public RpcRequest(long id, String method, ArgumentValues argVals) {
		super(id, argVals);
		this.method = method;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	@Override
	public String toString() {
		return "RpcRequest [id=" + getId() + ", method=" + method + ", argValues=" + getArgValues() + "]";
	}


}
