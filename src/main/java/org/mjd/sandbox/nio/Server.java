package org.mjd.sandbox.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.mjd.sandbox.nio.handlers.key.InvalidKeyHandler;
import org.mjd.sandbox.nio.handlers.key.InvalidKeyHandler.InvalidKeyHandlerException;
import org.mjd.sandbox.nio.handlers.key.KeyChannelCloser;
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
    
    private ServerSocketChannel serverChannel;
    
    private Selector selector;

    private long conId;
    
    private final Map<Channel, MessageReader<MsgType>> readers = new HashMap<>();
    private final Map<Channel, Writer> responseWriters = new HashMap<>();
    
    private final ByteBuffer bodyBuffer = ByteBuffer.allocate(1024);
    private final ByteBuffer headerBuffer;
    private final ByteBuffer writeBuffer = ByteBuffer.allocate(1024);

    private MessageFactory<MsgType> messageFactory;

    private RespondingHandler<MsgType> handler;

    private InvalidKeyHandler validityHandler;
    
    /**
     * Creates a fully initialised single threaded non-blocking {@link Server}.
     * 
     * The server is not started and will not accept connections until you call {@link #start()}
     * 
     * @param messageFactory
     * @throws IOException
     */
    public Server(MessageFactory<MsgType> messageFactory)
    {
        this(messageFactory, new KeyChannelCloser());
    }
    
    public Server(MessageFactory<MsgType> messageFactory, InvalidKeyHandler invalidKeyHandler)
    {
        this.messageFactory = messageFactory;
        this.validityHandler = invalidKeyHandler;
        headerBuffer = ByteBuffer.allocate(messageFactory.getHeaderSize());
    }
    
    /**
     * Starts the listen loop with a blocking selector.
     * Each key is handled in a non-blocking loop before returning to the selector. 
     * 
     * @throws IOException
     * @throws MessageCreationException
     */
    public void start() throws IOException, MessageCreationException
    {
        LOG.info("Server starting..");
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(12509));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, OP_ACCEPT, "The Director");
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
    
    public boolean isAvailable()
    {
        return serverChannel.isOpen() && selector.isOpen();
    }
    
    public boolean isShutdown()
    {
        return !isAvailable();
    }
    
    public void shutDown() throws IOException
    {
        selector.close();
    }
    
    private void handleReadyKeys(Iterator<SelectionKey> iter) throws IOException, MessageCreationException
    {
        while (iter.hasNext()) 
        {
            SelectionKey key = iter.next();
            if(!key.isValid())
            {
                try
                {
                    validityHandler.handle(key);
                    iter.remove();
                }
                catch (InvalidKeyHandlerException e)
                {
                    LOG.error("Error handling invalid key {}. Key will be removed regardless. Error {}", 
                              key.attachment(), e);
                }
                continue;
            }
            // serverChannel
            if(key.isAcceptable())
            {
                handleAccept(key);
            }
            // clients SocketChannels
            if(key.isReadable())
            {
                try
                {
                    handleReadable(key);
                }
                catch(MessageCreationException ex)
                {
                    LOG.warn("Error creating message for data sent by client " + key.attachment(), ex);
                    continue;
                }
            }
            // client response, triggered by read.
            if(key.isValid() && key.isWritable())
            {
                Writer responseWriter = responseWriters.get(key.channel());
                if(responseWriter != null)
                {
//                    writeBuffer.clear();
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

    private void handleReadable(SelectionKey key) throws MessageCreationException
    {
        MessageReader<MsgType> reader = Maps.getOrCreateFrom(readers, key.channel(), () -> {
            return RequestReader.from(key, messageFactory);
        });

        clearReadBuffers();
        reader.read(headerBuffer, bodyBuffer);
        if(reader.isEndOfStream())
        {
            handleEndOfStream(key);
        }
        if(reader.messageComplete())
        {
            handleCompleteMsg(reader, key);
        }
    }

    private void handleEndOfStream(SelectionKey key)
    {
        LOG.trace("{} end of stream.", key.attachment());
        readers.remove(key.channel());
        cancelClient(key);
    }

    private void handleAccept(SelectionKey key) throws IOException, ClosedChannelException
    {
        SelectableChannel socketChannel = key.channel();
        LOG.trace("{} is acceptable, a client is connecting.", key.attachment());
        SocketChannel clientChannel = serverChannel.accept();
        LOG.trace("Socket accepted {}", socketChannel);
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, OP_READ, "client " + (conId++));
    }
    
    private void handleCompleteMsg(MessageReader<MsgType> reader, SelectionKey key)
    {
        if(key.isValid())
        {
            if(handler == null)
            {
                LOG.warn("No handlers for {}. Message will be discarded.", key.attachment());
                return;
            }
            writeBuffer.clear();
            
//            int size = handler.execute(reader.getMessage().get(), writeBuffer);
//            responseWriters.put(key.channel(), new ByteBufferWriter((SocketChannel) key.channel(), size));
            Optional<ByteBuffer> resultToWrite = handler.execute(reader.getMessage().get());
            if(resultToWrite.isPresent())
            {
                responseWriters.put(key.channel(), new ByteBufferWriter((SocketChannel) key.channel(), resultToWrite.get()));
                key.interestOps(key.interestOps() | OP_WRITE);
            }
            readers.remove(key.channel());
        }
    }

    private void clearReadBuffers()
    {
        headerBuffer.clear();
        bodyBuffer.clear();
    }

    private void cancelClient(SelectionKey key)
    {
        key.cancel();
        try
        {
            key.channel().close();
        }
        catch (IOException e)
        {
            LOG.warn("Exception closing channel of cancelled client {}. The key has been canceelled so the " +
                     "Server won't interact with it anymore regardless.", key.attachment());
        }
    }
}
