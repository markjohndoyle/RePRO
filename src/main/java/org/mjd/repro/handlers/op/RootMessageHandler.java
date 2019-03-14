package org.mjd.repro.handlers.op;

import java.nio.channels.SelectionKey;

import org.mjd.repro.message.Message;

public interface RootMessageHandler<MsgType> {

	void handle(SelectionKey key, Message<MsgType> message);
}
