package org.mjd.repro.writers;

import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.function.BiFunction;

import com.google.common.collect.ImmutableList;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.handlers.response.ResponseRefiner;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link RefiningChannelWriter} class.
 */
@RunWith(OleasterRunner.class)
public class RefiningChannelWriterTest {

	@Mock private Selector mockSelector;
	@Mock private ResponseRefiner<String> mockRefiner;
	@Mock private SelectionKey mockKey;
	@Mock private Writer mockWriter;
	@Mock private SelectableChannel mockChannel;

	private static final String FAKE_MSG = "experience power";
	private final ByteBuffer fakeResult = ByteBuffer.wrap("Division".getBytes());
	private RefiningChannelWriter<String, SelectionKey> writerNoRefiners;
	private RefiningChannelWriter<String, SelectionKey> writerWithRefiner;
	private BiFunction<SelectionKey, ByteBuffer, Writer> mockWriterSupplier;

	// TEST INSTANCE BLOCK
	{
		before(() -> {
			MockitoAnnotations.initMocks(this);
			mockWriterSupplier = (k,b) -> mockWriter;
			when(mockRefiner.execute(FAKE_MSG, fakeResult)).thenReturn(fakeResult);
			when(mockKey.channel()).thenReturn(mockChannel);
			when(mockWriter.isComplete()).thenReturn(false).thenReturn(true);
		});

		describe("a RefiningChannelWriter with no refiners", () -> {
			before(() -> {
				writerNoRefiners = new RefiningChannelWriter<>(mockSelector, ImmutableList.of(), mockWriterSupplier);
			});
			describe("prepares a write", () -> {
				before(() -> {
					writerNoRefiners.prepWrite(mockKey, FAKE_MSG, fakeResult);
				});
				it("should add OP_WRITE to the selection key interested operations", () -> {
					verify(mockKey).interestOps(mockKey.interestOps() | OP_WRITE);
				});
				it("should wakeup the selector", () -> {
					verify(mockSelector).wakeup();
				});
			});
			describe("prepares a write for a key that is cancelled", () -> {
				before(() -> {
					when(mockKey.interestOps(anyInt())).thenThrow(CancelledKeyException.class);
					writerNoRefiners.prepWrite(mockKey, FAKE_MSG, fakeResult);
				});
				it("should NOT wakeup the selector", () -> {
					verify(mockSelector, never()).wakeup();
				});
				it("should remove all writers for this key", () -> {
					writerNoRefiners.write(mockKey);
					verify(mockWriter, never()).write();
				});
			});
		});
		describe("a RefiningChannelWriter with refiners", () -> {
			before(() -> {
				writerWithRefiner = new RefiningChannelWriter<>(mockSelector, ImmutableList.of(mockRefiner), mockWriterSupplier);
			});
			describe("prepares a write", () -> {
				before(() -> {
					writerWithRefiner.prepWrite(mockKey, FAKE_MSG, fakeResult);
				});
				it("should add OP_WRITE to the selection key interested operations", () -> {
					verify(mockKey).interestOps(mockKey.interestOps() | OP_WRITE);
				});
				it("should wakeup the selector", () -> {
					verify(mockSelector).wakeup();
				});
				describe("and is then asked to write", () -> {
					before(() -> {
						writerWithRefiner.write(mockKey);
					});
					it("should trigger a write on the current response writers", () -> {
						verify(mockWriter).write();
					});
					it("should set the key to READ_OP only", () -> {
						verify(mockKey).interestOps(OP_READ);
					});
				});
			});
		});
	}
}
