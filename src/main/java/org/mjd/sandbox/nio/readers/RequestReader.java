package org.mjd.sandbox.nio.readers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Optional;

import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.factory.MessageFactory;
import org.mjd.sandbox.nio.message.factory.MessageFactory.MessageCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RequestReader<T> implements MessageReader<T>
{
    private static final Logger LOG = LoggerFactory.getLogger(RequestReader.class);
    private static final int UNKNOWN = -1;
    
    private MessageFactory<T> factory;
    private final SocketChannel channel;
    private final String id;
    
    
    private final int headerSize;
    private int headerValue = UNKNOWN;
    
    private Message<T> message = null;
    private byte[] bytesReadFromChannel;
    private int messageRead;
    private int messageSize = UNKNOWN;
    
    private boolean endOfStream;

    public RequestReader(String requestID, SocketChannel channel, MessageFactory<T> factory)
    {
        this.id = requestID;
        this.channel = channel;
        this.factory = factory;
        headerSize = factory.getHeaderSize();
    }

    @Override
    public void read(ByteBuffer headerBuffer, ByteBuffer bodyBuffer) throws IOException, MessageCreationException
    {
        ByteBuffer[] buffers = {headerBuffer, bodyBuffer};
        // read the bytes from the buffer
        long totalBytesReadThisCall = 0;
        long bytesRead;
        while((bytesRead = channel.read(buffers)) > 0)
        {
            totalBytesReadThisCall += bytesRead;
        }
        
        if(totalBytesReadThisCall > 0)
        {
            // This wont work if the header isn't read in one go. The next time around the headerBuffer
            // would have been cleared! It's fine if the header is one byte of course
            if(!headerBuffer.hasRemaining())
            {
                headerValue = ((ByteBuffer)headerBuffer.flip()).getInt();
                messageSize = headerValue;
                bytesReadFromChannel = new byte[headerValue];
            }
            if(headerComplete())
            {
                int copyAmount = calcBodyCopy(totalBytesReadThisCall);
                bodyBuffer.flip();
                copyFromChannelBuffer(bodyBuffer, bytesReadFromChannel, copyAmount);
                messageRead += copyAmount;
                
                // if enough, create the message.
                if(bytesReadFromChannel.length == headerValue)
                {
                    message = factory.create(bytesReadFromChannel);
                }
            }
        }

        if(bytesRead == -1) 
        {
            LOG.debug("ENDOFSTREAM for '{}'. Message complete: {}", id, messageComplete());
            endOfStream = true;
        }
    }

    private int calcBodyCopy(long totalBytesReadThisCall)
    {
        int copyAmount;
        if(totalBytesReadThisCall + messageRead > messageSize)
        {
            copyAmount = messageSize - messageRead;
        }
        else
        {
            copyAmount = Math.toIntExact(totalBytesReadThisCall - headerSize);
        }
        return copyAmount;
    }

    private void copyFromChannelBuffer(ByteBuffer buffer, byte[] intoBuffer, int copyAmount)
    {
        try
        {
            buffer.get(intoBuffer, messageRead, copyAmount);
        }
        catch(Exception e)
        {
            e.printStackTrace();
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
    
    private boolean headerComplete()
    {
        return headerValue != UNKNOWN;
    }

    @Override
    public boolean isEndOfStream()
    {
        return endOfStream;
    }

    public static <MsgType> RequestReader<MsgType> from(SelectionKey key, MessageFactory<MsgType> messageFactory)
    {
        return new RequestReader<MsgType>((String)key.attachment(), (SocketChannel) key.channel(), messageFactory);
    }
}
