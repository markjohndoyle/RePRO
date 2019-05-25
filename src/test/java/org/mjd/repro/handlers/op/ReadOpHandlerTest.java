package org.mjd.repro.handlers.op;

import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.handlers.routing.MessageHandlerRouter;
import org.mjd.repro.message.factory.MessageFactory;
import org.mjd.repro.util.chain.Handler;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static java.nio.channels.SelectionKey.OP_READ;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(OleasterRunner.class)
public class ReadOpHandlerTest {
	@Mock private Handler<SelectionKey> mockNextHandler;
	@Mock private MessageFactory<Integer> mockMsgFactory;
	@Mock private MessageHandlerRouter<Integer> mockRouter;
	@Mock private SelectionKey mockKey;
	@Mock private MockChannel mockChannel;

	private ReadOpHandler<Integer, SelectionKey> protocolUnderTest;


	private abstract class MockChannel extends SelectableChannel implements ScatteringByteChannel {
		// Test support
	}

	// TEST INSTANCE BLOCK
	{
		before(() -> {
			MockitoAnnotations.initMocks(this);
			when(mockKey.channel()).thenReturn(mockChannel);
			protocolUnderTest = new ReadOpHandler<>(mockMsgFactory, mockRouter);
			protocolUnderTest.setNext(mockNextHandler);
		});

		describe("When an read op protocol", () -> {
			describe("receives a valid key", () -> {
				before(() -> {
					when(mockKey.isValid()).thenReturn(true);
				});
				describe("in an readable state", () -> {
					before(() -> {
						when(mockKey.readyOps()).thenReturn(OP_READ);
					});
					describe("and the server successfully accepts the connection", () -> {
						before(() -> {
							protocolUnderTest.handle(mockKey);
						});
						it("should pass on the key to the next handler", () -> {
							verify(mockNextHandler).handle(mockKey);
						});
					});
				});
			});
			describe("receives a invalid key", () -> {
				before(() -> {
					when(mockKey.isValid()).thenReturn(false);
					protocolUnderTest.handle(mockKey);
				});
				it("should pass on the key to the next handler", () -> {
					verify(mockNextHandler).handle(mockKey);
				});
			});
		});
	}
}
