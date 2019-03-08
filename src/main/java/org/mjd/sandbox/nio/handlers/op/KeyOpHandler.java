package org.mjd.sandbox.nio.handlers.op;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public interface KeyOpHandler {

	void handle(SelectionKey key) throws IOException;

}