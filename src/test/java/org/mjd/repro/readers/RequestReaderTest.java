package org.mjd.repro.readers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ScatteringByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.message.factory.MessageFactory;
import org.mjd.repro.message.factory.MessageFactory.MessageCreationException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.OngoingStubbing;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.afterEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@RunWith(OleasterRunner.class)
public final class RequestReaderTest
{
	private static final Integer EXPECTED_MSG = 17369615;
    @Mock private ScatteringByteChannel mockChannel;
    @Mock private MessageFactory<Integer> mockFactory;
    private RequestReader<Integer> readerUnderTest;
    private ByteBuffer headerBuffer;
    private final int sizeOfTestMsg = Integer.BYTES;
    private final byte[] headerValueArray = Ints.toByteArray(sizeOfTestMsg);
    private final List<Byte> headerValueList = Bytes.asList(headerValueArray);
    private ByteBuffer bodyBuffer;
    private final byte[] bodyValueBytes = Ints.toByteArray(EXPECTED_MSG);
    private final List<Byte> bodyValueList = Bytes.asList(bodyValueBytes);
    private ByteBuffer[] remaining;
    private List<Byte> bodyWithNextHeader;

    // NOTES: The HeaderReader of this Request reader should really be mocked out. It's currently
    // hardcoded to an IntHeaderReader which is what this test works with.

