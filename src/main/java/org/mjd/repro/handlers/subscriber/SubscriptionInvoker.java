package org.mjd.repro.handlers.subscriber;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

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
 * @param <T> the type of the notification
 *
 * @NotThreadSafe
 */
public final class SubscriptionInvoker<R extends RequestWithArgs, T> implements MessageHandler<R> {
	private static final Logger LOG = LoggerFactory.getLogger(SubscriptionInvoker.class);
	private final Marshaller marshaller;
	private Object subscriptionService;
	private Method registrationMethod;
	private Function<R, Object> targetSupplier;
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
		registrationMethod = getRegistrationMethod();
	}

	/**
	 * Constructs a new {@link SubscriptionInvoker} with a {@link Function} target supplier.
	 *
	 * @param marshaller     The {@link Marshaller} used to serialise responses into bytes
	 * @param targetSupplier The target supplier, in this case, a lambda that when evaluated will return the target object
	 *                       of the subscription requests. It must have one method annotated with the
	 *                       {@link SubscriptionRegistrar} annotation.
	 */
	public SubscriptionInvoker(final Marshaller marshaller, final Function<R, Object> targetSupplier) {
		this.marshaller = marshaller;
		this.targetSupplier = targetSupplier;
	}

	@Override
	public Future<Optional<ByteBuffer>> handle(final ConnectionContext<R> connectionContext, final R message) {
		if (targetSupplier != null) {
			subscriptionService = targetSupplier.apply(message);
			registrationMethod = getRegistrationMethod();
		}
		return executor.submit(() -> {
			final RequestWithArgs subscriptionRequest = message;
			try {
				LOG.debug("Invoking subscription for ID '{}' with args {}", subscriptionRequest.getId(),
						subscriptionRequest.getArgValues());
				final SubscriptionWriter<R, T> subscriptionWriter = new SubscriptionWriter<>(marshaller,
						connectionContext.getKey(), connectionContext.getWriter(), message);
				Object[] arguments = getArgumentsArray(message, subscriptionWriter);
				MethodUtils.invokeMethod(subscriptionService, registrationMethod.getName(), arguments);
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

	private Method getRegistrationMethod() {
		final Method[] registrationMethods = MethodUtils.getMethodsWithAnnotation(subscriptionService.getClass(),
				SubscriptionRegistrar.class);
		if (registrationMethods.length != 1) {
			throw new IllegalArgumentException(
					"SubscriptionInvoker requires the RPC target providing the subscription service has 1 method "
							+ "annotated on RPC target " + subscriptionService.getClass() + " with "
							+ SubscriptionRegistrar.class.getName() + " for subscription calls");
		}
		return registrationMethods[0];
	}

	private Object[] getArgumentsArray(final R message, final SubscriptionWriter<R, T> subscriptionWriter) {
		List<Object> arguments = new ArrayList<>();
		arguments.add(subscriptionWriter);
		arguments.addAll(Arrays.asList(message.getArgValues()));
		return arguments.toArray();
	}
}
