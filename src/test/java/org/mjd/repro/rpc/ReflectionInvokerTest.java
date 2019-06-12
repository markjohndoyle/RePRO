package org.mjd.repro.rpc;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.message.RpcRequest;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Unit tests for the {@link ReflectionInvoker}
 */
@RunWith(OleasterRunner.class)
public class ReflectionInvokerTest {
	private final FakeTarget fakeTarget = new FakeTarget();
	private RpcRequest testRequest;

	class FakeTarget {
		public void voidMethod() { /* nothing to do */ }
		public int intReturn() { return 120509;}
	}

	// TEST INSTANCE BLOCK
	{
		final ReflectionInvoker invokerUnderTest = new ReflectionInvoker(fakeTarget);
		final ReflectionInvoker invokerNoTargetUnderTest = new ReflectionInvoker();

		describe("when a " + ReflectionInvoker.class + " invokes a", () -> {
			describe("valid method", () -> {
				describe("with a void return", () -> {
					before(() -> {
						testRequest = new RpcRequest("client-0", "voidMethod");
					});
					it("it should do so without error and return null", () -> {
						assertThat(invokerUnderTest.invoke(testRequest), is(nullValue()));
					});
				});
				describe("a non-null return", () -> {
					before(() -> {
						testRequest = new RpcRequest("client-0", "intReturn");
					});
					it("it should do so without error and return the correct response", () -> {
						assertThat(invokerUnderTest.invoke(testRequest), is(120509));
					});
				});
			});
			describe("an invalid method", () -> {
				before(() -> {
					testRequest = new RpcRequest("client-0", "youwantmetocallwhat?");
				});
				it("it should throw an " + InvocationException.class, () -> {
					expect(() -> invokerUnderTest.invoke(testRequest)).toThrow(InvocationException.class);
				});
			});
		});
		describe("when a " + ReflectionInvoker.class + " with no target", () -> {
			before(() -> {
				testRequest = new RpcRequest("client-0", "voidMethod");
			});
			describe("invokes a method", () -> {
				it("it should throw an " + IllegalStateException.class, () -> {
					expect(() -> invokerNoTargetUnderTest.invoke(testRequest)).toThrow(IllegalStateException.class);
				});
			});
			describe("has a target assigned", () -> {
				before(() -> {
					invokerNoTargetUnderTest.changeTarget(fakeTarget);
				});
				describe("and then invokes a valid method", () -> {
					before(() -> {
						testRequest = new RpcRequest("client-0", "voidMethod");
					});
					it("it should do so without error and return null", () -> {
						assertThat(invokerUnderTest.invoke(testRequest), is(nullValue()));
					});
				});
			});
		});
	}
}
