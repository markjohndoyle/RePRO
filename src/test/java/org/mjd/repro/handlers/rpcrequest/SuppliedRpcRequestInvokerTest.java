package org.mjd.repro.handlers.rpcrequest;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import com.google.common.util.concurrent.MoreExecutors;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.handlers.message.MessageHandler;
import org.mjd.repro.handlers.message.ResponseMessage;
import org.mjd.repro.message.RpcRequest;
import org.mjd.repro.rpc.InvocationException;
import org.mjd.repro.rpc.RpcRequestMethodInvoker;
import org.mjd.repro.serialisation.Marshaller;
import org.mjd.repro.support.KryoMarshaller;
import org.mjd.repro.support.KryoPool;
import org.mjd.repro.support.KryoRpcUtils;
import org.mjd.repro.support.RpcKryo;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(OleasterRunner.class)
public class SuppliedRpcRequestInvokerTest {
	private static final String RPC_TARGET = "ThePhoenixProject";

	@Mock private RpcRequestMethodInvoker mockRpcInvoker;

	private final KryoPool kryos = new KryoPool(true, 10, RpcKryo::configure,
														  (k) -> {
														  	k.register(IllegalStateException.class);
														  	return k;
														  });

	private final Marshaller marshaller = new KryoMarshaller(10, RpcKryo::configure,
																 (k) -> {
																     k.register(IllegalStateException.class);
																  	 return k;
																 });
	private final Function<RpcRequest, Object> targetSupplier = (req) -> RPC_TARGET;
	private final ExecutorService mockExecutor = MoreExecutors.newDirectExecutorService();

	private MessageHandler.ConnectionContext<RpcRequest> mockConnCtx;
	private SuppliedRpcRequestInvoker<RpcRequest> invokerUnderTest;

	// TEST INSTANCE BLOCK
	{
		before(() -> {
			MockitoAnnotations.initMocks(this);
		});

		describe("A SuppliedRpcRequestInvoker", () -> {
			beforeEach(() -> {
				invokerUnderTest = new SuppliedRpcRequestInvoker<>(mockExecutor, marshaller, mockRpcInvoker, targetSupplier);
			});
			describe("receives a valid rcp request", () -> {
				describe("that executes successfully", () -> {
					before(() -> {
						when(mockRpcInvoker.invoke(any(RpcRequest.class))).thenReturn(RPC_TARGET.length());
					});
					it("should return a ByteBuffer of a ResponseMessage where the message contains the correct value", () -> {
						final RpcRequest testMessage = new RpcRequest(0L, "length");
						final Optional<ByteBuffer> actual = invokerUnderTest.handle(mockConnCtx, testMessage).get();
						expect(actual.get()).toBeNotNull();
						final ResponseMessage<Integer> actualRspMsg = KryoRpcUtils.readBytesWithKryo(kryos.obtain(), actual.get().array(), ResponseMessage.class);
						expect(actualRspMsg.isError()).toBeFalse();
						expect(actualRspMsg.getId()).toEqual(0L);
						expect(actualRspMsg.getValue()).toBeNotNull();
						expect(actualRspMsg.getValue().get()).toEqual(RPC_TARGET.length());
					});
				});
				describe("that throws when executes", () -> {
					before(() -> {
						final InvocationException ex = new InvocationException("blah", new IllegalStateException());
						when(mockRpcInvoker.invoke(any(RpcRequest.class))).thenThrow(ex);
					});
					it("should return a ByteBuffer of a ResponseMessage where the message contains the correct value", () -> {
						final RpcRequest testMessage = new RpcRequest(0L, "length");
						final Optional<ByteBuffer> actual = invokerUnderTest.handle(mockConnCtx, testMessage).get();
						expect(actual.get()).toBeNotNull();
						final ResponseMessage<Integer> actualRspMsg = KryoRpcUtils.readBytesWithKryo(kryos.obtain(), actual.get().array(), ResponseMessage.class);
						expect(actualRspMsg.getId()).toEqual(0L);
						expect(actualRspMsg.isError()).toBeTrue();
						expect(actualRspMsg.getValue().isPresent()).toBeFalse();
						expect(actualRspMsg.getError().get()).toBeInstanceOf(IllegalStateException.class);
					});
				});
			});
		});
	}
}
