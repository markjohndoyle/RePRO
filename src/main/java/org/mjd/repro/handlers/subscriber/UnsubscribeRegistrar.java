package org.mjd.repro.handlers.subscriber;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.mjd.repro.handlers.subscriber.SubscriptionRegistrar.Subscriber;

/**
 * The {@link UnsubscribeRegistrar} annotation marks a method as capable of deregistering a {@link Subscriber}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UnsubscribeRegistrar {
}
