package org.mjd.repro.async;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.writers.ChannelWriter;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.afterEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(OleasterRunner.class)
public final class SequentialMessageJobExecutorTest {
	private final Optional<ByteBuffer> fakeResult = Optional.of(ByteBuffer.allocate(0));
	private final Optional<ByteBuffer> fakeVoidResult = Optional.empty();
	private Selector selector;
	private SequentialMessageJobExecutor<Integer> executorUnderTest;
	private AsyncMessageJob<Integer> fakeJob;
	private Integer fakeMessage = 1;
	@Mock private ChannelWriter<Integer, SelectionKey> mockChannelWriter;
	@Spy private SelectionKey selectionKey;
	@Mock private Future<Optional<ByteBuffer>> mockFuture;


	// UNIT TEST INSTANCE BLOCK
	{
		before(() -> {
			MockitoAnnotations.initMocks(this);
			selector = Selector.open();
			fakeJob = new AsyncMessageJob<>(selectionKey, fakeMessage, mockFuture);
		});


		describe("when a started " + SequentialMessageJobExecutor.class.getName(), () -> {
			beforeEach(() -> {
				executorUnderTest = new SequentialMessageJobExecutor<>(selector, mockChannelWriter, true);
			});
			afterEach(() -> executorUnderTest.stop());

			describe("receives one job that throws an exception when processing", () -> {
				beforeEach(() -> {
					when(mockFuture.get(anyLong(), any(TimeUnit.class))).thenThrow(ExecutionException.class);
					executorUnderTest.add(fakeJob);
				});
				it("should put the job back on the queue to be processed next time around", () -> {
					executorUnderTest.start();
					verify(mockChannelWriter, never()).writeResult(selectionKey, fakeMessage, fakeResult.get());
				});
			});
			describe("receives one job that has NOT finished processing", () -> {
				beforeEach(() -> {
					when(mockFuture.get(anyLong(), any(TimeUnit.class)))
										.thenThrow(TimeoutException.class)
										.thenReturn(fakeResult);
					executorUnderTest.add(fakeJob);
				});
				it("should put the job back on the queue to be processed next time around", () -> {
					executorUnderTest.start();
					verify(mockChannelWriter, timeout(5000)).writeResult(selectionKey, fakeMessage, fakeResult.get());
				});
			});
			describe("receives has one job that has finished processing", () -> {
				beforeEach(() -> {
					executorUnderTest.add(fakeJob);
				});

				describe("but does NOT have a result to write back", () -> {
					beforeEach(() -> {
						when(mockFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(fakeVoidResult);
						executorUnderTest.start();
					});
					it("should not write anything to the channel writer", () -> {
						verify(mockChannelWriter, timeout(5000)).writeResult(selectionKey, fakeMessage, ByteBuffer.allocate(0));
					});

				});
				describe("and has a result to write back", () -> {
					beforeEach(() -> {
						when(mockFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(fakeResult);
						executorUnderTest.start();
					});

					it("write the result of the job to the channel writer", () -> {
						verify(mockChannelWriter, timeout(5000)).writeResult(selectionKey, fakeMessage, fakeResult.get());
					});
				});
			});
		});

	}

}
