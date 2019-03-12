package org.mjd.sandbox.nio.rpc;

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.mjd.sandbox.nio.handlers.message.MessageHandler.HandlerException;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.util.ArgumentValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link RpcRequestMethodInvoker} that uses reflection to invoke an {@link RpcRequest} on the {@code rpcTarget}.
 * The rpcTarget is provided at construction time and is used for every invocation.
 *
 * @ThreadSafe However, the rpcTarget may not be threadsafe depending upon the method invoked.
 */
public class ReflectionInvoker implements RpcRequestMethodInvoker {
	private static final Logger LOG = LoggerFactory.getLogger(ReflectionInvoker.class);
	private final Object rpcTarget;

	/**
	 * Constructs a fully initialised {@link ReflectionInvoker} for the given {@code rpcTarget}
	 *
	 * @param rpcTarget the object to invoke the methods upon
	 */
	public ReflectionInvoker(final Object rpcTarget) {
		this.rpcTarget = rpcTarget;
	}

	@Override
	public final Object invoke(final RpcRequest request) {
		try {
			final String requestedMethodCall = request.getMethod();
			final ArgumentValues args = request.getArgValues();
			LOG.debug("Invoking {} with args {}", requestedMethodCall, args);
			return MethodUtils.invokeMethod(rpcTarget, requestedMethodCall, args.asObjArray());
		}
		catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException ex) {
			throw new InvocationException("Error invoking " + request, ex);
		}
	}
}
