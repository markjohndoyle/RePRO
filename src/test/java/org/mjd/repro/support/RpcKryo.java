package org.mjd.repro.support;

import java.nio.ByteBuffer;
import java.util.Optional;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import org.mjd.repro.handlers.message.MessageHandler.HandlerException;
import org.mjd.repro.handlers.message.ResponseMessage;
import org.mjd.repro.message.Request;
import org.mjd.repro.message.RequestWithArgs;
import org.mjd.repro.message.RpcRequest;

public final class RpcKryo {
	private RpcKryo() {
		// functional class
	}

	public static Kryo configure(final Kryo kryo) {
		kryo.addDefaultSerializer(Throwable.class, new JavaSerializer());
		kryo.register(Request.class, new RequestSerialiser());
		kryo.register(RequestWithArgs.class, new RequestWithArgsSerialiser());
		kryo.register(RpcRequest.class, new RpcRequestSerialiser());
		kryo.register(ResponseMessage.class, new ResponseMessage.ResponseMessageSerialiser());
		kryo.register(HandlerException.class);
		kryo.register(Optional.class);
		kryo.register(ByteBuffer.allocate(0).getClass(), new ByteBufferSerializer());
		kryo.register(Object.class);
		return kryo;
	}
}
