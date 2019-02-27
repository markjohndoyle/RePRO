package org.mjd.sandbox.nio.handlers.response;

import java.nio.ByteBuffer;

public interface ResponseHandler<T> {

	ByteBuffer execute(T message, ByteBuffer result);

}
