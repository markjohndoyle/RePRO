package org.mjd.sandbox.nio.handlers.message;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Optional;

import com.esotericsoftware.kryo.Kryo;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.mjd.sandbox.nio.Server;
import org.mjd.sandbox.nio.handlers.message.SubscriptionRegistrar.Subscriber;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.util.kryo.KryoRpcUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mjd.sandbox.nio.util.kryo.KryoRpcUtils.objectToKryoBytes;

/**
 * A {@link SubscriptionInvoker} will register your client for notifications on the rpcTarget Object using the
 * in the {@link RpcRequest}, that is, {@link RpcRequest#getId()}.
 * In this {@link MessageHandler} the method name in the {@link RpcRequest} is actually ignored because the
 * {@link SubscriptionInvoker} looks up the registration method on the rpcTarget denoted byt the
 * {@link SubscriptionRegistrar} annotation. This is a limited use case since every request will be a subscription.
 * A Server using this {@link MessageHandler} would be restricted as a  subscription service.
 *
 * TODO {@link Server} will be extended to handle multiple handlers and messages will be routed to the correct
 * handler based upon some kind of flag or address.
 */
public final class SubscriptionInvoker implements MessageHandler<RpcRequest>, Subscriber {
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionInvoker.class);
	private final Kryo kryo;
	private final Object subscriptionService;
	private final Method registrationMethod;
	private ConnectionContext<RpcRequest> connectionContext;
	private RpcRequest subscriptionRequest;

	/**
	 * Constrcuts a fully initialised {@link SubscriptionInvoker}, it is ready to
	 * use after this constructor completes.
	 *
	 * @param kryo      {@link Kryo} object used for serialising the notifications
	 *                  back to the server.
	 * @param rpcTarget the RPC target, in this case, the target of subscription
	 *                  requests. It must have one method annotated with the
	 *                  {@link SubscriptionRegistrar} annotation.
	 */
	public SubscriptionInvoker(Kryo kryo, Object rpcTarget) {
		this.kryo = kryo;
		this.subscriptionService = rpcTarget;
		Method[] registrationMethods = MethodUtils.getMethodsWithAnnotation(rpcTarget.getClass(), SubscriptionRegistrar.class);
		if (registrationMethods.length != 1) {
			throw new IllegalStateException(
					"SubscriptionInvoker requires the RPC target providing the subscription service has 1 method "
					+ "annotated on RPC target " + rpcTarget.getClass() + " with "
					+ SubscriptionRegistrar.class.getName() + " for subscription calls");
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
		catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException ex) {
			LOG.error("Error invoking subscription", ex);
			ResponseMessage<Object> responseMessage = new ResponseMessage<>(new HandlerException("Error invoking " + subscriptionRequest, ex));
			byte[] returnBuffer;
			try {
				returnBuffer = KryoRpcUtils.objectToKryoBytes(kryo, responseMessage);
				return Optional.of(ByteBuffer.wrap(returnBuffer));
			}
			catch (IOException e) {
				LOG.error("Game over; error serialising the error. TODO Figure out what to do here.");
				return Optional.empty();
			}
		}
	}

	@Override
	public void receive(String notification) {
		try {
			ResponseMessage<Object> responseMessage = new ResponseMessage<>(notification);
			final ByteBuffer resultByteBuffer = ByteBuffer.wrap(objectToKryoBytes(kryo, responseMessage));
			resultByteBuffer.position(resultByteBuffer.limit());
			connectionContext.server.receive(connectionContext.key, subscriptionRequest, Optional.of(resultByteBuffer));
		}
		catch (IOException e) {
			LOG.error("Error notifying server of subscription message.", e);
		}
	}
}
