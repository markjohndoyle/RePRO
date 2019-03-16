package org.mjd.repro.util.kryo;

import java.nio.ByteBuffer;
import java.util.Optional;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import org.mjd.repro.handlers.message.MessageHandler.HandlerException;
import org.mjd.repro.handlers.message.ResponseMessage;
import org.mjd.repro.message.Request;
import org.mjd.repro.message.RequestWithArgs;
import org.mjd.repro.message.RpcRequest;
import org.mjd.repro.util.kryo.RpcRequestKryoPool.ByteBufferSerializer;

public final class RpcKryo {
	private RpcKryo() {
		// functional class
	}

	public static Kryo configure(final Kryo kryo) {
		kryo.addDefaultSerializer(java.lang.Throwable.class, new JavaSerializer());
		kryo.register(Request.class);
		kryo.register(RequestWithArgs.class);
		kryo.register(RpcRequest.class);
		kryo.register(ResponseMessage.class, new ResponseMessage.ResponseMessageSerialiser());
		kryo.register(HandlerException.class);
		kryo.register(Optional.class);
		kryo.register(ByteBuffer.allocate(0).getClass(), new ByteBufferSerializer());
		kryo.register(Object.class);
		return kryo;
	}
}
