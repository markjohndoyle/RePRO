package org.mjd.sandbox.nio.util;

import java.nio.ByteBuffer;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;

@RunWith(OleasterRunner.class)
public class ByteBuffersTest {

	private final ByteBuffer testBuffer = ByteBuffer.allocate(Integer.BYTES);

	// TEST INSTANCE BLOCK
	{
//		describe("When a safe read operation is performed", () -> {
//			describe("on a part filled ByteBuffer", () -> {
//				beforeEach(() -> {
//					testBuffer.put((byte) 0x05).put((byte) 0x06);
//				});
//				it("should return the buffer's position and limit original offsets", () -> {
//					int remaining = safe(testBuffer, ByteBuffer::remaining);
//					expect(remaining).toEqual(2);
//					expect(testBuffer.position()).toEqual(2);
//					expect(testBuffer.limit()).toEqual(4);
//				});
//			});
//		});
	}
}
