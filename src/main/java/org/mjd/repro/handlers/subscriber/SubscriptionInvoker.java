package org.mjd.repro.handlers.subscriber;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.google.common.util.concurrent.MoreExecutors;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.mjd.repro.handlers.message.MessageHandler;
import org.mjd.repro.handlers.message.ResponseMessage;
import org.mjd.repro.message.RequestWithArgs;
import org.mjd.repro.serialisation.Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SubscriptionInvoker} will register your client for notifications on the rpcTarget Object using the
 * identifier passed in the {@link RequestWithArgs} message. The {@link SubscriptionInvoker} looks up the registration
 * method on the rpcTarget denoted by the {@link SubscriptionRegistrar} annotation. A Server using this
 * {@link MessageHandler} would be restricted as a subscription service.
 *
 * @param <R> the type of {@link RequestWithArgs} message
 *
 * @NotThreadSafe
 */
public final class SubscriptionInvoker<R extends RequestWithArgs> implements MessageHandler<R> {
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionInvoker.class);
	private final Marshaller marshaller;
	private final Object subscriptionService;
	private final Method registrationMethod;
	private final ExecutorService executor = MoreExecutors.newDirectExecutorService();

	/**
	 * Constructs a fully initialised {@link SubscriptionInvoker}, it is ready to use after this constructor completes.
	 *
	 * @param marshaller The {@link Marshaller} used to serialise responses into bytes
	 * @param rpcTarget  The RPC target, in this case, the target of subscription requests. It must have one method
	 *                   annotated with the {@link SubscriptionRegistrar} annotation.
	 */
	public SubscriptionInvoker(final Marshaller marshaller, final Object rpcTarget) {
		this.marshaller = marshaller;
		this.subscriptionService = rpcTarget;
		final Method[] registrationMethods = MethodUtils.getMethodsWithAnnotation(subscriptionService.getClass(),
				SubscriptionRegistrar.class);
		if (registrationMethods.length != 1) {
			throw new IllegalArgumentException(
					"SubscriptionInvoker requires the RPC target providing the subscription service has 1 method "
							+ "annotated on RPC target " + subscriptionService.getClass() + " with "
							+ SubscriptionRegistrar.class.getName() + " for subscription calls");
		}
		this.registrationMethod = registrationMethods[0];
	}

	@Override
	public Future<Optional<ByteBuffer>> handle(final ConnectionContext<R> connectionContext, final R message) {
		return executor.submit(() -> {
			final RequestWithArgs subscriptionRequest = message;
			try {
				LOG.debug("Invoking subscription for ID '{}' with args {}", subscriptionRequest.getId(),
						subscriptionRequest.getArgValues());
				final SubscriptionWriter<R> subscriptionWriter = new SubscriptionWriter<>(marshaller,
						connectionContext.getKey(), connectionContext.getWriter(), message);
				MethodUtils.invokeMethod(subscriptionService, registrationMethod.getName(), subscriptionWriter);
				return Optional.of(
						ByteBuffer.wrap(marshaller.marshall(ResponseMessage.voidMsg(message.getId()), ResponseMessage.class)));
			}
			catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException ex) {
				LOG.error("Error invoking subscription", ex);
				final HandlerException handlerEx = new HandlerException("Error invoking " + subscriptionRequest, ex);
				final byte[] bytes = marshaller.marshall(ResponseMessage.error(subscriptionRequest.getId(), handlerEx),
						ResponseMessage.class);
				return Optional.of(ByteBuffer.wrap(bytes));
			}
		});
	}
}
