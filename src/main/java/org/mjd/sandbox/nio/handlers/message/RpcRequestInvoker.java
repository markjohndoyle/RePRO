package org.mjd.sandbox.nio.handlers.message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

import com.esotericsoftware.kryo.Kryo;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;

import static org.mjd.sandbox.nio.util.kryo.KryoRpcUtils.objectToKryoBytes;

//TODO move kryo serialisation to strategy
public final class RpcRequestInvoker implements MessageHandler<RpcRequest> {
	private final Kryo kryo;
	private final RpcRequestMethodInvoker methodInvoker;

	public RpcRequestInvoker(Kryo kryo, RpcRequestMethodInvoker rpcMethodInvoker) {
		this.kryo = kryo;
		this.methodInvoker = rpcMethodInvoker;
	}

	@Override
	public Optional<ByteBuffer> handle(ConnectionContext<RpcRequest> connectionContext, Message<RpcRequest> message) {
		byte[] msgBytes;
		Object result = methodInvoker.invoke(message.getValue());
		if (result == null) {
			return Optional.empty();
		}
		try {
			msgBytes = objectToKryoBytes(kryo, result);
			return Optional.of(ByteBuffer.allocate(msgBytes.length).put(msgBytes));
		}
		catch (IOException ex) {
			throw new HandlerException("Error invoking " + message.getValue(), ex);
		}
	}
}

