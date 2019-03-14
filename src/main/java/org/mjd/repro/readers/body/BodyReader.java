package org.mjd.repro.readers.body;

import java.nio.ByteBuffer;

import org.mjd.repro.message.Message;

public interface BodyReader<T> {

	ByteBuffer read(ByteBuffer bodyBuffer);

	boolean isComplete();

	Message<T> getMessage();

	void setBodySize(int size);

}