    // TEST INSTANCE BLOCK
    {
        beforeEach(() -> {
            MockitoAnnotations.initMocks(this);
            when(mockFactory.createMessage(bodyValueBytes)).thenReturn(EXPECTED_MSG);
            readerUnderTest =  new RequestReader<>("unittest", mockChannel, mockFactory);
            headerBuffer = ByteBuffer.allocate(Integer.BYTES);
            bodyBuffer = ByteBuffer.allocate(1024);
        });

        afterEach(() -> clearReadBuffers());

        describe("When the RequestReader", () -> {

            describe("receives no data", () -> {
                beforeEach(() -> {
                    addHeaderBytesToChannel(0, 0);
                    readerUnderTest.read(headerBuffer, bodyBuffer);
                });
                it("should not be complete", () -> {
                   expect(readerUnderTest.messageComplete()).toBeFalse();
                });
                it("should not detect end of stream", () -> {
                    expect(readerUnderTest.isEndOfStream()).toBeFalse();
                });
                it("should not have a message", () -> {
                    expect(readerUnderTest.getMessage()).toEqual(Optional.empty());
                });
                describe("followed by an end of stream", () -> {
                    beforeEach(() -> {
                       addEndOfStreamToChannel();
                       readerUnderTest.read(headerBuffer, bodyBuffer);
                    });
                    it("should not be complete", () -> {
                        expect(readerUnderTest.messageComplete()).toBeFalse();
                    });
                    it("should not have a message", () -> {
                        expect(readerUnderTest.getMessage()).toEqual(Optional.empty());
                    });
                    it("should detect end of stream", () -> {
                        expect(readerUnderTest.isEndOfStream()).toBeTrue();
                    });
                    it("should throw an IOException on further reads", () -> {
                        expect(() -> readerUnderTest.read(headerBuffer, bodyBuffer)).toThrow(IOException.class);
                    });
                });
            });

            describe("receives part [1byte] of the header", () -> {
                beforeEach(() -> {
                    addHeaderBytesToChannel(0, 1);
                    readerUnderTest.read(headerBuffer, bodyBuffer);
                });
                it("should not be complete", () -> {
                    expect(readerUnderTest.messageComplete()).toBeFalse();
                });
                it("should not detect end of stream", () -> {
                    expect(readerUnderTest.isEndOfStream()).toBeFalse();
                });
                it("should not have a message", () -> {
                    expect(readerUnderTest.getMessage()).toEqual(Optional.empty());
                });

                describe("then receives the rest [3bytes] of the header", () -> {
                    beforeEach(() -> {
                        addHeaderBytesToChannel(1, 4);
                        readerUnderTest.read(headerBuffer, bodyBuffer);
                    });
                    it("should not be complete", () -> {
                        expect(readerUnderTest.messageComplete()).toBeFalse();
                    });
                    it("should not detect end of stream", () -> {
                        expect(readerUnderTest.isEndOfStream()).toBeFalse();
                    });
                    it("should not have a message", () -> {
                        expect(readerUnderTest.getMessage()).toEqual(Optional.empty());
                    });

                    describe("and then all of the body", () -> {
                        beforeEach(() -> {
                            addBodyBytesToChannel(0, 4);
                            readerUnderTest.read(headerBuffer, bodyBuffer);
                        });
                        it("should produce the correct message, an integer 17369615", () -> {
                            expect(readerUnderTest.getMessage().isPresent()).toBeTrue();
                            expect(readerUnderTest.getMessage().get()).toBeInstanceOf(Integer.class);
                            expect(readerUnderTest.getMessage().get()).toEqual(17369615);
                        });
                    });

                    describe("but only part of the body", () -> {
                        beforeEach(() -> {
                            addBodyBytesToChannel(0, 2);
                            readerUnderTest.read(headerBuffer, bodyBuffer);
                        });
                        it("should not be complete", () -> {
                            expect(readerUnderTest.messageComplete()).toBeFalse();
                        });
                        it("should not detect end of stream", () -> {
                            expect(readerUnderTest.isEndOfStream()).toBeFalse();
                        });
                        it("should not have a message", () -> {
                            expect(readerUnderTest.getMessage()).toEqual(Optional.empty());
                        });

                        describe("followed by the rest of the body", () -> {
                            beforeEach(() -> {
                                addBodyBytesToChannel(2, 4);
                                readerUnderTest.read(headerBuffer, bodyBuffer);
                            });
                            it("should be complete", () -> {
                                expect(readerUnderTest.messageComplete()).toBeTrue();
                            });
                            it("should produce the correct message, i.e., an integer of value 17369615", () -> {
                                expect(readerUnderTest.getMessage().get()).toBeInstanceOf(Integer.class);
                                expect(readerUnderTest.getMessage().get()).toEqual(17369615);
                            });
                        });
                        // This case is where two requests are sent by the client back to back and requests
                        // are fragmented over multiple reads
                        describe("followed by the rest of the body and the header from the next message", () -> {
                            beforeEach(() -> {
                                // Create a list with the rest  of the body AND the following message header
                                final List<Byte> bodyWithNextHeader = new ArrayList<>(bodyValueList.subList(2, 4));
                                bodyWithNextHeader.addAll(headerValueList);
                                addBodyBytesToChannel(bodyWithNextHeader);
                                readerUnderTest.read(headerBuffer, bodyBuffer);
                            });
                            it("should complete the message", () -> {
                                expect(readerUnderTest.messageComplete()).toBeTrue();
                            });
                        });
                    });
                });
                describe("then receives the rest of the header in single bytes", () -> {
                    beforeEach(() -> {
                        addHeaderBytesToChannel(1, 2);
                        readerUnderTest.read(headerBuffer, bodyBuffer);
                        addHeaderBytesToChannel(2, 3);
                        readerUnderTest.read(headerBuffer, bodyBuffer);
                        addHeaderBytesToChannel(3, 4);
                        readerUnderTest.read(headerBuffer, bodyBuffer);
                    });
                    it("should not be complete", () -> {
                        expect(readerUnderTest.messageComplete()).toBeFalse();
                    });
                    it("should not detect end of stream", () -> {
                        expect(readerUnderTest.isEndOfStream()).toBeFalse();
                    });
                    it("should not have a message", () -> {
                        expect(readerUnderTest.getMessage()).toEqual(Optional.empty());
                    });

                    describe("followed by the complete body in single bytes", () ->
                    {
                        beforeEach(() -> {
                            addBodyBytesToChannel(0, 1);
                            readerUnderTest.read(headerBuffer, bodyBuffer);
                            addBodyBytesToChannel(1, 2);
                            readerUnderTest.read(headerBuffer, bodyBuffer);
                            addBodyBytesToChannel(2, 3);
                            readerUnderTest.read(headerBuffer, bodyBuffer);
                            addBodyBytesToChannel(3, 4);
                            readerUnderTest.read(headerBuffer, bodyBuffer);
                        });
                        it("should be complete", () -> {
                            expect(readerUnderTest.messageComplete()).toBeTrue();
                        });
                        it("should not detect end of stream", () -> {
                            expect(readerUnderTest.isEndOfStream()).toBeFalse();
                        });
                        it("should produce the correct message, i.e., an integer of value 17369615", () -> {
                            expect(readerUnderTest.getMessage().get()).toBeInstanceOf(Integer.class);
                            expect(readerUnderTest.getMessage().get()).toEqual(17369615);
                        });
                    });
                });
            });

            describe("receives a complete header and body, and the next message HEADER in ONE read ", () -> {
                beforeEach(() -> {
                	bodyWithNextHeader = new ArrayList<>(bodyValueList);
                    bodyWithNextHeader.addAll(headerValueList);
                    addHeaderAndBodyBytesToChannel(headerValueList, bodyWithNextHeader);
                    remaining = readerUnderTest.read(headerBuffer, bodyBuffer);
                });
                it("should be complete", () -> {
                    expect(readerUnderTest.messageComplete()).toBeTrue();
                });
                it("should not detect end of stream", () -> {
                    expect(readerUnderTest.isEndOfStream()).toBeFalse();
                });
                it("should produce the correct message, i.e., an integer of value 17369615", () -> {
                    expect(readerUnderTest.getMessage().get()).toBeInstanceOf(Integer.class);
                    expect(readerUnderTest.getMessage().get()).toEqual(17369615);
                });
                it("should return the following message in a ByteBuffer", () -> {
                	expect(remaining).toBeNotNull();
                	expect(remaining[0]).toEqual(ByteBuffer.allocate(headerValueArray.length).put(headerValueArray));
                });
            });

            describe("receives a complete header and body, and the next complete message in ONE read ", () -> {
            	beforeEach(() -> {
            		bodyWithNextHeader = new ArrayList<>(bodyValueList);
            		bodyWithNextHeader.addAll(headerValueList);
            		bodyWithNextHeader.addAll(bodyValueList);
            		addHeaderAndBodyBytesToChannel(headerValueList, bodyWithNextHeader);
            		remaining = readerUnderTest.read(headerBuffer, bodyBuffer);
            	});
            	it("should be complete", () -> {
            		expect(readerUnderTest.messageComplete()).toBeTrue();
            	});
            	it("should not detect end of stream", () -> {
            		expect(readerUnderTest.isEndOfStream()).toBeFalse();
            	});
            	it("should produce the correct message, i.e., an integer of value 17369615", () -> {
            		expect(readerUnderTest.getMessage().get()).toBeInstanceOf(Integer.class);
            		expect(readerUnderTest.getMessage().get()).toEqual(17369615);
            	});
            	it("should return the following message in in a header and body ByteBuffer", () -> {
            		expect(remaining).toBeNotNull();
            		expect(remaining[0]).toEqual(ByteBuffer.allocate(headerValueArray.length).put(headerValueArray));
            		expect(remaining[1]).toEqual(ByteBuffer.allocate(bodyValueBytes.length).put(bodyValueBytes));
            	});
            });

            describe("receives a complete header but not all of the body in ONE read ", () ->{
                beforeEach(() -> {
                    addHeaderAndBodyBytesToChannel(headerValueList, bodyValueList.subList(0, bodyValueList.size() - 1));
                    readerUnderTest.read(headerBuffer, bodyBuffer);
                });
                it("should not be complete", () -> {
                    expect(readerUnderTest.messageComplete()).toBeFalse();
                });
                it("should not detect end of stream", () -> {
                    expect(readerUnderTest.isEndOfStream()).toBeFalse();
                });
                it("should not have a message", () -> {
                    expect(readerUnderTest.getMessage()).toEqual(Optional.empty());
                });
            });
        });
    } // END TEST INSTANCE BLOCK


