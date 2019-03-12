package org.mjd.sandbox.nio.handlers.message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.esotericsoftware.kryo.Kryo;
import com.google.common.util.concurrent.MoreExecutors;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;

import static org.mjd.sandbox.nio.util.kryo.KryoRpcUtils.objectToKryoBytes;

//TODO move kryo serialisation to strategy
public final class RpcRequestInvoker implements MessageHandler<RpcRequest> {
	private final Kryo kryo;
	private final RpcRequestMethodInvoker methodInvoker;
	private final ExecutorService executor = MoreExecutors.newDirectExecutorService();

	public RpcRequestInvoker(final Kryo kryo, final RpcRequestMethodInvoker rpcMethodInvoker) {
		this.kryo = kryo;
		this.methodInvoker = rpcMethodInvoker;
	}

	@Override
	public Future<Optional<ByteBuffer>> handle(final ConnectionContext<RpcRequest> connectionContext,
											   final Message<RpcRequest> message) {
		return executor.submit(() -> {
			byte[] msgBytes;
			final Object result = methodInvoker.invoke(message.getValue());
			if (result == null) {
				return Optional.empty();
			}
			try {
				final ResponseMessage<Object> responseMessage = new ResponseMessage<>(result);
				msgBytes = objectToKryoBytes(kryo, responseMessage);
				return Optional.of(ByteBuffer.allocate(msgBytes.length).put(msgBytes));
			}
			catch (final IOException ex) {
				throw new HandlerException("Error invoking " + message.getValue(), ex);
			}
		});
	}
}

