package org.mjd.sandbox.nio.handlers.op;

import org.mjd.sandbox.nio.util.chain.AbstractHandler;
import org.mjd.sandbox.nio.util.chain.Handler;

public final class KeyOpProtocol<SelectionKey> implements Handler<SelectionKey> {

	private AbstractHandler<SelectionKey> firstHandler;
	private AbstractHandler<SelectionKey> lastHandler;

	public KeyOpProtocol<SelectionKey> add(final AbstractHandler<SelectionKey> handler) {
		if(firstHandler == null) {
			firstHandler = handler;
			lastHandler = handler;
		}
		else {
			lastHandler.setNext(handler);
			lastHandler = handler;
		}
		return this;
	}

	@Override
	public void handle(final SelectionKey key) {
		firstHandler.handle(key);
	}

}
