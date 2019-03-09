package org.mjd.sandbox.nio.async;

public interface AsyncMessageJobExecutor<MsgType> {

	void start();

	void add(AsyncMessageJob<MsgType> job);

}
