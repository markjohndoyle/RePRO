package org.mjd.sandbox.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.mjd.sandbox.nio.message.factory.MessageFactory;
import org.mjd.sandbox.nio.message.factory.MessageFactory.MessageCreationException;
import org.mjd.sandbox.nio.readers.MessageReader;
import org.mjd.sandbox.nio.readers.RequestReader;
import org.mjd.sandbox.nio.util.Maps;
import org.mjd.sandbox.nio.writers.ByteBufferWriter;
import org.mjd.sandbox.nio.writers.Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

public final class Server<MsgType>
{
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    
    private final ServerSocketChannel serverChannel;
    
    private final Selector selector = Selector.open();

    private final AtomicLong conId = new AtomicLong(0);
    
    private final Map<Channel, MessageReader<MsgType>> readers = new HashMap<>();
    private final Map<Channel, Writer> responseWriters = new HashMap<>();
    
    private final ByteBuffer bodyBuffer = ByteBuffer.allocate(1024);
    private final ByteBuffer headerBuffer;
    private final ByteBuffer writeBuffer = ByteBuffer.allocate(1024);

    private MessageFactory<MsgType> messageFactory;

    private RespondingHandler<MsgType> handler;
    
    public Server(MessageFactory<MsgType> messageFactory) throws IOException
    {
        this.messageFactory = messageFactory;
        headerBuffer = ByteBuffer.allocate(messageFactory.getHeaderSize());
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(12509));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, OP_ACCEPT, "The Director");
    }
    
    public void start() throws IOException, MessageCreationException
    {
        LOG.info("Server starting..");
        while(!Thread.interrupted())
        {
            selector.select();
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            handleReadyKeys(selectedKeys.iterator());
        }
        LOG.info("Server shutting down...");
        selector.close();
        serverChannel.close();
    }
    
    public Server<MsgType> addHandler(RespondingHandler<MsgType> handler)
    {
        this.handler = handler;
        return this;
    }
    
    public boolean isShutdown()
    {
        return !serverChannel.isOpen() && !selector.isOpen();
    }
    
    private void handleReadyKeys(Iterator<SelectionKey> iter) throws IOException, MessageCreationException
    {
        while (iter.hasNext()) 
        {
            SelectionKey key = iter.next();
            if(!key.isValid())
            {
                LOG.trace("Invalid key {} closing channel", key.attachment());
                key.channel().close();
                iter.remove();
                continue;
            }
            // serverChannel
            if(key.isAcceptable())
            {
                SelectableChannel socketChannel = key.channel();
                LOG.trace("{} is acceptable, a client is connecting.", key.attachment());
                SocketChannel clientChannel = serverChannel.accept();
                LOG.trace("Socket accepted {}", socketChannel);
                clientChannel.configureBlocking(false);
                clientChannel.register(selector, OP_READ, "client " + conId.incrementAndGet());
            }
            // clients SocketChannels
            if(key.isReadable())
            {
                MessageReader<MsgType> reader = Maps.getOrCreateFrom(readers, key.channel(), () -> {
                    return RequestReader.from(key, messageFactory);
                });

                clearReadBuffers();
                try
                {
                    reader.read(headerBuffer, bodyBuffer);
                }
                catch (ClosedByInterruptException e)
                {
                    LOG.trace("{} client was closed within read. We'll just skip it.", key.attachment());
                    continue;
                }
                if(reader.isEndOfStream())
                {
                    LOG.trace("{} end of stream.", key.attachment());
                    readers.remove(key.channel());
                    cancelClient(key);
                }
                handleCompleteMsg(reader, key);
            }
            // client response, triggered by read.
            if(key.isValid() && key.isWritable())
            {
                Writer responseWriter = responseWriters.get(key.channel());
                if(responseWriter != null)
                {
                    writeBuffer.clear();
                    responseWriter.write();
                    // If the writer is complete we're done. Otherwise we'll leave the key interested in write
                    if(responseWriter.complete())
                    {
                        key.interestOps(OP_READ);
                        responseWriters.remove(key.channel());
                    }
                }
            }
            iter.remove();
        }
    }
    
    private void handleCompleteMsg(MessageReader<MsgType> reader, SelectionKey key)
    {
        if(reader.messageComplete())
        {
            if(key.isValid())
            {
                if(handler == null)
                {
                    LOG.warn("No handlers for {}. Message will be discarded.", key.attachment());
                    return;
                }
                ByteBuffer result = handler.execute(reader.getMessage().get());
                responseWriters.put(key.channel(), new ByteBufferWriter((SocketChannel) key.channel(), result));
                key.interestOps(key.interestOps() | OP_WRITE);
                readers.remove(key.channel());
            }
        }
    }

    private void clearReadBuffers()
    {
        headerBuffer.clear();
        bodyBuffer.clear();
    }

    private void cancelClient(SelectionKey key) throws IOException
    {
        key.cancel();
        key.channel().close();
    }
}
