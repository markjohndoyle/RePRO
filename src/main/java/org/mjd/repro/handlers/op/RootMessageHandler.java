package org.mjd.repro.handlers.op;

import java.nio.channels.SelectionKey;

public interface RootMessageHandler<MsgType> {

	void handle(SelectionKey key, MsgType message);
}
