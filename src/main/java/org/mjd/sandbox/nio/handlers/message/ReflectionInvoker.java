package org.mjd.sandbox.nio.handlers.message;

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.mjd.sandbox.nio.handlers.message.MessageHandler.HandlerException;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.util.ArgumentValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReflectionInvoker implements RpcRequestMethodInvoker {
	private static final Logger LOG = LoggerFactory.getLogger(ReflectionInvoker.class);
	private final Object rpcTarget;

	public ReflectionInvoker(Object rpcTarget) {
		this.rpcTarget = rpcTarget;
	}

	@Override
	public final Object invoke(RpcRequest request) {
		try {
			String requestedMethodCall = request.getMethod();
			ArgumentValues args = request.getArgValues();
			LOG.debug("Invoking {} with args {}", requestedMethodCall, args);
			return MethodUtils.invokeMethod(rpcTarget, requestedMethodCall, args.asObjArray());
		}
		catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException ex) {
			throw new HandlerException("Error invoking " + request, ex);
		}
	}
}
