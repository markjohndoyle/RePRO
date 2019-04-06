package org.mjd.repro.handlers.routing;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.async.AsyncMessageJob;
import org.mjd.repro.async.AsyncMessageJobExecutor;
import org.mjd.repro.handlers.message.MessageHandler;
import org.mjd.repro.writers.ChannelWriter;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(OleasterRunner.class)
public class SuppliedMsgHandlerRouterTest {

	private static final String HANDLER_ID = "theHandler";
	private SuppliedMsgHandlerRouter<Integer> routerWithHandlers;
	private SuppliedMsgHandlerRouter<Integer> routerOhneHandlers;
	@Mock private Function<Integer, String> mockHandlerRouter;
	@Mock private ChannelWriter<Integer, SelectionKey> mockChannelWriter;
	@Mock private AsyncMessageJobExecutor<Integer> mockAsyncMsgJobExecutor;
	@Mock private SelectionKey mockKey;
	@Mock private MessageHandler<Integer> mockMsgHandler;
	@Mock private Future<Optional<ByteBuffer>> mockFuture;
	private Map<String, MessageHandler<Integer>> mockMsgHandlers;

	// TEST INSTANCE BLOCK
	{
		before(() -> {
			MockitoAnnotations.initMocks(this);
			when(mockHandlerRouter.apply(anyInt())).thenReturn(HANDLER_ID);
			mockMsgHandlers = ImmutableMap.of(HANDLER_ID, mockMsgHandler);
			routerWithHandlers = new SuppliedMsgHandlerRouter<>(mockHandlerRouter, mockMsgHandlers, mockChannelWriter,
														     	mockAsyncMsgJobExecutor);
			routerOhneHandlers = new SuppliedMsgHandlerRouter<>(mockHandlerRouter, ImmutableMap.of(), mockChannelWriter,
																mockAsyncMsgJobExecutor);
			when(mockMsgHandler.handle(any(MessageHandler.ConnectionContext.class), eq(1))).thenReturn(mockFuture);
		});

		describe("Routing a message when there are configured handlers", () -> {
			describe("when there are configured handlers", () -> {
				before(() -> {
					routerWithHandlers.routeToHandler(mockKey, 1);
				});
				it("should send it to the correct handler", () -> {
					verify(mockMsgHandler).handle(any(MessageHandler.ConnectionContext.class), eq(1));
				});
				it("should add a job to the asyncMessageJobExecutor", () -> {
					verify(mockAsyncMsgJobExecutor).add(AsyncMessageJob.from(mockKey, 1, mockFuture));
				});
			});
			describe("when there are NO configured handlers", () -> {
				before(() -> {
					routerOhneHandlers.routeToHandler(mockKey, 1);
				});
				it("should NOT add a job to the asyncMessageJobExecutor", () -> {
					verify(mockAsyncMsgJobExecutor, never()).add(any(AsyncMessageJob.class));
				});
			});
		});
	}
}
