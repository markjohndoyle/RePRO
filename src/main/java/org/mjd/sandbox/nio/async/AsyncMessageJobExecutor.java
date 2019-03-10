package org.mjd.sandbox.nio.async;

import java.nio.channels.Selector;

/**
 * {@link AsyncMessageJobExecutor}s processes {@link AsyncMessageJob}s once it has been started.
 *
 * The {@link AsyncMessageJobExecutor} mus tbe able to accept new jobs at any time.
 *
 * @param <MsgType> the type of {@link AsyncMessageJob} instances this processes
 */
public interface AsyncMessageJobExecutor<MsgType> {

	/**
	 * Starts the executor with the given selector. Once started, this executor will begin processing jobs as per the
	 * implementation
	 *
	 * @param selector {@link Selector} in use by the server. Executors are free to use this, for example, if they
	 * 				   need to wake up the Selector.
	 */
	void start(Selector selector);

	/**
	 * Adds a new {@link AsyncMessageJob} to this exectutor with the assumption it will be processed in the future
	 * as per the implementation.
	 *
	 * @param job {@link AsyncMessageJob} to add.
	 */
	void add(AsyncMessageJob<MsgType> job);

}
