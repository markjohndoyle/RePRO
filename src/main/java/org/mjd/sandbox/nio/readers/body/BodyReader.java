package org.mjd.sandbox.nio.readers.body;

import java.nio.ByteBuffer;

import org.mjd.sandbox.nio.message.Message;

public interface BodyReader<T> {

	ByteBuffer read(ByteBuffer bodyBuffer);

	boolean isComplete();

	Message<T> getMessage();

	void setBodySize(int size);

}
