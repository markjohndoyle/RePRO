package org.mjd.sandbox.nio.util.chain;

public interface Handler<R> {

	void handle(R request);

}
