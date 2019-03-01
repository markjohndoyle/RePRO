package org.mjd.sandbox.nio.handlers.response;

import java.nio.ByteBuffer;

public interface ResponseRefiner<T> {

	ByteBuffer execute(T message, ByteBuffer result);

}
