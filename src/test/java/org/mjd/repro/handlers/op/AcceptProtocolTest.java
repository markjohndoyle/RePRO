package org.mjd.repro.handlers.op;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.util.chain.Handler;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(OleasterRunner.class)
public class AcceptProtocolTest {
	@Mock private ServerSocketChannel mockServerChannel;
	@Mock private Selector mockSelector;
	@Mock private SelectionKey mockKey;
	@Mock private SelectableChannel mockClientChannel;
	@Mock private Handler<SelectionKey> mockNextHandler;
	@Mock private AcceptProtocol.Acceptor mockAcceptor;

	private AcceptProtocol<SelectionKey> protocolUnderTest;

	// TEST INSTANCE BLOCK
	{
		before(() -> {
			MockitoAnnotations.initMocks(this);
			protocolUnderTest = new AcceptProtocol<>(mockServerChannel, mockSelector, mockAcceptor);
			protocolUnderTest.setNext(mockNextHandler);
		});

		describe("When constructing an AcceptProtocol with no Acceptor", () -> {
			it("should return an AcceptProtocol instance", () -> {
				final AcceptProtocol<SelectionKey> acceptProtocol = new AcceptProtocol<>(mockServerChannel, mockSelector);
				expect(acceptProtocol).toBeNotNull();
				expect(acceptProtocol).toBeInstanceOf(AcceptProtocol.class);
			});
		});

		describe("When an accept protocol", () -> {
			describe("receives a valid key", () -> {
				before(() -> {
					when(mockKey.isValid()).thenReturn(true);
				});
				describe("in an accept state", () -> {
					before(() -> {
						when(mockKey.readyOps()).thenReturn(OP_ACCEPT);
					});
					describe("and the server successfully accepts the connection", () -> {
						before(() -> {
							when(mockAcceptor.accept(mockServerChannel)).thenReturn(mockClientChannel);
							protocolUnderTest.handle(mockKey);
						});
						it("should configure the client to non-blocking", () -> {
							verify(mockClientChannel).configureBlocking(false);
						});
						it("should register the client with the selector for read ops and set a string ID", () -> {
							verify(mockClientChannel).register(eq(mockSelector), eq(OP_READ), anyString());
						});
						it("should NOT pass on the key to the next handler", () -> {
							verify(mockNextHandler, never()).handle(mockKey);
						});
					});
					describe("and the server does NOT successfully accept the connection", () -> {
						before(() -> {
							when(mockAcceptor.accept(mockServerChannel)).thenReturn(null);
							protocolUnderTest.handle(mockKey);
						});
						it("should NOT pass on the key to the next handler", () -> {
							verify(mockNextHandler, never()).handle(mockKey);
						});
					});
					describe("and the server throws an exception whilst attempting to accept the connection", () -> {
						before(() -> {
							when(mockAcceptor.accept(mockServerChannel)).thenThrow(IOException.class);
							protocolUnderTest.handle(mockKey);
						});
						it("should NOT pass on the key to the next handler", () -> {
							verify(mockNextHandler, never()).handle(mockKey);
						});
					});
				});
				describe("not in an accept state", () -> {
					before(() -> {
						when(mockKey.readyOps()).thenReturn(OP_READ);
						protocolUnderTest.handle(mockKey);
					});
					it("should pass on the key to the next handler", () -> {
						verify(mockNextHandler).handle(mockKey);
					});
				});
			});
			describe("receives a invalid key", () -> {
				before(() -> {
					when(mockKey.isValid()).thenReturn(false);
					protocolUnderTest.handle(mockKey);
				});
				it("should pass on the key to the next handler", () -> {
					verify(mockNextHandler).handle(mockKey);
				});
			});
		});
	}
}
