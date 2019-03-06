package org.mjd.sandbox.nio.handlers.response.provided;

import java.nio.ByteBuffer;

import org.mjd.sandbox.nio.message.RpcRequest;

public final class RpcRequestRefiners {

	public static final Prepend prepend = new Prepend();

	private RpcRequestRefiners() {
		// Util/function class
	}

	public static final class Prepend {
		/**
		 * Prepends the RPC request ID to the given buffer.
		 *
		 * @param rpcRequest
		 * @param buffer
		 * @return
		 */
		public ByteBuffer requestId(RpcRequest rpcRequest, ByteBuffer buffer) {
			return ByteBuffer.allocate(Long.BYTES + buffer.capacity()).putLong(rpcRequest.getId()).put(buffer);
		}
	}
}
