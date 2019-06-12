package org.mjd.repro.handlers.response.provided;

import java.nio.ByteBuffer;

import org.mjd.repro.message.RequestWithArgs;

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
		public ByteBuffer requestId(RequestWithArgs rpcRequest, ByteBuffer buffer) {
			byte[] idBytes = rpcRequest.getId().getBytes();
			final ByteBuffer refinerBuffer = ByteBuffer.allocate(Integer.BYTES + idBytes.length + buffer.capacity());
			refinerBuffer.putInt(idBytes.length);
			refinerBuffer.put(idBytes);
			refinerBuffer.put(buffer);

			return refinerBuffer;
		}
	}
}
