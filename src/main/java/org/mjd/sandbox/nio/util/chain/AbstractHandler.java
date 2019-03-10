package org.mjd.sandbox.nio.util.chain;

public abstract class AbstractHandler<R> implements Handler<R> {

	private Handler<R> next;

	protected void passOnToNextHandler(final R request) {
		if(next != null) {
			next.handle(request);
		}
	}

	public AbstractHandler<R> setNext(final Handler<R> nextHandler) {
		this.next = nextHandler;
		return this;
	}
}
