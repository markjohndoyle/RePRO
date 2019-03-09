package org.mjd.sandbox.nio.async;

import java.nio.channels.Selector;

public interface AsyncMessageJobExecutor<MsgType> {

	void start(Selector selector);

	void add(AsyncMessageJob<MsgType> job);

}
