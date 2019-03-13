package org.mjd.sandbox.nio.handlers.message;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.sandbox.nio.handlers.message.MessageHandler.ConnectionContext;
import org.mjd.sandbox.nio.handlers.message.MessageHandler.HandlerException;
import org.mjd.sandbox.nio.handlers.message.SubscriptionRegistrar.Subscriber;
import org.mjd.sandbox.nio.message.IdentifiableRequest;
import org.mjd.sandbox.nio.message.RequestMessage;
import org.mjd.sandbox.nio.util.kryo.KryoRpcUtils;
import org.mjd.sandbox.nio.util.kryo.RpcRequestKryoPool;
import org.mjd.sandbox.nio.writers.ChannelWriter;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link SubscriptionInvoker}
 */
@RunWith(OleasterRunner.class)
public class SubscriptionInvokerTest {

	@Spy private SelectionKey spyKey;
	@Mock private ChannelWriter<IdentifiableRequest, SelectionKey> mockChannelWriter;
	@Mock private FakeBroadcaster mockBroadcaster;

	private final RpcRequestKryoPool kryos = new RpcRequestKryoPool(true, false, 10);
	private SubscriptionInvoker invokerUnderTest;
	private ConnectionContext<IdentifiableRequest> fakeCtx = new ConnectionContext<>(mockChannelWriter, spyKey);

	// TEST INSTANCE BLOCK
	{
		before(()-> {
			MockitoAnnotations.initMocks(this);
			invokerUnderTest = new SubscriptionInvoker(kryos, mockBroadcaster);
		});

		describe("when the " + SubscriptionInvoker.class, () -> {
			describe("is given an RPC target object with no " + SubscriptionRegistrar.class + " method" , () -> {
				it("should thrown an IllegalStateException exception", () -> {
					expect(() -> new SubscriptionInvoker(kryos, new Object())).toThrow(IllegalArgumentException.class);
				});
			});
			describe("is given a valid RPC target object" , () -> {
				it("should be able to register as a Subscriber without error" , () -> {
					final IdentifiableRequest voidRequest = new IdentifiableRequest(0L);
					final RequestMessage<IdentifiableRequest> fakeMsg = new RequestMessage<>(voidRequest);
					invokerUnderTest.handle(fakeCtx, fakeMsg);
					verify(mockBroadcaster).register(any(Subscriber.class));
				});
				describe("but the call throws an exception" , () -> {
					before(() -> {
						doThrow(RuntimeException.class).when(mockBroadcaster).register(any(Subscriber.class));
					});
					it("should throw a HandlerException" , () -> {
						final IdentifiableRequest voidRequest = new IdentifiableRequest(0L);
						final RequestMessage<IdentifiableRequest> fakeMsg = new RequestMessage<>(voidRequest);
						final ByteBuffer actualReturn = invokerUnderTest.handle(fakeCtx, fakeMsg).get().get();
						final Kryo kryo = kryos.obtain();
						try(Input input = new Input(actualReturn.array())) {
							final ResponseMessage<?> actualResponse = kryo.readObject(input, ResponseMessage.class);
							expect(actualResponse).toBeNotNull();
							expect(actualResponse.isError()).toBeTrue();
							expect(actualResponse.getError().get()).toBeInstanceOf(HandlerException.class);
						}
						finally {
							kryos.free(kryo);
						}
					});
				});
			});
		});

	}

	class FakeBroadcaster { @SubscriptionRegistrar public void register(final Subscriber sub) { /* nothing to do */ } }
}
