package org.mjd.sandbox.nio.handlers.message;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.Optional;

import com.esotericsoftware.kryo.Kryo;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.util.ArgumentValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mjd.sandbox.nio.util.kryo.KryoRpcUtils.objectToKryoBytes;

public final class RpcRequestInvoker implements MessageHandler<RpcRequest> {
	private static final Logger LOG = LoggerFactory.getLogger(RpcRequestInvoker.class);
	private final Kryo kryo;
	private final Object rpcTarget;

	public RpcRequestInvoker(Kryo kryo, Object rpcTarget) {
		this.kryo = kryo;
		this.rpcTarget = rpcTarget;
	}

	@Override
	public Optional<ByteBuffer> handle(ConnectionContext<RpcRequest> connectionContext, Message<RpcRequest> message) {
		byte[] msgBytes;
		Object result;
		RpcRequest request = message.getValue();
		try {
			String requestedMethodCall = request.getMethod();
			ArgumentValues args = request.getArgValues();
			LOG.debug("Invoking {} with args {}", requestedMethodCall, args);
			result = MethodUtils.invokeMethod(rpcTarget, requestedMethodCall, args.asObjArray());
			if (result == null) {
				return Optional.empty();
			}
			msgBytes = objectToKryoBytes(kryo, result);
			return Optional.of(ByteBuffer.allocate(msgBytes.length).put(msgBytes));
		}
		catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | IOException ex) {
			result = new String("Error executing method: " + request.getMethod() + " due to " + ex);
			throw new HandlerException("Error invoking " + request, ex);
		}
	}
}

