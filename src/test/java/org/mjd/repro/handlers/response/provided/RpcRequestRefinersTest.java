package org.mjd.repro.handlers.response.provided;

import java.nio.ByteBuffer;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.handlers.response.provided.RpcRequestRefiners.Prepend;
import org.mjd.repro.message.RequestWithArgs;
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

	private static final String ID = "client-5478";
	private static final int SIZE_OF_REQ_ID = Integer.BYTES + ID.getBytes().length;

	private RequestWithArgs rpcRequest;
	private ByteBuffer buffer;
	private Prepend prependRefinerUnderTest;
	private ByteBuffer result;

	// TEST INSTANCE BLOCK
	{
		before(() -> {
			prependRefinerUnderTest = RpcRequestRefiners.prepend;
			rpcRequest = new RpcRequest(ID, "warp5");
		});
		beforeEach(() -> {
			buffer = (ByteBuffer) ByteBuffer.allocate(Integer.BYTES).putInt(5).flip();
		});

		describe("when a prepend refiner", () -> {
			describe("prepends a request ID", () -> {
				beforeEach(() -> {
					result = (ByteBuffer) prependRefinerUnderTest.requestId(rpcRequest, buffer).flip();
				});
				it("should prepend the correct String value to the given buffer", () -> {
					final int idLength = result.getInt();
					expect(idLength).toEqual(ID.getBytes().length);
					final byte[] idBytes = new byte[idLength];
					result.get(idBytes, 0, idLength);
					final String id = new String(idBytes);
					expect(id).toEqual(ID);
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


