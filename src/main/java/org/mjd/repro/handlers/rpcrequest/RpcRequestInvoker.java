package org.mjd.repro.handlers.rpcrequest;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.mjd.repro.handlers.message.MessageHandler;
import org.mjd.repro.handlers.message.ResponseMessage;
import org.mjd.repro.message.RpcRequest;
import org.mjd.repro.rpc.InvocationException;
import org.mjd.repro.rpc.RpcRequestMethodInvoker;
import org.mjd.repro.serialisation.Marshaller;

// TODO move kryo serialisation to strategy
public final class RpcRequestInvoker<R extends RpcRequest> implements MessageHandler<R> {
	private final Marshaller marshaller;
	private final RpcRequestMethodInvoker methodInvoker;
	private final ExecutorService executor;

	public RpcRequestInvoker(final ExecutorService executor, final Marshaller marshaller,
			final RpcRequestMethodInvoker rpcMethodInvoker) {
		this.executor = executor;
		this.marshaller = marshaller;
		this.methodInvoker = rpcMethodInvoker;
	}

	@Override
	public Future<Optional<ByteBuffer>> handle(final ConnectionContext<R> connectionContext, final R message) {
		return executor.submit(() -> {
			ResponseMessage<Object> responseMessage;
			try {
				final Object result = methodInvoker.invoke(message);
				responseMessage = new ResponseMessage<>(message.getId(), result);
			}
			catch (final InvocationException e) {
				responseMessage = new ResponseMessage<>(message.getId(), e.getCause());
			}
			return Optional.of(ByteBuffer.wrap(marshaller.marshall(responseMessage, ResponseMessage.class)));
		});
	}
}
