package org.mjd.repro.readers.header;

import java.nio.ByteBuffer;

import com.google.common.primitives.Ints;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;

@RunWith(OleasterRunner.class)
public final class IntHeaderReaderTest
{
	private static final int SINGLE_FIRST_BYTE_VAL = 5;
	private static final int LAST_THREE_BYTE_VAL = 3841;
    private IntHeaderReader readerUnderTest;

    private byte[] fullFiveHeader;

    private byte[] lastThreeByteHeader;
    private ByteBuffer completeBuffer;
    private ByteBuffer partBuffer;
    private ByteBuffer restOfItBuffer;

    // TEST BLOCK
    {
        beforeEach(() -> {
            readerUnderTest = new IntHeaderReader("test");
            fullFiveHeader = Ints.toByteArray(SINGLE_FIRST_BYTE_VAL);
            lastThreeByteHeader = Ints.toByteArray(LAST_THREE_BYTE_VAL);
            completeBuffer = ByteBuffer.wrap(fullFiveHeader);
            partBuffer = ByteBuffer.wrap(fullFiveHeader, 0, fullFiveHeader.length - 1);
            restOfItBuffer = ByteBuffer.wrap(fullFiveHeader, fullFiveHeader.length - 1, 1);
        });

        describe("When a client asks the IntHeader reader for the header size", () -> {
        	it("should return " + Integer.BYTES, () -> {
        		expect(readerUnderTest.getSize()).toEqual(Integer.BYTES);
        	});
        });

        describe("when the header data", () -> {
            describe("arrives in", ()-> {
                describe("one single read, that is, all at once", ()-> {
                	beforeEach(() -> {
                		readerUnderTest.readHeader(completeBuffer);
                	});
                    it("should read the correct header value", ()->{
                        expect(readerUnderTest.isComplete()).toBeTrue();
                    });
                    it("should have 0 bytes remaining", () -> {
                    	expect(readerUnderTest.remaining()).toEqual(0);
                    });
                    it("should have the correct value", () -> {
                    	expect(readerUnderTest.getValue()).toEqual(SINGLE_FIRST_BYTE_VAL);
                    });
                });
                describe("two single bytes and the rest does not arrive", () -> {
                	beforeEach(() -> {
                		readerUnderTest.readHeader(ByteBuffer.wrap(fullFiveHeader, 0, 1));
                		readerUnderTest.readHeader(ByteBuffer.wrap(fullFiveHeader, 1, 1));
                	});
                	it("should not be complete", () -> {
                		expect(readerUnderTest.isComplete()).toBeFalse();
                	});
                	it("should have two bytes remaining", () -> {
                		expect(readerUnderTest.remaining()).toEqual(2);
                	});
                	it("should throw an exception for any attempt to get the value", () -> {
                		expect(() -> readerUnderTest.getValue()).toThrow(IllegalStateException.class);
                	});
                });
                describe("single bytes", () -> {
                	beforeEach(() -> {
                		readerUnderTest.readHeader(ByteBuffer.wrap(fullFiveHeader, 0, 1));
                		readerUnderTest.readHeader(ByteBuffer.wrap(fullFiveHeader, 1, 1));
                		readerUnderTest.readHeader(ByteBuffer.wrap(fullFiveHeader, 2, 1));
                		readerUnderTest.readHeader(ByteBuffer.wrap(fullFiveHeader, 3, 1));
                	});
                    it("should complete", () -> {
                        expect(readerUnderTest.isComplete()).toBeTrue();
                    });
                    it("should have zero bytes remaining", () -> {
                    	expect(readerUnderTest.remaining()).toEqual(0);
                    });
                    it("should have the correct value", () -> {
                    	expect(readerUnderTest.getValue()).toEqual(SINGLE_FIRST_BYTE_VAL);
                    });
                });
                describe("two parts", ()-> {
                	describe("with 3 bytes arriving first", ()-> {
	                	beforeEach(() -> {
	                		readerUnderTest.readHeader(partBuffer);
	                	});
	                    describe("and then finally the last byte", ()-> {
	                    	beforeEach(() -> {
	                    		expect(readerUnderTest.isComplete()).toBeFalse();
	                    	});
	                        it("should read the correct header value", () -> {
	                            readerUnderTest.readHeader(restOfItBuffer);
	                            expect(readerUnderTest.isComplete()).toBeTrue();
	                            expect(readerUnderTest.getValue()).toEqual(SINGLE_FIRST_BYTE_VAL);
	                        });
	                        it("should show 1 remaining after the first 3 bytes arrive", () -> {
	                            readerUnderTest.readHeader(partBuffer);
	                            expect(readerUnderTest.isComplete()).toBeFalse();
	                            expect(readerUnderTest.remaining()).toEqual(1);
	                        });
	                    });
                	});
                    describe("where the last byte arrives first", ()-> {
                        it("should read the correct header value", () -> {
                            final ByteBuffer firstByte = ByteBuffer.wrap(fullFiveHeader, 0, 1);
                            final ByteBuffer restOfItBuffer = ByteBuffer.wrap(fullFiveHeader, 1, fullFiveHeader.length - 1);
                            readerUnderTest.readHeader(firstByte);
                            expect(readerUnderTest.isComplete()).toBeFalse();
                            readerUnderTest.readHeader(restOfItBuffer);
                            expect(readerUnderTest.isComplete()).toBeTrue();
                            expect(readerUnderTest.getValue()).toEqual(SINGLE_FIRST_BYTE_VAL);
                        });
                        it("should show 3 remaining after the first byte arrives", () -> {
                            final ByteBuffer firstByte = ByteBuffer.wrap(fullFiveHeader, 0, 1);
                            readerUnderTest.readHeader(firstByte);
                            expect(readerUnderTest.isComplete()).toBeFalse();
                            expect(readerUnderTest.remaining()).toEqual(3);
                        });
                        describe("and the header value uses three bytes", ()-> {
                            it("should read the correct header value", () -> {
                                final ByteBuffer firstByte = ByteBuffer.wrap(lastThreeByteHeader, 0, 1);
                                final ByteBuffer restOfItBuffer = ByteBuffer.wrap(lastThreeByteHeader, 1, lastThreeByteHeader.length - 1);
                                readerUnderTest.readHeader(firstByte);
                                expect(readerUnderTest.isComplete()).toBeFalse();
                                readerUnderTest.readHeader(restOfItBuffer);
                                expect(readerUnderTest.isComplete()).toBeTrue();
                                expect(readerUnderTest.getValue()).toEqual(LAST_THREE_BYTE_VAL);
                            });
                        });
                    });
                });
                describe("three parts", ()-> {
                    it("should read the correct header value", () -> {
                        final ByteBuffer partBuffer = ByteBuffer.wrap(fullFiveHeader, 0, fullFiveHeader.length - 2);
                        final ByteBuffer middlePartBuffer = ByteBuffer.wrap(fullFiveHeader, fullFiveHeader.length - 2, 1);
                        readerUnderTest.readHeader(partBuffer);
                        expect(readerUnderTest.isComplete()).toBeFalse();
                        readerUnderTest.readHeader(middlePartBuffer);
                        expect(readerUnderTest.isComplete()).toBeFalse();
                        readerUnderTest.readHeader(restOfItBuffer);
                        expect(readerUnderTest.isComplete()).toBeTrue();
                        expect(readerUnderTest.getValue()).toEqual(SINGLE_FIRST_BYTE_VAL);
                    });
                    describe("with an empty part in the middle", ()-> {
                        it("should read the correct header value", () -> {
                            final ByteBuffer middlePartBuffer = ByteBuffer.wrap(new byte[0]);
                            readerUnderTest.readHeader(partBuffer);
                            expect(readerUnderTest.isComplete()).toBeFalse();
                            readerUnderTest.readHeader(middlePartBuffer);
                            expect(readerUnderTest.isComplete()).toBeFalse();
                            readerUnderTest.readHeader(restOfItBuffer);
                            expect(readerUnderTest.isComplete()).toBeTrue();
                            expect(readerUnderTest.getValue()).toEqual(SINGLE_FIRST_BYTE_VAL);
                        });
                    });
                });
            });
            describe("one single read, that is, all at once", ()-> {
            	describe("but in a ByteBuffer with old data at the beginning", ()-> {
                	beforeEach(() -> {
                		final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES + completeBuffer.capacity());
                		buffer.putLong(123456789L).put(completeBuffer);
                		readerUnderTest.readHeader(buffer, Long.BYTES);
                	});
                    it("should read the correct header value", ()->{
                        expect(readerUnderTest.isComplete()).toBeTrue();
                    });
                    it("should have 0 bytes remaining", () -> {
                    	expect(readerUnderTest.remaining()).toEqual(0);
                    });
                    it("should have the correct value", () -> {
                    	expect(readerUnderTest.getValue()).toEqual(SINGLE_FIRST_BYTE_VAL);
                    });
            	});
            });
            describe("does not arrive", ()-> {
            	beforeEach(() -> {
            		final ByteBuffer emptyBuffer = ByteBuffer.wrap(new byte[0]);
            		readerUnderTest.readHeader(emptyBuffer);
            	});
                it("should not complete", ()->{
                    expect(readerUnderTest.isComplete()).toBeFalse();
                });
                it("should have 4 bytes remaining", ()->{
                	expect(readerUnderTest.remaining()).toEqual(Integer.BYTES);
                });
                it("should throw an exception for any attempt to get the value", () -> {
            		expect(() -> readerUnderTest.getValue()).toThrow(IllegalStateException.class);
            	});
            });

            describe("When an IntHeaderReader is constructed without an ID", () -> {
            	it("should construct without error", IntHeaderReader::new);
            });

        }); // end when header data... suite
    }

}
