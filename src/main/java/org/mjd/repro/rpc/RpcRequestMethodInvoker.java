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
	 * Invoke the given {@code request} and return the result.
	 *
	 * @param request the {@link RpcRequest} to invoke
	 * @return the result of the invocation
	 * @throws InvocationException
	 */
	Object invoke(RpcRequest request) throws InvocationException;

	void changeTarget(Object newTarget);
}
