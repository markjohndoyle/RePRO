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

public final class AsyncRpcRequestInvoker implements AsyncMessageHandler<RpcRequest> {
	private static final Logger LOG = LoggerFactory.getLogger(AsyncRpcRequestInvoker.class);
	private final Kryo kryo;
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private RpcRequestMethodInvoker methodInvoker;

	public AsyncRpcRequestInvoker(Kryo kryo, RpcRequestMethodInvoker rpcMethodInvoker) {
		this.kryo = kryo;
		this.methodInvoker = rpcMethodInvoker;
	}

	@Override
	public Future<Optional<ByteBuffer>> handle(Message<RpcRequest> message) {
		RpcRequest request = message.getValue();
		String requestedMethodCall = request.getMethod();
		ArgumentValues args = request.getArgValues();
		LOG.debug("Invoking {} with args {}", requestedMethodCall, args);
		return executor.submit(() -> {
			byte[] msgBytes;
			Object result = methodInvoker.invoke(message.getValue());
			if (result == null) {
				return Optional.empty();
			}
			msgBytes = objectToKryoBytes(kryo, result);
			return Optional.of(ByteBuffer.allocate(msgBytes.length).put(msgBytes));
		});
	}
}


