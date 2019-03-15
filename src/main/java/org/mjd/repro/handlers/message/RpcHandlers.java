package org.mjd.repro.handlers.message;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;

import com.esotericsoftware.kryo.Kryo;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.mjd.repro.message.RpcRequest;
import org.mjd.repro.rpc.ReflectionInvoker;

/**
 * Factory and utility methods for RPC based {@link MessageHandler}s.
 */
public final class RpcHandlers {
	private RpcHandlers() {
		// static factories holder class
	}

	/**
	 * Creates a new {@link RpcRequestInvoker} that invokers {@link RpcRequest} methods using reflection in the current
	 * thread.
	 *
	 * Responses are serialised using the given {@link Kryo} instance.
	 *
	 * @param kryo      the {@link Kryo} used to serialise responses.
	 * @param rpcTarget the Obejct to execute methods upon.
	 * @return {@link MessageHandler} for {@link RpcRequest} messages.
	 */
	public static <R extends RpcRequest> MessageHandler<R>
	directRpcInvoker(final Kryo kryo, final Object rpcTarget) {
		return new RpcRequestInvoker<>(MoreExecutors.newDirectExecutorService(), kryo, new ReflectionInvoker(rpcTarget));
	}

	/**
	 * Creates a new {@link RpcRequestInvoker} that invokers {@link RpcRequest} methods using reflection in a single
	 * threaded {@link ExecutorService}.
	 *
	 * Responses are serialised using the given {@link Kryo} instance.
	 *
	 * @param kryo      the {@link Kryo} used to serialise responses.
	 * @param rpcTarget the Obejct to execute methods upon.
	 * @return {@link MessageHandler} for {@link RpcRequest} messages.
	 */
	public static <R extends RpcRequest> MessageHandler<R>
	singleThreadRpcInvoker(final Kryo kryo, final Object rpcTarget) {
		final ThreadFactory nameFactory = new ThreadFactoryBuilder().setNameFormat(RpcRequestInvoker.class.getName()).build();
		return new RpcRequestInvoker<>(Executors.newSingleThreadExecutor(nameFactory), kryo, new ReflectionInvoker(rpcTarget));
	}

	/**
	 * Creates a new {@link RpcRequestInvoker} that invokers {@link RpcRequest} methods using reflection in a fixed size
	 * thread pool.
	 *
	 * Responses are serialised using the given {@link Kryo} instance.
	 *
	 * @param kryo        the {@link Kryo} used to serialise responses.
	 * @param rpcTarget   the Obejct to execute methods upon.
	 * @param threadCount the number of threads to use in the thread pool
	 * @return {@link MessageHandler} for {@link RpcRequest} messages.
	 */
	public static <R extends RpcRequest> MessageHandler<R>
	newFixedThreadRpcInvoker(final Kryo kryo, final Object rpcTarget,
			final int threadCount) {
		final ThreadFactory nameFactory = new ThreadFactoryBuilder().setNameFormat(RpcRequestInvoker.class.getName()).build();
		return new RpcRequestInvoker<>(Executors.newFixedThreadPool(threadCount, nameFactory), kryo,
				new ReflectionInvoker(rpcTarget));
	}

	public static <R extends RpcRequest> MessageHandler<R>
	newFixedThreadRpcInvoker(final Kryo kryo, final int threadCount, final Function<R, Object> rpcTargetSupplier) {
		final ThreadFactory nameFactory = new ThreadFactoryBuilder().setNameFormat(RpcRequestInvoker.class.getName()).build();
		return new SuppliedRpcRequestInvoker<>(Executors.newFixedThreadPool(threadCount, nameFactory), kryo,
				new ReflectionInvoker(), rpcTargetSupplier);
	}
}
