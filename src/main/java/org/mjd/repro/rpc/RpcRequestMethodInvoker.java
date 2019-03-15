package org.mjd.repro.rpc;

import org.mjd.repro.message.RpcRequest;

/**
 * An {@link RpcRequestMethodInvoker} can take an {@link RpcRequest} message and invoke it on a target object then
 * return the resultant {@link Object}.
 *
 * How this is occurs and on what target obejct is implementation specific.
 */
public interface RpcRequestMethodInvoker {
	/**
	 * Wrapping exception that {@link RpcRequestMethodInvoker} implementations can through for circumstances
	 * the client can't deal with at runtime.
	 */
	final class InvocationException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public InvocationException(final String message, final Throwable cause) {
			super(message, cause);
		}
	}

	/**
	 * Invoke the given {@code request} and return the result.
	 *
	 * @param request the {@link RpcRequest} to invoke
	 * @return the result of the invocation
	 */
	Object invoke(RpcRequest request);

	void changeTarget(Object newTarget);
}