    private OngoingStubbing<Long> addHeaderAndBodyBytesToChannel(final List<Byte> header, final List<Byte> body) throws IOException
    {
        clearReadBuffers();
        return when(mockChannel.read(any(ByteBuffer[].class)))
                        .thenAnswer(inv -> addbytesToHeader(header) + addbytesToBody(body))
                        .thenReturn(noMoreData());

    }

    private void addBodyBytesToChannel(final List<Byte> bodyWithNextHeader) throws IOException
    {
        clearReadBuffers();
        when(mockChannel.read(any(ByteBuffer[].class)))
            .thenAnswer(inv -> addbytesToBody(bodyWithNextHeader))
            .thenReturn(noMoreData());
    }

    private void addHeaderBytesToChannel(final int from, final int to) throws IOException, MessageCreationException
    {
        clearReadBuffers();
        when(mockChannel.read(any(ByteBuffer[].class)))
            .thenAnswer(inv -> addbytesToHeader(headerValueList.subList(from, to)))
            .thenReturn(noMoreData());
    }

    private void addBodyBytesToChannel(final int from, final int to) throws IOException, MessageCreationException
    {
        clearReadBuffers();
        when(mockChannel.read(any(ByteBuffer[].class)))
            .thenAnswer(inv -> addbytesToBody(bodyValueList.subList(from, to)))
            .thenReturn(noMoreData());
    }

    private void addEndOfStreamToChannel() throws IOException
    {
        clearReadBuffers();
        when(mockChannel.read(any(ByteBuffer[].class))).thenReturn(endOfStream());
    }

    private static long noMoreData() { return 0L; }
    private static long endOfStream() { return -1L; }

    /** Would happen before each call to read */
    private void clearReadBuffers()
    {
        headerBuffer.clear();
        bodyBuffer.clear();
    }

    private long addbytesToHeader(final List<Byte> bytes)
    {
        return addbytesToHeader(Bytes.toArray(bytes));
    }

    private long addbytesToHeader(final byte... bytesToAdd)
    {
        headerBuffer.put(bytesToAdd);
        return bytesToAdd.length;
    }

    private long addbytesToBody(final List<Byte> bytes)
    {
        return addbytesToBody(Bytes.toArray(bytes));
    }

    private long addbytesToBody(final byte... bytesToAdd)
    {
        bodyBuffer.put(bytesToAdd);
        return bytesToAdd.length;
    }
}
