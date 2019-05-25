package org.mjd.repro;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.handlers.message.MessageHandler;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static org.mjd.repro.Server.createServer;

@RunWith(OleasterRunner.class)
public class ServerBuilderTest {
	@Mock private MessageHandler<Integer> mockDefHandler;
	@Mock private MessageHandler<Integer> mockRedHandler;
	@Mock private MessageHandler<Integer> mockBlueHandler;

	private Server<Integer> intServer;

	// TEST INSTANCE BLOCK
	{
		before(() -> {
			MockitoAnnotations.initMocks(this);
		});

		describe("a ServerBuilder that creates a valid Server", () -> {
			before(() -> {
				intServer = createServer(Integer.class).defaultHandler(mockDefHandler)
													   .and().handler("red", mockRedHandler)
													   .and().handler("blue", mockBlueHandler)
													   .using().router(i -> "red")
													   .build();
			});
			it("should not return a null instance", () -> {
				expect(intServer).toBeNotNull();
			});
		});
	}
}
