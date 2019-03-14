package org.mjd.repro.handlers.response.provided;

import java.nio.ByteBuffer;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.handlers.response.provided.RpcRequestRefiners.Prepend;
import org.mjd.repro.message.IdentifiableRequest;
import org.mjd.repro.message.RpcRequest;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;

/**
 * Unit tests for the {@link RpcRequestRefiners}
 */
@RunWith(OleasterRunner.class)
public class RpcRequestRefinersTest {
	private static final int SIZE_OF_REQ_ID = Long.BYTES;
	private IdentifiableRequest rpcRequest;
	private ByteBuffer buffer;
	private Prepend prependRefinerUnderTest;
	private ByteBuffer result;

	// TEST INSTANCE BLOCK
	{
		before(() -> {
			prependRefinerUnderTest = RpcRequestRefiners.prepend;
			rpcRequest = new RpcRequest(5478L, "warp5");
		});
		beforeEach(() -> {
			buffer = (ByteBuffer) ByteBuffer.allocate(Integer.BYTES).putInt(5).flip();
		});

		describe("when a prepend refiner", () -> {
			describe("prepends a request ID", () -> {
				beforeEach(() -> {
					result = (ByteBuffer) prependRefinerUnderTest.requestId(rpcRequest, buffer).flip();
				});
				it("should prepend the correct long value to the given buffer", () -> {
					expect(result.getLong()).toEqual(5478);
				});
				it("should return a buffer with the original data at the end", () -> {
					result.position(SIZE_OF_REQ_ID);
					expect(result.getInt()).toEqual(5);
				});
				it("should return a buffer with a capacity of the original data plus the id type length", () -> {
					expect(result.capacity()).toEqual(SIZE_OF_REQ_ID + Integer.BYTES);
				});
				it("should return an buffer that is at position 0 when flipped for reading", () -> {
					expect(result.hasRemaining()).toBeTrue();
				});
			});
		});
	}
}


