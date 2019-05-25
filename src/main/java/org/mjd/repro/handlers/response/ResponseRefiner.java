package org.mjd.repro.handlers.response;

import java.nio.ByteBuffer;

@FunctionalInterface
public interface ResponseRefiner<T> {

	ByteBuffer execute(T message, ByteBuffer result);
}
