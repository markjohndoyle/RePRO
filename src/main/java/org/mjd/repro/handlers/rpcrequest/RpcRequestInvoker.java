package org.mjd.repro.handlers.rpcrequest;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.esotericsoftware.kryo.Kryo;
import org.mjd.repro.handlers.message.MessageHandler;
import org.mjd.repro.handlers.message.ResponseMessage;
import org.mjd.repro.message.Message;
import org.mjd.repro.message.RpcRequest;
import org.mjd.repro.rpc.RpcRequestMethodInvoker;

import static org.mjd.repro.util.kryo.KryoRpcUtils.objectToKryoBytes;

// TODO move kryo serialisation to strategy
public final class RpcRequestInvoker<R extends RpcRequest> implements MessageHandler<R> {
	private final Kryo kryo;
	private final RpcRequestMethodInvoker methodInvoker;
	private final ExecutorService executor;

	public RpcRequestInvoker(final ExecutorService executor, final Kryo kryo, final RpcRequestMethodInvoker rpcMethodInvoker) {
		this.executor = executor;
		this.kryo = kryo;
		this.methodInvoker = rpcMethodInvoker;
	}

	@Override
	public Future<Optional<ByteBuffer>> handle(final ConnectionContext<R> connectionContext, final Message<R> message) {
		return executor.submit(() -> {
			final Object result = methodInvoker.invoke(message.getValue());
			if (result == null) {
				return Optional.empty();
			}

			final ResponseMessage<Object> responseMessage = new ResponseMessage<>(message.getValue().getId(), result);
			final byte[] msgBytes = objectToKryoBytes(kryo, responseMessage);
			return Optional.of(ByteBuffer.wrap(msgBytes));
		});
	}
}
