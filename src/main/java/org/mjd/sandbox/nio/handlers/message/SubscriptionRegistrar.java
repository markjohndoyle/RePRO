package org.mjd.sandbox.nio.handlers.message;

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
	interface Subscriber {
		void receive(String notification);
	}
}
