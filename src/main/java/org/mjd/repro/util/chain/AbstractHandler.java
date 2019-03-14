package org.mjd.repro.util.chain;

public abstract class AbstractHandler<R> implements Handler<R> {

	private Handler<R> next;

	protected final void passOnToNextHandler(final R request) {
		if(next != null) {
			next.handle(request);
		}
	}

	public final AbstractHandler<R> setNext(final Handler<R> nextHandler) {
		this.next = nextHandler;
		return this;
	}
}
