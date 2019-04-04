package org.mjd.repro.handlers.subscriber;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@link SubscriptionRegistrar} annotation marks a method as capacble of registering a {@link Subscriber}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubscriptionRegistrar {
	/**
	 * Interface that classes using the {@link SubscriptionRegistrar} can write notifications too.
	 */
	@FunctionalInterface
	interface Subscriber {
		void receive(String notification);
	}
}
