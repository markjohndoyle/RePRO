package org.mjd.repro.handlers.routing;

import java.nio.channels.SelectionKey;

public interface MessageHandlerRouter<MsgType> {

	void routetoHandler(SelectionKey key, MsgType message);
}
