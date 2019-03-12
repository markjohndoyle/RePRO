package org.mjd.sandbox.nio.handlers.message;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.esotericsoftware.kryo.Kryo;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.rpc.RpcRequestMethodInvoker;

import static org.mjd.sandbox.nio.util.kryo.KryoRpcUtils.objectToKryoBytes;

//TODO move kryo serialisation to strategy
public final class RpcRequestInvoker implements MessageHandler<RpcRequest> {
	private final Kryo kryo;
	private final RpcRequestMethodInvoker methodInvoker;
	private final ExecutorService executor;

	public RpcRequestInvoker(final ExecutorService executor, final Kryo kryo,
							 final RpcRequestMethodInvoker rpcMethodInvoker) {
		this.executor = executor;
		this.kryo = kryo;
		this.methodInvoker = rpcMethodInvoker;
	}

	@Override
	public Future<Optional<ByteBuffer>> handle(final ConnectionContext<RpcRequest> connectionContext,
											   final Message<RpcRequest> message) {
		return executor.submit(() -> {
			final Object result = methodInvoker.invoke(message.getValue());
			if (result == null) {
				return Optional.empty();
			}
			final ResponseMessage<Object> responseMessage = new ResponseMessage<>(result);
			final byte[] msgBytes = objectToKryoBytes(kryo, responseMessage);
			return Optional.of(ByteBuffer.allocate(msgBytes.length).put(msgBytes));
		});
	}
}

