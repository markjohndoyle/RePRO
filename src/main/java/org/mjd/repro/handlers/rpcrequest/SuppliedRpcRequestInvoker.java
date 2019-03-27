package org.mjd.repro.handlers.rpcrequest;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

import com.esotericsoftware.kryo.Kryo;
import org.mjd.repro.handlers.message.MessageHandler;
import org.mjd.repro.handlers.message.ResponseMessage;
import org.mjd.repro.message.Message;
import org.mjd.repro.message.RpcRequest;
import org.mjd.repro.rpc.RpcRequestMethodInvoker;
import org.mjd.repro.util.kryo.KryoPool;

import static org.mjd.repro.util.kryo.KryoRpcUtils.objectToKryoBytes;

// TODO move kryo serialisation to strategy
public final class SuppliedRpcRequestInvoker<R extends RpcRequest> implements MessageHandler<R> {
	private final RpcRequestMethodInvoker methodInvoker;
	private final ExecutorService executor;
	private final KryoPool kryos;
	private Function<R, Object> rpcTargetSupplier;

	public SuppliedRpcRequestInvoker(final ExecutorService executor, final KryoPool kryos,
			final RpcRequestMethodInvoker rpcMethodInvoker, final Function<R, Object> supplier) {
		this.executor = executor;
		this.kryos = kryos;
		this.methodInvoker = rpcMethodInvoker;
		this.rpcTargetSupplier = supplier;
	}

	@Override
	public Future<Optional<ByteBuffer>> handle(final ConnectionContext<R> connectionContext, final Message<R> message) {
		return executor.submit(() -> {
			methodInvoker.changeTarget(rpcTargetSupplier.apply(message.getValue()));
			// ^ Maybe add a caching option users can configure so we don't need to set this every call.
			ResponseMessage<Object> responseMessage;
			try {
				final Object result = methodInvoker.invoke(message.getValue());
				responseMessage = new ResponseMessage<>(message.getValue().getId(), result);
			}
			catch (final RpcRequestMethodInvoker.InvocationException e) {
				responseMessage = new ResponseMessage<>(message.getValue().getId(), e.getCause());
			}
			final Kryo kryo = kryos.obtain();
			final byte[] msgBytes = objectToKryoBytes(kryo, responseMessage);
			kryos.free(kryo);
			return Optional.of(ByteBuffer.wrap(msgBytes));
		});
	}
}
