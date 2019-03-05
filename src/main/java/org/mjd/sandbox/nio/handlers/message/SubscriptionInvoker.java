package org.mjd.sandbox.nio.handlers.message;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Optional;

import com.esotericsoftware.kryo.Kryo;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.mjd.sandbox.nio.handlers.message.SubscriptionRegistrar.Subscriber;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mjd.sandbox.nio.util.kryo.KryoRpcUtils.objectToKryoBytes;

public final class SubscriptionInvoker implements MessageHandler<RpcRequest>, Subscriber {
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionInvoker.class);
	private final Kryo kryo;
	private final Object subscriptionService;
	private final Method registrationMethod;
	private ConnectionContext<RpcRequest> connectionContext;
	private RpcRequest subscriptionRequest;

	public SubscriptionInvoker(Kryo kryo, Object rpcTarget) {
		this.kryo = kryo;
		this.subscriptionService = rpcTarget;
		Method[] registrationMethods = MethodUtils.getMethodsWithAnnotation(rpcTarget.getClass(), SubscriptionRegistrar.class);
		if(registrationMethods.length != 1) {
			throw new IllegalStateException("There should be 1 method annotated on RPC target "
				+ rpcTarget.getClass() + " with " + SubscriptionRegistrar.class.getName() + " for subscription calls");
		}
		registrationMethod = registrationMethods[0];
	}

	@Override
	public Optional<ByteBuffer> handle(ConnectionContext<RpcRequest> connectionContext, Message<RpcRequest> message) {
		this.connectionContext = connectionContext;
		subscriptionRequest = message.getValue();
		try {
			String requestedMethodCall = subscriptionRequest.getMethod();
			LOG.debug("Invoking subscription {} with args", requestedMethodCall);
			MethodUtils.invokeMethod(subscriptionService, registrationMethod.getName(), this);
			return Optional.empty();
		}
		catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException  ex) {
			LOG.error("Error invoking subscription", ex);
			throw new HandlerException("Error invoking " + subscriptionRequest, ex);
		}
	}

	@Override
	public void receive(String notification) {
		try {
			final ByteBuffer resultByteBuffer = ByteBuffer.wrap(objectToKryoBytes(kryo, notification));
			resultByteBuffer.position(resultByteBuffer.limit());
			connectionContext.server.receive(connectionContext.key, subscriptionRequest, Optional.of(resultByteBuffer));
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}

