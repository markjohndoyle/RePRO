package org.mjd.sandbox.nio.handlers.key;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(OleasterRunner.class)
public class KeyChannelCloserTest {
	@Mock private SelectionKey mockKey;
	@Spy private SelectableChannel mockChannel;

	private KeyChannelCloser handlerUnderTest;

	{
		before(() -> {
			MockitoAnnotations.initMocks(this);
			when(mockKey.channel()).thenReturn(mockChannel);
		});

		beforeEach(() -> {
			handlerUnderTest = new KeyChannelCloser();
		});

		describe("When calling handle on the " + KeyChannelCloser.class.getSimpleName(), () -> {
			it("should close the channel associated with the key", () -> {
				handlerUnderTest.handle(mockKey);
				verify(mockChannel).close();
			});
		});
	}
}
