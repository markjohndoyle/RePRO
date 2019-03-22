package org.mjd.repro.util.chain;

public final class ProtocolChain<P> implements Handler<P> {

	private AbstractHandler<P> firstHandler;
	private AbstractHandler<P> lastHandler;

	public ProtocolChain<P> add(final AbstractHandler<P> handler) {
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
	public void handle(final P key) {
		firstHandler.handle(key);
	}
}
