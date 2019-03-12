package org.mjd.sandbox.nio.handlers.message;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.esotericsoftware.kryo.Kryo;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.util.ArgumentValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mjd.sandbox.nio.util.kryo.KryoRpcUtils.objectToKryoBytes;

public final class AsyncRpcRequestInvoker implements MessageHandler<RpcRequest> {
	private static final Logger LOG = LoggerFactory.getLogger(AsyncRpcRequestInvoker.class);
	private final Kryo kryo;
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private RpcRequestMethodInvoker methodInvoker;

	public AsyncRpcRequestInvoker(final Kryo kryo, final RpcRequestMethodInvoker rpcMethodInvoker) {
		this.kryo = kryo;
		this.methodInvoker = rpcMethodInvoker;
	}

	@Override
	public Future<Optional<ByteBuffer>> handle(final ConnectionContext<RpcRequest> conCtx, final Message<RpcRequest> message) {
		final RpcRequest request = message.getValue();
		final String requestedMethodCall = request.getMethod();
		final ArgumentValues args = request.getArgValues();
		LOG.debug("Invoking {} with args {}", requestedMethodCall, args);
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
