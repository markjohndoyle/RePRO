package org.mjd.sandbox.nio;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.awaitility.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mjd.sandbox.nio.message.IntMessage;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.factory.MessageFactory;
import org.mjd.sandbox.nio.message.factory.MessageFactory.MessageCreationException;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ServerIT
{
    private final ExecutorService serverService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Server").build());
    
    private Server<Integer> integerMessageServer;
    
    private static final String TEST_RSP_MSG  = "FakeResult from call message: "; 
    
    @Before
    public void startServer()
    {
        try
        {
            integerMessageServer = new Server<>(new MessageFactory<Integer>()
            {
                @Override public int getHeaderSize() { return Integer.BYTES; }
                @Override public Message<Integer> create(byte[] bytesRead) { 
                    return new IntMessage(Ints.fromByteArray(bytesRead)); 
                }
            });

            // Add echo handler
            integerMessageServer.addHandler(new RespondingHandler<Integer>() {
                @Override 
                public Optional<ByteBuffer> execute(Message<Integer> message) { 
                    String rsp = TEST_RSP_MSG + message.getValue();
                    byte[] msgBytes = rsp.getBytes();
                    return Optional.of((ByteBuffer)ByteBuffer.allocate(msgBytes.length).put(msgBytes).flip());
                }

                @Override
                public int execute(Message<Integer> message, ByteBuffer writeBuffer)
                {
                    String rsp = TEST_RSP_MSG + message.getValue();
                    byte[] msgBytes = rsp.getBytes();
                    writeBuffer.put(msgBytes).flip();
                    return msgBytes.length;
                }
            });

            serverService.submit(() -> {
                try
                {
                    integerMessageServer.start();
                }
                catch (IOException | MessageCreationException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            });
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    @After
    public void shutdownServer() throws IOException, InterruptedException
    {
        serverService.shutdownNow();
        await().until(() -> { return integerMessageServer.isShutdown();});
        await().until(() -> { return serverService.isTerminated();});
    }
    
    /**
     * Response is as follows:
     * 
     *   ---------------------------------
     *  | header [1Byte] | body [n Bytes] |
     *  |   msgSize      |      msg       |
     *   ---------------------------------
     *   
     * @param in
     * @return
     * @throws IOException
     */
    private String readResponse(DataInputStream in) throws IOException
    {
        await("Waiting for response").atMost(Duration.TEN_SECONDS).until(() -> { return in.available() > Integer.BYTES;});
        int responseSize = in.readInt();
        byte[] bytesRead = new byte[responseSize];
        int totalRead = 0;
        int bodyRead = 0;
        while((bodyRead = in.read(bytesRead, bodyRead, responseSize - bodyRead)) > 0)
        {
            // We don't want to potentially head into a further message.
            totalRead += bodyRead;
            if(totalRead == responseSize)
            {
                break;
            }
            // else there is more to read
        }
        return new String(bytesRead);
    }

    @Test
    public void test() throws UnknownHostException, IOException, InterruptedException
    {
        Socket testSocket = new Socket("localhost", 12509);
        DataInputStream in = new DataInputStream(testSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(testSocket.getOutputStream());
        out.writeInt(Integer.BYTES);
        out.writeInt(5);
        out.flush();
        
        assertThat(readResponse(in), is("FakeResult from call message: 5"));
        
        out.close();
        testSocket.close();
    }

    @Test
    public void testStaggered() throws UnknownHostException, IOException, InterruptedException
    {
        Socket testSocket = new Socket("localhost", 12509);
        DataInputStream in = new DataInputStream(testSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(testSocket.getOutputStream());
        
        out.writeInt(Integer.BYTES);
        ByteBuffer fiveBuffer = ByteBuffer.allocate(Integer.BYTES).putInt(5);
        for(byte b : fiveBuffer.array())
        {
            out.write(b);
            out.flush();
        }
        
        assertThat(readResponse(in), is("FakeResult from call message: 5"));
        
        out.close();
        testSocket.close();
    }
    
    @Test
    public void testStaggeredOverflow() throws UnknownHostException, IOException, InterruptedException
    {
        Socket testSocket = new Socket("localhost", 12509);
        DataInputStream in = new DataInputStream(testSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(testSocket.getOutputStream());
        
        out.writeInt(Integer.BYTES);
        ByteBuffer testValueBuffer = ByteBuffer.allocate(Integer.BYTES).putInt(234);

        // Stagger write the first 3 bytes
        for(int i = 0; i < testValueBuffer.capacity() - 1; i++)
        {
            out.write(testValueBuffer.array()[i]);
            out.flush();
        }
        // write the rest plus overflow
        out.write(new byte[]{testValueBuffer.get(3), 0x0F, 0x0F, 0x0F, 0x0F, 0x0F});
        
        assertThat(readResponse(in), is("FakeResult from call message: 234"));
        
        out.close();
        testSocket.close();
    }

}
