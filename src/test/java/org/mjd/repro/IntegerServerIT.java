package org.mjd.repro;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;
import org.mjd.repro.handlers.message.MessageHandler.ConnectionContext;
import org.mjd.repro.handlers.message.ResponseMessage;
import org.mjd.repro.util.kryo.KryoPool;
import org.mjd.repro.util.kryo.KryoRpcUtils;
import org.mjd.repro.util.kryo.RpcKryo;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.afterEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.TEN_SECONDS;

@RunWith(OleasterRunner.class)
public final class IntegerServerIT
{
    private static final String TEST_RSP_MSG  = "FakeResult from call message: ";
    private ExecutorService serverService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Server").build());
    private Socket testSocket;
    private DataInputStream socketIn;
    private DataOutputStream socketOut;
    private Server<Integer> integerMessageServer;
//    private final RpcRequestKryoPool kryos = new RpcRequestKryoPool(true, false, 500);
    private final KryoPool kryos = KryoPool.newThreadSafePool(500, RpcKryo::configure);
    private int serverPort;

    // TEST BLOCK
    {
        beforeEach(() -> {
            serverService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Server").build());
            startServer();
            await().atMost(TEN_SECONDS).until(() -> { return integerMessageServer.isAvailable();});
            testSocket = new Socket("localhost", serverPort);
            socketIn = new DataInputStream(testSocket.getInputStream());
            socketOut = new DataOutputStream(testSocket.getOutputStream());
        });

        afterEach(()-> {
            socketOut.close();
            shutdownServer();
            await().atMost(TEN_SECONDS).until(() -> { return integerMessageServer.isShutdown();});
            await().atMost(TEN_SECONDS).until(() -> { return serverService.isTerminated();});
        });

        describe("When a valid request is sent to the server", () -> {
            it("should respond with the expected message as defined by this test servers handler", () -> {
               final int requestMsg = 5;
               socketOut.writeInt(Integer.BYTES);
               socketOut.writeInt(requestMsg);
               socketOut.flush();
        	   expect(serversResponseFrom(socketIn)).toEqual(TEST_RSP_MSG + requestMsg);
           });

           describe("in multiple writes", () -> {
               it("should respond with the expected message as defined by this test servers handler", () -> {
                   final int requestMsg = 10;
                   socketOut.writeInt(Integer.BYTES);
                   final ByteBuffer fiveBuffer = ByteBuffer.allocate(Integer.BYTES).putInt(requestMsg);
                   for(final byte b : fiveBuffer.array())
                   {
                       socketOut.write(b);
                       socketOut.flush();
                   }
            	   expect(serversResponseFrom(socketIn)).toEqual(TEST_RSP_MSG + requestMsg);
               });

               describe("and the client overflows, i.e., writes too many bytes", () -> {
                   it("should respond with the expected message as defined by this test servers handler", () -> {
                       final int requestMsg = 234;
                       socketOut.writeInt(Integer.BYTES);
                       final ByteBuffer testValueBuffer = ByteBuffer.allocate(Integer.BYTES).putInt(requestMsg);

                       // Stagger write the first 3 bytes
                       for(int i = 0; i < testValueBuffer.capacity() - 1; i++)
                       {
                           socketOut.write(testValueBuffer.array()[i]);
                           socketOut.flush();
                       }
                       // write the rest plus overflow
                       socketOut.write(new byte[]{testValueBuffer.get(3), 0x01, 0x0A, 0x07, 0x03, 0x00,
                    		   											  0x0F, 0x0F, 0x0F, 0x0F, 0x0F});
                       socketOut.flush();

                	   expect(serversResponseFrom(socketIn)).toEqual(TEST_RSP_MSG + requestMsg);
                   });
               });
           });
        });
    }

    public void startServer()
    {
        integerMessageServer = new Server<>(bytesRead -> Ints.fromByteArray(bytesRead));
        serverPort = integerMessageServer.getPort();

        // Add echo handler
        integerMessageServer.addHandler((final ConnectionContext<Integer> context, final Integer message) -> {
        	final ExecutorService executor = MoreExecutors.newDirectExecutorService();
        	final Kryo kryo = kryos.obtain();
        	return executor.submit(() -> {
	        	try {
				    final String rsp = TEST_RSP_MSG + message;
				    final ResponseMessage<String> responseMessage = new ResponseMessage<>(0, rsp);
				    final byte[] rspMsgBytes = KryoRpcUtils.objectToKryoBytes(kryo, responseMessage);

					return Optional.of(ByteBuffer.wrap(rspMsgBytes));
	        	}
	        	finally {
	        		kryos.free(kryo);
	        	}
        	});
		});

        serverService.submit(() -> { integerMessageServer.start(); return null; });
    }

    public void shutdownServer()
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
    private String serversResponseFrom(final DataInputStream in) throws IOException
    {
        await("Waiting for response").atMost(TEN_SECONDS).until(() -> { return in.available() > Integer.BYTES;});
        final int responseSize = in.readInt();
        final byte[] bytesRead = new byte[responseSize];
        int totalRead = 0;
        int bodyRead = 0;
        while((bodyRead = in.read(bytesRead, bodyRead, responseSize - bodyRead)) > 0)
        {
            totalRead += bodyRead;
            if(totalRead == responseSize) // necessary? the read shouldn't go too far
            {
            	// We don't want to potentially head into a further message.
                break;
            }
            // else there is more to read
        }
        final Kryo kryo = kryos.obtain();
        try (Input kin = new Input(bytesRead)) {
	        final ResponseMessage<String> responseMessage = kryo.readObject(kin, ResponseMessage.class);
	        if(responseMessage.isError()) {
	        	return responseMessage.getError().get().toString();
	        }
	        return responseMessage.getValue().get();
        }
        finally {
			kryos.free(kryo);
		}
    }
}
