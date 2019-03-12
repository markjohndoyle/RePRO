package org.mjd.sandbox.nio.handlers.message;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.mjd.sandbox.nio.Server;
import org.mjd.sandbox.nio.message.IdentifiableRequest;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;
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
public final class SubscriptionInvoker implements MessageHandler<IdentifiableRequest> {
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionInvoker.class);
	private final Pool<Kryo> kryos;
	private final Kryo kryo;
	private final Object subscriptionService;
	private final Method registrationMethod;
	final ExecutorService executor = MoreExecutors.newDirectExecutorService();

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
	public SubscriptionInvoker(final Pool<Kryo> kryos, final Object rpcTarget) {
		this.kryos = kryos;
		this.kryo = kryos.obtain();
		this.subscriptionService = rpcTarget;
		final Method[] registrationMethods = MethodUtils.getMethodsWithAnnotation(rpcTarget.getClass(), SubscriptionRegistrar.class);
		if (registrationMethods.length != 1) {
			throw new IllegalStateException(
					"SubscriptionInvoker requires the RPC target providing the subscription service has 1 method "
					+ "annotated on RPC target " + rpcTarget.getClass() + " with "
					+ SubscriptionRegistrar.class.getName() + " for subscription calls");
		}
		registrationMethod = registrationMethods[0];
	}

	@Override
	public Future<Optional<ByteBuffer>> handle(final ConnectionContext<IdentifiableRequest> connectionContext,
									   final Message<IdentifiableRequest> message) {
		return executor.submit(() -> {
			final IdentifiableRequest subscriptionRequest = message.getValue();
			try {
				LOG.debug("Invoking subscription for ID '{}' with args {}", subscriptionRequest.getId(), subscriptionRequest.getArgValues());
				final SubscriptionWriter<IdentifiableRequest> subscriptionWriter =
						new SubscriptionWriter<>(kryos, connectionContext.key, connectionContext.writer, message);
				MethodUtils.invokeMethod(subscriptionService, registrationMethod.getName(), subscriptionWriter);
				return Optional.empty();
			}
			catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException ex) {
				LOG.error("Error invoking subscription", ex);
				final ResponseMessage<Object> responseMessage = new ResponseMessage<>(new HandlerException("Error invoking " + subscriptionRequest, ex));
				byte[] returnBuffer;
				try {
					returnBuffer = objectToKryoBytes(kryo, responseMessage);
					return Optional.of(ByteBuffer.wrap(returnBuffer));
				}
				catch (final IOException e) {
					LOG.error("Game over; error serialising the error. TODO Figure out what to do here.");
					return Optional.empty();
				}
			}
		});
	}

//	@Override
//	public void receive(final String notification) {
//		try {
//			final ResponseMessage<Object> responseMessage = new ResponseMessage<>(notification);
//			final ByteBuffer resultByteBuffer = ByteBuffer.wrap(objectToKryoBytes(kryo, responseMessage));
//			resultByteBuffer.position(resultByteBuffer.limit());
////			connectionContext.writer.receive(connectionContext.key, subscriptionRequest, Optional.of(resultByteBuffer));
//			connectionContext.writer.writeResult(connectionContext.key, responseMessage, resultByteBuffer);
//		}
//		catch (IOException e) {
//			LOG.error("Error notifying server of subscription message.", e);
//		}
//	}
}
