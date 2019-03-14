package org.mjd.repro.handlers.response;

import java.nio.ByteBuffer;

public interface ResponseRefiner<T> {

	ByteBuffer execute(T message, ByteBuffer result);

}
