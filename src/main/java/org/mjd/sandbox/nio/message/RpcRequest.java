package org.mjd.sandbox.nio.message;

import java.io.Serializable;

import org.mjd.sandbox.nio.util.ArgumentValues;

/**
 * @author mark
 *
 */
public final class RpcRequest implements Serializable {
	private static final long serialVersionUID = 1L;

	// TEMP USE CASE for development - reposnse object are not yet implemented.
	public static final RpcRequest ERROR = new RpcRequest(-1, "error");

	private long id;
	private String method;
	private ArgumentValues argValues = new ArgumentValues();

	public RpcRequest() {
		// TODO Auto-generated constructor stub
	}

	public RpcRequest(long id, String method) {
		this.id = id;
		this.method = method;
	}

	public RpcRequest(long id, String method, ArgumentValues argVals) {
		this.id = id;
		this.method = method;
		this.argValues = argVals;
	}

	public long getId() {
		return id;
	}

	public String getMethod() {
		return method;
	}

	public ArgumentValues getArgValues() {
		return argValues;
	}

	public void setId(long id) {
		this.id = id;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public void setArgValues(ArgumentValues argVals) {
		this.argValues = argVals;
	}

	@Override
	public String toString() {
		return "RpcRequest [id=" + id + ", method=" + method + ", argValues=" + argValues + "]";
	}


}
