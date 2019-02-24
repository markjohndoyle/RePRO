package org.mjd.sandbox.nio.readers;

import java.nio.ByteBuffer;

import com.google.common.primitives.Ints;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.sandbox.nio.readers.header.IntHeaderReader;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;

@RunWith(OleasterRunner.class)
public final class IntHeaderReaderTest
{
    private IntHeaderReader readerUnderTest;

    private final int singleFirstByteValue = 5;
    private final int lastThreeByteValue = 3841;
    private byte[] fullFiveHeader;

    private byte[] lastThreeByteHeader;
    private ByteBuffer completeBuffer;
    private ByteBuffer partBuffer;
    private ByteBuffer restOfItBuffer;

    // TEST BLOCK
    {
        beforeEach(() -> {
            readerUnderTest = new IntHeaderReader(Integer.BYTES);
            fullFiveHeader = Ints.toByteArray(singleFirstByteValue);
            lastThreeByteHeader = Ints.toByteArray(lastThreeByteValue);
            completeBuffer = ByteBuffer.wrap(fullFiveHeader);
            partBuffer = ByteBuffer.wrap(fullFiveHeader, 0, fullFiveHeader.length - 1);
            restOfItBuffer = ByteBuffer.wrap(fullFiveHeader, fullFiveHeader.length - 1, 1);
        });


        describe("when the header data", () -> {
            describe("arrives in", ()-> {
                describe("one write, that is, all at once", ()-> {
                    it("should read the correct header value", ()->{
                        readerUnderTest.readHeader("unit test", completeBuffer);
                        expect(readerUnderTest.isComplete()).toBeTrue();
                        expect(readerUnderTest.getValue()).toEqual(singleFirstByteValue);
                    });
                });
                describe("two single bytes and the rest does not arrive", () -> {
                   it("should not be complete", () -> {
                       readerUnderTest.readHeader("twoSingleTest", ByteBuffer.wrap(fullFiveHeader, 0, 1));
                       readerUnderTest.readHeader("twoSingleTest", ByteBuffer.wrap(fullFiveHeader, 1, 1));
                       expect(readerUnderTest.isComplete()).toBeFalse();
                   });
                });
                describe("single bytes", () -> {
                    it("should complete", () -> {
                        readerUnderTest.readHeader("allSingleTest", ByteBuffer.wrap(fullFiveHeader, 0, 1));
                        readerUnderTest.readHeader("allSingleTest", ByteBuffer.wrap(fullFiveHeader, 1, 1));
                        readerUnderTest.readHeader("allSingleTest", ByteBuffer.wrap(fullFiveHeader, 2, 1));
                        readerUnderTest.readHeader("allSingleTest", ByteBuffer.wrap(fullFiveHeader, 3, 1));
                        expect(readerUnderTest.isComplete()).toBeTrue();
                    });
                });
                describe("two parts", ()-> {
//                	describe("followed by the the rest of the header and the entier body", () -> {
//                		it("should read the correct header value", () -> {
//                			System.err.println(Arrays.toString(partBuffer.array()));
//                			ByteBuffer restOfIt = ByteBuffer.allocate(8)
//                									.put(restOfItBuffer)
//                									.putInt(0).put((byte) 0x01);
//                			System.err.println(Arrays.toString(partBuffer.array()));
//                			System.err.println(Arrays.toString(restOfIt.array()));
//                			readerUnderTest.readHeader("unit test", partBuffer);
//                			expect(readerUnderTest.isComplete()).toBeFalse();
//                			readerUnderTest.readHeader("unit test", (ByteBuffer) restOfIt.flip());
//                			expect(readerUnderTest.isComplete()).toBeTrue();
//                			expect(readerUnderTest.getValue()).toEqual(singleFirstByteValue);
//                		});
//                	});
                    describe("where the last byte arrives last", ()-> {
                        it("should read the correct header value", () -> {
                            readerUnderTest.readHeader("unit test", partBuffer);
                            expect(readerUnderTest.isComplete()).toBeFalse();
                            readerUnderTest.readHeader("unit test", restOfItBuffer);
                            expect(readerUnderTest.isComplete()).toBeTrue();
                            expect(readerUnderTest.getValue()).toEqual(singleFirstByteValue);
                        });
                        it("should show 1 remaining after the first 3 bytes arrive", () -> {
                            readerUnderTest.readHeader("unit test", partBuffer);
                            expect(readerUnderTest.isComplete()).toBeFalse();
                            expect(readerUnderTest.remaining()).toEqual(1);
                        });
                    });
                    describe("where the last byte arrives first", ()-> {
                        it("should read the correct header value", () -> {
                            ByteBuffer firstByte = ByteBuffer.wrap(fullFiveHeader, 0, 1);
                            ByteBuffer restOfItBuffer = ByteBuffer.wrap(fullFiveHeader, 1, fullFiveHeader.length - 1);
                            readerUnderTest.readHeader("unit test", firstByte);
                            expect(readerUnderTest.isComplete()).toBeFalse();
                            readerUnderTest.readHeader("unit test", restOfItBuffer);
                            expect(readerUnderTest.isComplete()).toBeTrue();
                            expect(readerUnderTest.getValue()).toEqual(singleFirstByteValue);
                        });
                        it("should show 3 remaining after the first byte arrives", () -> {
                            ByteBuffer firstByte = ByteBuffer.wrap(fullFiveHeader, 0, 1);
                            readerUnderTest.readHeader("unit test", firstByte);
                            expect(readerUnderTest.isComplete()).toBeFalse();
                            expect(readerUnderTest.remaining()).toEqual(3);
                        });
                        describe("and the header value uses three bytes", ()-> {
                            it("should read the correct header value", () -> {
                                ByteBuffer firstByte = ByteBuffer.wrap(lastThreeByteHeader, 0, 1);
                                ByteBuffer restOfItBuffer = ByteBuffer.wrap(lastThreeByteHeader, 1, lastThreeByteHeader.length - 1);
                                readerUnderTest.readHeader("unit test", firstByte);
                                expect(readerUnderTest.isComplete()).toBeFalse();
                                readerUnderTest.readHeader("unit test", restOfItBuffer);
                                expect(readerUnderTest.isComplete()).toBeTrue();
                                expect(readerUnderTest.getValue()).toEqual(lastThreeByteValue);
                            });
                        });
                    });
                });
                describe("three parts", ()-> {
                    it("should read the correct header value", () -> {
                        ByteBuffer partBuffer = ByteBuffer.wrap(fullFiveHeader, 0, fullFiveHeader.length - 2);
                        ByteBuffer middlePartBuffer = ByteBuffer.wrap(fullFiveHeader, fullFiveHeader.length - 2, 1);
                        readerUnderTest.readHeader("unit test", partBuffer);
                        expect(readerUnderTest.isComplete()).toBeFalse();
                        readerUnderTest.readHeader("unit test", middlePartBuffer);
                        expect(readerUnderTest.isComplete()).toBeFalse();
                        readerUnderTest.readHeader("unit test", restOfItBuffer);
                        expect(readerUnderTest.isComplete()).toBeTrue();
                        expect(readerUnderTest.getValue()).toEqual(singleFirstByteValue);
                    });
                    describe("with an empty part in the middle", ()-> {
                        it("should read the correct header value", () -> {
                            ByteBuffer middlePartBuffer = ByteBuffer.wrap(new byte[0]);
                            readerUnderTest.readHeader("unit test", partBuffer);
                            expect(readerUnderTest.isComplete()).toBeFalse();
                            readerUnderTest.readHeader("unit test", middlePartBuffer);
                            expect(readerUnderTest.isComplete()).toBeFalse();
                            readerUnderTest.readHeader("unit test", restOfItBuffer);
                            expect(readerUnderTest.isComplete()).toBeTrue();
                            expect(readerUnderTest.getValue()).toEqual(singleFirstByteValue);
                        });
                    });
                });
            });
            describe("does not arrive", ()-> {
                it("should not complete", ()->{
                    ByteBuffer emptyBuffer = ByteBuffer.wrap(new byte[0]);
                    readerUnderTest.readHeader("unit test", emptyBuffer);
                    expect(readerUnderTest.isComplete()).toBeFalse();
                });
            });
        }); // end when header data... suite
    }

}
