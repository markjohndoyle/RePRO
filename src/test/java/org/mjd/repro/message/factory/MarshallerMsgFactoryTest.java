package org.mjd.repro.message.factory;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.serialisation.Marshaller;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the {@link MarshallerMsgFactory}.
 */
@RunWith(OleasterRunner.class)
public class MarshallerMsgFactoryTest {
	private static final byte[] FAKE_BYTES = new byte[] {0x0F};

	@Mock private Marshaller mockMarshaller;
	private MarshallerMsgFactory<Integer> factoryUnderTest;

	// TEST INSTANCE BLOCK
	{
		before(() -> {
			MockitoAnnotations.initMocks(this);
			factoryUnderTest = new MarshallerMsgFactory<>(mockMarshaller, Integer.class);
		});

		describe("when a marshaller factory creates a message", () -> {
			before(() -> {
				factoryUnderTest.createMessage(FAKE_BYTES);
			});
			it("should use the associated Marshaller", () -> {
				verify(mockMarshaller).unmarshall(FAKE_BYTES, Integer.class);
			});
		});

	}
}
