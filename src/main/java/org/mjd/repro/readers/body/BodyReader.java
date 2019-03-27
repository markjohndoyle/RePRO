package org.mjd.repro.readers.body;

import java.nio.ByteBuffer;

public interface BodyReader<T> {

	ByteBuffer read(ByteBuffer bodyBuffer);

	boolean isComplete();

	T getMessage();

	void setBodySize(int size);

}
