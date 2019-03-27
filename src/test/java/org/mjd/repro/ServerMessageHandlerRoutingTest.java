package org.mjd.repro;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.handlers.message.MessageHandler;
import org.mjd.repro.message.factory.MessageFactory;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.afterEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.ONE_MINUTE;
import static org.awaitility.Duration.TEN_SECONDS;
import static org.mjd.repro.util.thread.Threads.called;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(OleasterRunner.class)
public final class ServerMessageHandlerRoutingTest {

	@Mock private MessageFactory<Integer> mockMsgFactory;
	@Mock private MessageHandler<Integer> mockHandlerDef;
	@Mock private MessageHandler<Integer> mockHandlerOne;
	@Mock private Future<Optional<ByteBuffer>> mockAsyncHandle;

	private ExecutorService serverService;
	private Server<Integer> serverUnderTest;
	private Socket clientSocket;

	// TEST INSTANCE BLOCK
	{
		before(() -> {
			MockitoAnnotations.initMocks(this);
		});

		describe("A server with two handlers, oneHandler for messages of value 1 and default for all other messages", () -> {
			beforeEach(() -> {
				serverService = Executors.newSingleThreadExecutor(called("Server"));
				serverUnderTest = new Server<>(mockMsgFactory, (i) -> i == 1 ? "oneHandler" : "default");
				serverUnderTest.addHandler(mockHandlerDef)
							   .addHandler("oneHandler", mockHandlerOne);
				startServer();
			});
			afterEach(() -> {
				closeOutStream();
				serverUnderTest.shutDown();
				serverService.shutdownNow();
				await().atMost(ONE_MINUTE).until(serverUnderTest::isShutdown);
			});
			describe("that receives an integer message with the value 5", () -> {
				final int msgFive = 5;
				beforeEach(() -> {
					when(mockMsgFactory.createMessage(any(byte[].class))).thenReturn(msgFive);
					when(mockHandlerDef.handle(any(MessageHandler.ConnectionContext.class), eq(msgFive))).thenReturn(mockAsyncHandle);
					when(mockAsyncHandle.get(anyLong(), any(TimeUnit.class))).thenReturn(Optional.empty());

					final DataOutputStream outStream = getOutStream();
					outStream.writeInt(1);
					outStream.writeInt(5);
				});
				it("should only route to the default handler", () -> {
					verify(mockHandlerDef, timeout(5000)).handle(any(MessageHandler.ConnectionContext.class), eq(msgFive));
					verify(mockHandlerOne, never()).handle(any(MessageHandler.ConnectionContext.class), any());
				});
			});
			describe("that receives an integer message with the value 1", () -> {
				final int msgOne = 1;
				beforeEach(() -> {
					when(mockMsgFactory.createMessage(any(byte[].class))).thenReturn(msgOne);
					when(mockHandlerDef.handle(any(MessageHandler.ConnectionContext.class), any())).thenReturn(mockAsyncHandle);
					when(mockAsyncHandle.get(anyLong(), any(TimeUnit.class))).thenReturn(Optional.empty());

					final DataOutputStream outStream = getOutStream();
					outStream.writeInt(1);
					outStream.writeInt(1);
				});
				it("should only route to the oneHandler handler", () -> {
					verify(mockHandlerOne, timeout(10000)).handle(any(MessageHandler.ConnectionContext.class), eq(msgOne));
					verify(mockHandlerDef, never()).handle(any(MessageHandler.ConnectionContext.class), any());
				});
			});
		});
	}

	private void startServer() {
		serverService.execute(serverUnderTest::start);
		await().atMost(TEN_SECONDS).until(serverUnderTest::isAvailable);
	}

	private DataOutputStream getOutStream() throws Exception {
		clientSocket = new Socket("localhost", serverUnderTest.getPort());
		return new DataOutputStream(clientSocket.getOutputStream());
	}

	private void closeOutStream() throws IOException {
		clientSocket.close();
	}
}
