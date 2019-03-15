package org.mjd.repro.writers;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
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
public class SizeHeaderWriterTest {
	@Mock private WritableByteChannel mockChannel;
	private final ByteBuffer testBuffer = ByteBuffer.allocate(Long.BYTES * 4);
	private SizeHeaderWriter writerUnderTest;

	// TEST INSTANCE BLOCK
	{
		before(() -> {
			MockitoAnnotations.initMocks(this);
			testBuffer.putLong(1).putLong(2).putLong(3).putLong(4).flip();
			when(mockChannel.write(any(ByteBuffer.class)))
												.then((answer) -> {
													((ByteBuffer)answer.getArgument(0)).position(Integer.BYTES);
													return Integer.BYTES;
												})
												.then((answer) -> {
													final ByteBuffer buffer = (ByteBuffer)answer.getArgument(0);
													buffer.position(buffer.limit());
													return buffer.capacity();
												});
		});

		describe("when a size header writer", () -> {
			describe("is asked to write the complete header", () -> {
				beforeEach(() -> {
					writerUnderTest = new SizeHeaderWriter(0, mockChannel, testBuffer);
					writerUnderTest.write();
				});
				it("should complete", () -> {
					expect(writerUnderTest.isComplete()).toBeTrue();
				});
			});
		});
	}

}
