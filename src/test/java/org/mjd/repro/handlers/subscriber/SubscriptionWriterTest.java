package org.mjd.repro.handlers.subscriber;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.handlers.message.ResponseMessage;
import org.mjd.repro.message.RequestWithArgs;
import org.mjd.repro.serialisation.Marshaller;
import org.mjd.repro.writers.ChannelWriter;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(OleasterRunner.class)
public class SubscriptionWriterTest {

	private static final RequestWithArgs TEST_MSG = new RequestWithArgs("client-0");

	@Mock private Marshaller mockMarshaller;
	@Mock private SelectionKey mockKey;
	@Mock private ChannelWriter<RequestWithArgs, SelectionKey> mockWriter;

	private SubscriptionWriter<RequestWithArgs> writerUnderTest;

	// TEST INSTANCE BLOCK
	{
		before(() -> {
			MockitoAnnotations.initMocks(this);
			writerUnderTest = new SubscriptionWriter(mockMarshaller, mockKey, mockWriter, TEST_MSG);

			when(mockMarshaller.marshall(any(ResponseMessage.class),
										 ArgumentMatchers.<Class<ResponseMessage<Object>>>any()))
										.thenReturn(new byte[] {});
		});

		describe("when a subscription writer", () ->  {
			describe("is notified", () ->  {
				before(() -> {
					writerUnderTest.receive("this thing!");
				});
				it("should prepate a valid write via the ChannelWriter", () -> {
					mockWriter.prepWrite(eq(mockKey), eq(TEST_MSG), any(ByteBuffer.class));
				});
			});
		});
	}
}
