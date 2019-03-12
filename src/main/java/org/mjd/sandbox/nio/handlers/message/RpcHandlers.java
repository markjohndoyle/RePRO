package org.mjd.sandbox.nio.handlers.message;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.esotericsoftware.kryo.Kryo;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.mjd.sandbox.nio.message.RpcRequest;

public final class RpcHandlers {
	private RpcHandlers() {
		// static factories holder class
	}

	public static MessageHandler<RpcRequest> directRpcInvoker(final Kryo kryo, final Object rpcTarget) {
		return new RpcRequestInvoker(MoreExecutors.newDirectExecutorService(), kryo, new ReflectionInvoker(rpcTarget));
	}

	public static MessageHandler<RpcRequest> singleThreadRpcInvoker(final Kryo kryo, final Object rpcTarget) {
		final ThreadFactory nameFactory = new ThreadFactoryBuilder().setNameFormat(RpcRequestInvoker.class.getName()).build();
		return new RpcRequestInvoker(Executors.newSingleThreadExecutor(nameFactory), kryo, new ReflectionInvoker(rpcTarget));
	}
}
