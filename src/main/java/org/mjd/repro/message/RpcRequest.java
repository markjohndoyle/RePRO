package org.mjd.repro.message;

import org.mjd.repro.util.ArgumentValues;

/**
 * @author mark
 *
 */
public class RpcRequest extends IdentifiableRequest {
	private static final long serialVersionUID = 3320910799863854768L;

	private String method;

	public RpcRequest() {
		// TODO Auto-generated constructor stub
		// TODO For Kryo, will create serialisers
	}

	public RpcRequest(final long id, final String method) {
		this(id, method, ArgumentValues.none());
	}

	public RpcRequest(final long id, final String method, final ArgumentValues argVals) {
		super(id, argVals);
		this.method = method;
	}

	public final String getMethod() {
		return method;
	}

	public final void setMethod(final String method) {
		this.method = method;
	}

	@Override
	public final String toString() {
		return "RpcRequest [id=" + getId() + ", method=" + method + ", argValues=" + getArgValues() + "]";
	}


}
