package org.mjd.repro.message;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class RequestMessage<R extends RequestWithArgs> implements Message<R> {
	private final R request;
	private ByteBuffer byteBuffer;

	public RequestMessage(final R request) throws IOException {
		this.request = request;
	}

	@Override
	public R getValue() {
		return request;
	}

	@Override
	public int size() {
		return byteBuffer.capacity();
	}

	@Override
	public String toString() {
		return "RpcRequestMessage [request=" + request + ", byteBuffer=" + byteBuffer + "]";
	}

}
