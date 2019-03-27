package org.mjd.repro.readers.body;

import java.nio.ByteBuffer;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.message.factory.MessageFactory;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.when;


@RunWith(OleasterRunner.class)
public final class SingleMessageBodyReaderTest {

	private static final String TEST_MSG_VAL = "I'm sorry, Dave; I'm afraid I can't do that.";
	private static final byte[] TEST_MSG_VAL_BYTES = TEST_MSG_VAL.getBytes();
	private ByteBuffer testMsgValBytes;
	private ByteBuffer remaining;

	private SingleMessageBodyReader<String> readerUnderTest;

	@Mock private MessageFactory<String> mockMessageFactory;

	// TEST INSTANCE BLOCK
	{
		beforeEach(() -> {
			MockitoAnnotations.initMocks(this);
			when(mockMessageFactory.createMessage(aryEq(TEST_MSG_VAL_BYTES))).thenReturn(TEST_MSG_VAL);
			readerUnderTest = new SingleMessageBodyReader<>("unittest", mockMessageFactory);
			readerUnderTest.setBodySize(TEST_MSG_VAL_BYTES.length);
			testMsgValBytes = ByteBuffer.wrap(TEST_MSG_VAL_BYTES);
		});

		describe("When the " + SingleMessageBodyReader.class.getName(), () ->
		{
			describe("reads a buffer with a complete message", () ->
			{
				beforeEach(() -> {
					testMsgValBytes = ByteBuffer.wrap(TEST_MSG_VAL_BYTES);
					remaining = readerUnderTest.read(testMsgValBytes);
				});
				it("should be complete", () -> {
					expect(readerUnderTest.isComplete()).toBeTrue();
				});
				it("should have a message", () -> {
					expect(readerUnderTest.getMessage()).toBeNotNull();
				});
				it("should decode the correct message", () -> {
					expect(readerUnderTest.getMessage()).toEqual(TEST_MSG_VAL);
				});
				it("should return null for remaining data", () -> {
					expect(remaining.hasRemaining()).toBeFalse();
				});
			});

			describe("reads a buffer with a complete message PLUS following message data", () ->
			{
				beforeEach(() -> {
					final ByteBuffer restPlusFollowingData = ByteBuffer.allocate(testMsgValBytes.capacity() + Integer.BYTES)
															.put(testMsgValBytes)
															.putInt(4);
					testMsgValBytes = ByteBuffer.wrap(TEST_MSG_VAL_BYTES);
					remaining = readerUnderTest.read((ByteBuffer) restPlusFollowingData.flip());
				});
				it("should be complete", () -> {
					expect(readerUnderTest.isComplete()).toBeTrue();
				});
				it("should have a message", () -> {
					expect(readerUnderTest.getMessage()).toBeNotNull();
				});
				it("should decode the correct message", () -> {
					expect(readerUnderTest.getMessage()).toEqual(TEST_MSG_VAL);
				});
				it("should have " + Integer.BYTES + " bytes of remaining data", () -> {
					expect(remaining).toBeNotNull();
					expect(remaining.limit()).toEqual(Integer.BYTES);
				});
			});

			describe("reads a buffer with a 0 bytes", () ->
			{
				beforeEach(() -> {
					testMsgValBytes = ByteBuffer.allocate(0);
					readerUnderTest.read(testMsgValBytes);
				});
				it("should not e complete", () -> {
					expect(readerUnderTest.isComplete()).toBeFalse();
				});
				it("should not have a message", () -> {
					expect(readerUnderTest.getMessage()).toBeNull();
				});
			});

			describe("reads a buffer with a half of a message", () ->
			{
				beforeEach(() -> {
					testMsgValBytes = ByteBuffer.wrap(TEST_MSG_VAL_BYTES, 0, TEST_MSG_VAL.length() / 2);
					readerUnderTest.read(testMsgValBytes);
				});
				it("should not be complete", () -> {
					expect(readerUnderTest.isComplete()).toBeFalse();
				});
				it("should not have a message", () -> {
					expect(readerUnderTest.getMessage()).toBeNull();
				});

				describe("and then receives the final half of the message", () ->
				{
					beforeEach(() -> {
						testMsgValBytes = ByteBuffer.wrap(TEST_MSG_VAL_BYTES, TEST_MSG_VAL.length() / 2, TEST_MSG_VAL.length() / 2);
						readerUnderTest.read(testMsgValBytes);
					});
					it("should be complete", () -> {
						expect(readerUnderTest.isComplete()).toBeTrue();
					});
					it("should have a message", () -> {
						expect(readerUnderTest.getMessage()).toBeNotNull();
					});
					it("should decode the correct message", () -> {
						expect(readerUnderTest.getMessage()).toEqual(TEST_MSG_VAL);
					});
				});

				describe("and then receives the final half of the message PLUS the following message's header", () ->
				{
					beforeEach(() -> {
						testMsgValBytes = ByteBuffer.wrap(TEST_MSG_VAL_BYTES, TEST_MSG_VAL.length() / 2, TEST_MSG_VAL.length() / 2);
						final ByteBuffer restPlusNextHeader = ByteBuffer.allocate(testMsgValBytes.capacity() + Integer.BYTES)
								.put(testMsgValBytes)
								.putInt(4);
						remaining = readerUnderTest.read((ByteBuffer) restPlusNextHeader.flip());
					});
					it("should be complete", () -> {
						expect(readerUnderTest.isComplete()).toBeTrue();
					});
					it("should have a message", () -> {
						expect(readerUnderTest.getMessage()).toBeNotNull();
					});
					it("should decode the correct message", () -> {
						expect(readerUnderTest.getMessage()).toEqual(TEST_MSG_VAL);
					});
					it("should have " + Integer.BYTES + " bytes of remaining data", () -> {
						expect(remaining).toBeNotNull();
						expect(remaining.limit()).toEqual(Integer.BYTES);
					});
				});
			});

			describe("already has a size set, further set size calls don't disrupt multiple reads", () -> {
				beforeEach(() -> {
					readerUnderTest.setBodySize(TEST_MSG_VAL_BYTES.length);
				});
				describe("reads a buffer with a half of a message", () ->
				{
					beforeEach(() -> {
						testMsgValBytes = ByteBuffer.wrap(TEST_MSG_VAL_BYTES, 0, TEST_MSG_VAL.length() / 2);
						readerUnderTest.read(testMsgValBytes);
					});
					it("should not be complete", () -> {
						expect(readerUnderTest.isComplete()).toBeFalse();
					});
					it("should not have a message", () -> {
						expect(readerUnderTest.getMessage()).toBeNull();
					});

					describe("and then receives the final half of the message", () ->
					{
						beforeEach(() -> {
							testMsgValBytes = ByteBuffer.wrap(TEST_MSG_VAL_BYTES, TEST_MSG_VAL.length() / 2, TEST_MSG_VAL.length() / 2);
							readerUnderTest.read(testMsgValBytes);
						});
						it("should be complete", () -> {
							expect(readerUnderTest.isComplete()).toBeTrue();
						});
						it("should have a message", () -> {
							expect(readerUnderTest.getMessage()).toBeNotNull();
						});
						it("should decode the correct message", () -> {
							expect(readerUnderTest.getMessage()).toEqual(TEST_MSG_VAL);
						});
					});
				});
			});
		});
	}
}
