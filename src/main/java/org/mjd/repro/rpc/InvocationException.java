package org.mjd.repro.rpc;

/**
 * Wrapping exception that {@link RpcRequestMethodInvoker} implementations can throw for circumstances
 * the client can't deal with at runtime.
 */
public final class InvocationException extends Exception {
	private static final long serialVersionUID = 1L;

	public InvocationException(final String message, final Throwable cause) {
		super(message, cause);
	}
}