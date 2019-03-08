package org.mjd.sandbox.nio.handlers.op;

import java.nio.channels.SelectionKey;

import org.mjd.sandbox.nio.message.Message;

public interface RootMessageHandler<MsgType> {

	void handle(SelectionKey key, Message<MsgType> message);
}
