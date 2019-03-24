package org.mjd.repro.util.chain;

/**
 * Functional interface that performs some action. The function will receive a parameter of type R.
 *
 * THis may seem rather abstract, this is because it's part of an implementation of the Chain of Responsibility
 * pattern.
 *
 * @param <R> the type of requests this Handler handles.
 */
@FunctionalInterface
public interface Handler<R> {

	/**
	 * Performs some action when given the request.
	 *
	 * @param request the request to handle
	 */
	void handle(R request);

}
