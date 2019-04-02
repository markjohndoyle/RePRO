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

		describe("when a " + ReflectionInvoker.class + " invokes a valid void method", () -> {
			describe("a valid void method", () -> {
				before(() -> {
					testRequest = new RpcRequest(0L, "voidMethod");
				});
				it("it should do so without error and return null", () -> {
					assertThat(invokerUnderTest.invoke(testRequest), is(nullValue()));
				});
			});
			describe("a valid return method", () -> {
				before(() -> {
					testRequest = new RpcRequest(0L, "intReturn");
				});
				it("it should do so without error and return the correct response", () -> {
					assertThat(invokerUnderTest.invoke(testRequest), is(120509));
				});
			});
			describe("an invalid method", () -> {
				before(() -> {
					testRequest = new RpcRequest(0L, "youwantmetocallwhat?");
				});
				it("it should throw an " + InvocationException.class, () -> {
					expect(() -> invokerUnderTest.invoke(testRequest)).toThrow(InvocationException.class);
				});
			});
		});
	}
}
