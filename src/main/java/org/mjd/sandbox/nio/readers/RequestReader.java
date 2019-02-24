package org.mjd.sandbox.nio.readers;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Optional;

import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.factory.MessageFactory;
import org.mjd.sandbox.nio.message.factory.MessageFactory.MessageCreationException;
import org.mjd.sandbox.nio.readers.header.HeaderReader;
import org.mjd.sandbox.nio.readers.header.IntHeaderReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RequestReader<T> implements MessageReader<T>
{
    private static final Logger LOG = LoggerFactory.getLogger(RequestReader.class);
    private static final int UNKNOWN = -1;

    private MessageFactory<T> factory;
    private final ScatteringByteChannel channel;
    private final String id;

    private final int headerSize;
    private boolean vectoredIO;
    private HeaderReader<Integer> headerReader;

    private Message<T> message = null;
    private byte[] bytesReadFromChannel;
    private int messageRead;
    private int bodySize = UNKNOWN;
    private int totalBodyRead;

    private boolean endOfStream;
    private int remainingBody = Integer.MAX_VALUE;


    public RequestReader(String requestID, ScatteringByteChannel channel, MessageFactory<T> factory)
    {
        this.id = requestID;
        this.channel = channel;
        this.factory = factory;
        headerSize = factory.getHeaderSize();
        headerReader = new IntHeaderReader(id, headerSize);
    }

    @Override
    public ByteBuffer read(ByteBuffer headerBuffer, ByteBuffer bodyBuffer) throws MessageCreationException, IOException
    {
    	LOG.trace("[{}] ------------NEW READ------------------", id);
        prereadCheck();

        ByteBuffer remaining = null;
        int headerRemaining = bodySize;
        ByteBuffer[] buffers;
        if(!headerReader.isComplete())
        {
            // we don't have the header yet
            headerRemaining = headerReader.remaining();
            headerBuffer.position(headerSize - headerRemaining).mark(); // Explain this part - header split over multiple reads - aligning buffer to simulate it arriving in one go
            LOG.trace("[{}] partial header in vectored read {}/{}; setting position and marking to {}", id, headerReader.remaining(), headerSize, headerSize - headerRemaining);
            buffers = new ByteBuffer[]{headerBuffer, bodyBuffer};
            vectoredIO = true;
        }
        else
        {
            LOG.trace("[{}] Header already complete on a previous read, using body buffer alone for further reads", id);
            buffers = new ByteBuffer[]{bodyBuffer};
            vectoredIO = false;
        }
        // read the bytes from the buffer
        long totalBytesReadThisCall = 0;
        long bytesRead;
        try
        {
            LOG.trace("[{}] Reading from channel <--[=====--]. Vectoring is {}, Using {} buffer(s)", id, vectoredIO, buffers.length);
            LOG.trace("[{}] headerbuffer state before send to read: {}  {}", id, headerBuffer, Arrays.toString(headerBuffer.array()));
            LOG.trace("[{}] bodybuffer state before send to read: {} ", id, bodyBuffer);//, Arrays.toString(bodyBuffer.array()));
            while((bytesRead = channel.read(buffers)) > 0)
            {
//                LOG.trace("[{}] Read {} bytes from channel", id, bytesRead);
                totalBytesReadThisCall += bytesRead;
            }
            if(vectoredIO)
            {
                LOG.trace("[{}] Buffer states post read -> head:{} body:{}", id, Arrays.toString(headerBuffer.array()), bodyBuffer);
            }
            else
            {
                LOG.trace("[{}] Buffer states post read -> body:{}", id, bodyBuffer);
            }
            LOG.trace("[{}] Read {} bytes from channel until no more data or EOS", id, totalBytesReadThisCall);
        }
        catch (IOException e)
        {
            LOG.trace("[{}] Client channel disconnected in read. Ending stream.", id);
            endOfStream = true;
            return ByteBuffer.allocate(0);
        }

        if(totalBytesReadThisCall > 0)
        {
            if(!headerReader.isComplete())
            {
                LOG.trace("[{}] headerbuff state before send to Header reader: {} Contents: {}", id, headerBuffer, Arrays.toString(headerBuffer.array()));
                if(headerRemaining != headerSize)
                {
                    int inThisBuffer = Math.min(headerRemaining, Math.toIntExact(totalBytesReadThisCall));
                    LOG.trace("{} header remaining is not equal to {} header size. " +
                              "Reseting to mark and moving limit back to {} before passing to header reader." +
                              "There are {} bytes of header in this buffer",
                              headerRemaining, headerSize, headerBuffer.position() + inThisBuffer, inThisBuffer);
                    headerReader.readHeader(id, (ByteBuffer) headerBuffer.reset().limit(headerBuffer.position() + inThisBuffer));
                }
                else
                {
                    headerReader.readHeader(id, (ByteBuffer) headerBuffer.flip());
                }

                if(headerReader.isComplete())
                {
                    bodySize = headerReader.getValue();
                    remainingBody = bodySize;
                    bytesReadFromChannel = new byte[headerReader.getValue()];
                    LOG.trace("[{}] Header read complete! Message body size is {}", id, bodySize);
                }
            }
            if(bodySize == UNKNOWN && headerReader.isComplete())
            {
                bodySize = headerReader.getValue();
                remainingBody = bodySize;
                bytesReadFromChannel = new byte[headerReader.getValue()];
                LOG.trace("[{}] Header read completed eventually, message body size is {}", id, bodySize);
            }

            int bodyReadThisTime;
            // Header needs to be complete to get the value which is the size of the body
            if(headerReader.isComplete())
            {
                if(!vectoredIO)
                {
                    // then we didn't use the header buffer this time and can assume total read was the minimum of
                    // the total bytes written or the total minuse the header. Anything more is the next message.
                    // We also need to ignore the next message!
                    LOG.trace("[{}] Non-vectored IO", id);
                    if(totalBytesReadThisCall > remainingBody)
                    {
                        // we have part of the next message, this needs to be returned from this read method
                        bodyReadThisTime = remainingBody;
                        LOG.trace("[{}] We have part of the next message, {} bytes belong to this body of {} read", id, remainingBody, totalBytesReadThisCall);
                        byte[] remainder = new byte[Math.toIntExact(totalBytesReadThisCall) - remainingBody];
                        bodyBuffer.position(bodyReadThisTime);
                        bodyBuffer.get(remainder, 0, remainder.length);
                        remaining = ByteBuffer.wrap(remainder);
                    }
                    else
                    {
                        bodyReadThisTime = Math.toIntExact(totalBytesReadThisCall);
                    }
                }
                else
                {
                    LOG.trace("[{}] Vectored IO: body read is totalbytesRead - headerSize. {} - {}", id, totalBytesReadThisCall, headerSize);
                    bodyReadThisTime = Math.toIntExact(totalBytesReadThisCall - headerRemaining);
                }
                bodyBuffer.flip();
                if(bodyBuffer.hasRemaining())
                {
                    LOG.trace("[{}] Header is already complete; the body is {} bytes within {}", id, remainingBody, bodyBuffer);
                    int maxBodyWeCanRead = Math.min(bodyReadThisTime, remainingBody); // in case we have the next message
                    LOG.trace("[{}] Will attempt copy of {} which is all we have read UP to the body size.", id, maxBodyWeCanRead);
                    LOG.trace("[{}] {}", id, bodyBuffer.toString());
                    // TODO efficiency! If the body buffer is complete we can use it in the factory directly rather than copy
                    copyFromChannelBuffer(bodyBuffer, bytesReadFromChannel, maxBodyWeCanRead);

                    // if enough, create the message.
                    if(totalBodyRead == bodySize)
                    {
                        LOG.trace("[{}] Total read {}/{} of message size. Creating message.", id, totalBodyRead, bodySize);
                        message = factory.create(bytesReadFromChannel);
                    }
                    else
                    {
                        LOG.trace("[{}] Not read the entire body yet. Read {} of {}", id, totalBodyRead, bodySize);
                        remainingBody = bodySize - totalBodyRead;
                    }
                }
                else
                {
                    LOG.trace("[{}] Body buffer is empty", id);
                }
            }
        }

        if(bytesRead == -1)
        {
            LOG.debug("[{}] ENDOFSTREAM for '{}'. Message complete: {}", id, id, messageComplete());
            endOfStream = true;
        }

        if(remaining != null)
        {
            System.err.println("Unread = " + remaining + " hb = "+ Arrays.toString(remaining.array()));
        }
        LOG.trace("[{}] ------------END READ------------------", id);
        return remaining;
    }

    private void prereadCheck() throws IOException
    {
        if(endOfStream)
        {
            throw new IOException("Cannot read because this reader encloses a channel that as previously sent " +
                                  "end of stream");
        }
    }


    private void copyFromChannelBuffer(ByteBuffer buffer, byte[] intoBuffer, int copyAmount)
    {
        try
        {
            LOG.trace("[{}] Attempting copy of size {} after flooring to message size max", id, copyAmount);
            buffer.get(intoBuffer, messageRead, copyAmount);
            messageRead += copyAmount;
            totalBodyRead += copyAmount;
        }
        catch(BufferUnderflowException | IndexOutOfBoundsException e)
        {
            LOG.error("[" + id + "] Error copying from ByteBuffer to local byte array buffer. Attempted read of {};" +
                      " there were {} remaining in the source ByteBffer",
                      copyAmount, buffer.remaining(), e);
            LOG.error(buffer.toString());
        }
    }

    @Override
    public Optional<Message<T>> getMessage()
    {
        return Optional.ofNullable(message);
    }

    @Override
    public boolean messageComplete()
    {
        return message != null;
    }

    @Override
    public boolean isEndOfStream()
    {
        return endOfStream;
    }

    public static <MsgType> RequestReader<MsgType> from(SelectionKey key, MessageFactory<MsgType> messageFactory)
    {
        return new RequestReader<>((String)key.attachment(), (SocketChannel) key.channel(), messageFactory);
    }
}
