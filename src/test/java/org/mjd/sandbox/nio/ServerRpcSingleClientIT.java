package org.mjd.sandbox.nio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.Duration;
import org.junit.runner.RunWith;
import org.mjd.sandbox.nio.message.Message;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.message.RpcRequestMessage;
import org.mjd.sandbox.nio.message.factory.MessageFactory;
import org.mjd.sandbox.nio.support.FakeRpcTarget;
import org.mjd.sandbox.nio.support.KryoRpcUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.after;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.afterEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.TEN_SECONDS;

@RunWith(OleasterRunner.class)
public class ServerRpcSingleClientIT
{
    private static final Logger LOG = LoggerFactory.getLogger(ServerRpcSingleClientIT.class);

    private ExecutorService serverService;

    private Server<RpcRequest> rpcServer;

    private static AtomicLong reqId = new AtomicLong();

    private FakeRpcTarget rpcTarget;

    private final Pool<Kryo> kryos = new Pool<Kryo>(true, false, 10) {
        @Override
        protected Kryo create () {
            Kryo kryo = new Kryo();
            kryo.register(RpcRequest.class);
            return kryo;
        }
    };

    private Socket clientSocket = new Socket();

    // TEST BLOCK
    {
        before(()->{
        	rpcTarget = new FakeRpcTarget();
            serverService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Server").build());
            startServer();
        });

        after(() -> {
            shutdownServer();
        });

        describe("When a client sends multiple kryo RPC request/reply RpcRequests asynchronously", () -> {
        	beforeEach(() -> {
        		clientSocket.connect(new InetSocketAddress("localhost", 12509), 512000);
        		await().atMost(TEN_SECONDS.multiply(12)).until(() -> clientSocket.isConnected());
        		LOG.trace("Client isConnected!");
        	});
        	afterEach(() -> {
        		clientSocket.close();
        	});
        	it("it should recieve all of the responses", () -> {
        		final int numCalls = FakeRpcTarget.methodNamesAndReturnValues.size();
        		ExecutorService executor = Executors.newFixedThreadPool(numCalls);

        		DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());
				LOG.trace("Running client");
				ConcurrentHashMap<Long, Future<?>> calls = new ConcurrentHashMap<>();

				FakeRpcTarget.methodNamesAndReturnValues.forEach((methodName, returnValue) -> {
					final long id = reqId.getAndIncrement();
					calls.put(id, executor.submit(() -> {
						Kryo kryo = kryos.obtain();
						LOG.debug("Preparing to call request {}", id);
						RpcRequest request = new RpcRequest(id, methodName);
						try {
							KryoRpcUtils.writeKryoWithHeader(kryo, clientOut, request).flush();
							LOG.trace("Request {} written to server from client", request);
							return request;
						}
						catch (IOException e) {
							e.printStackTrace();
							return RpcRequest.ERROR;
						}
						finally {
							kryos.free(kryo);
						}
					}));

				});

				DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream());

				for(int i = 0; i < numCalls; i++) {
					Pair<Long, String> response = readResponse(kryos.obtain(), dataIn);
					final Long responseId = response.getLeft();
					Future<?> call = calls.get(responseId);
					RpcRequest requestMade = (RpcRequest) call.get();
					expect(responseId).toEqual(requestMade.getId());
				}
        	});
        });
    }

    private void startServer()
    {
        rpcServer = new Server<>(new RpcRequestMsgFactory());

        // Add RPC to rpcTarget handler
        // The handler is what the server will do with your messages.
        rpcServer.addHandler((RespondingMessageHandler<RpcRequest>) message -> {
		    byte[] msgBytes;
		    Object result;
		    RpcRequest request = message.getValue();
		    Kryo kryo = kryos.obtain();
		    try
		    {
		        String requestedMethodCall = request.getMethod();
		        result = MethodUtils.invokeMethod(rpcTarget, requestedMethodCall);
		        if(result == null)
		        {
		            return Optional.empty();
		        }
		    }
		    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException e1)
		    {
		        result = new String("Error executing method: " + request.getMethod() + " due to " + e1);
		    }
		    try
		    {
		        msgBytes = objectToKryoBytes(kryo, result);
		    }
		    catch (IOException e)
		    {
		        e.printStackTrace();
		        msgBytes = new byte[] {0x00};
		    }
		    finally
		    {
		    	kryos.free(kryo);
		    }
		    return Optional.of(ByteBuffer.allocate(msgBytes.length).put(msgBytes));
		}).addHandler((rpcRequest, writeBuffer) -> {
				LOG.trace("id handler tagging repsonse with ID {}", rpcRequest.getId());
						return ByteBuffer.allocate(Long.BYTES + writeBuffer.capacity())
				  										.putLong(rpcRequest.getId())
				  										.put(writeBuffer);
		});

        serverService.submit(() -> {
            try
            {
                rpcServer.start();
            }
            catch (Exception e)
            {
                LOG.error("ERROR IN SERVER, see following stack trace:");
                LOG.error(Joiner.on(System.lineSeparator()).join(e.getStackTrace()));
                e.printStackTrace();
            }
        });
        await().until(() -> { return rpcServer.isAvailable();});
    }

    private static byte[] objectToKryoBytes(Kryo kryo, Object obj) throws IOException
	{
	    try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
	        Output kryoByteArrayOut = new Output(bos))
	    {
	        kryo.writeObject(kryoByteArrayOut, obj);
	        kryoByteArrayOut.flush();
	        return bos.toByteArray();
	    }
	}

	private void shutdownServer()
    {
        LOG.debug("Test is shutting down server....");
        serverService.shutdownNow();
        await().atMost(Duration.TEN_SECONDS).until(() -> { return rpcServer.isShutdown();});
        await().atMost(Duration.TEN_SECONDS).until(() -> { return serverService.isTerminated();});
    }

	/**
	 * Response is as follows:
	 *
	 * <pre>
	 *   ---------------------------------
	 *  | header [1Byte] | body [n Bytes] |
	 *  |   msgSize      |      msg       |
	 *   ---------------------------------
	 * </pre>
	 *
	 * @param kryo
	 *
	 * @param in
	 * @return
	 * @throws IOException
	 */
	private static Pair<Long, String> readResponse(Kryo kryo, DataInputStream in) throws IOException {
		int responseSize;
		long requestId;
		int messageSizeAfterId;
		try {
			responseSize = in.readInt();
			requestId = in.readLong();
			messageSizeAfterId = responseSize - Long.BYTES;
			LOG.trace("Response size is {} and request ID is {}. Message size (after ID) is {}", responseSize, requestId, messageSizeAfterId);
		}
		catch (IOException e) {
			LOG.error("Error reading header client-side due to {}", e.toString());
			e.printStackTrace();
			throw e;
		}
		byte[] bytesRead = new byte[messageSizeAfterId];
		int bodyRead = 0;
		LOG.trace("Reading response of size: {}", messageSizeAfterId);
		try {
			while ((bodyRead = in.read(bytesRead, bodyRead, messageSizeAfterId - bodyRead)) > 0) {
				// Just keep reading
			}
		}
		catch (IOException e) {
			LOG.error("Error reading body client-side");
			e.printStackTrace();
			throw e;
		}
		try (Input kin = new Input(bytesRead)) {
			LOG.trace("Deserialising repsonse message to String via kryo");
			String result = kryo.readObject(kin, String.class);
			return Pair.of(requestId, result);
		}
	}

	private static final class RpcRequestMsgFactory implements MessageFactory<RpcRequest>
	{
		private static final Logger REQLOG = LoggerFactory.getLogger(RpcRequestMsgFactory.class);
	    private final Kryo kryo = new Kryo();

	    public RpcRequestMsgFactory()
	    {
	        kryo.register(RpcRequest.class);
	    }

	    @Override
	    public int getHeaderSize() { return Integer.BYTES; }

	    /**
	     * Expects a Kryo object with a marshalled RpcRequest
	     */
	    @Override
	    public Message<RpcRequest> create(byte[] bytesRead) throws MessageCreationException {
	        try {
	            RpcRequest request = readBytesWithKryo(kryo, bytesRead);
	            REQLOG.debug("Message factory created {}", request);
	            return new RpcRequestMessage(request);
	        }
	        catch (IOException | IndexOutOfBoundsException e) {
	            System.err.println("[" + Thread.currentThread().getName() + "]" + " Kryo: " + kryo + " " + e.toString());
	            e.printStackTrace();
	            throw new MessageCreationException(e);
	        }
	    }

	    private static RpcRequest readBytesWithKryo(Kryo kryo, byte[] data) throws IOException
	    {
	        try(ByteArrayInputStream bin = new ByteArrayInputStream(data);
	            Input kryoByteArrayIn = new Input(bin))
	        {
	            RpcRequest req = kryo.readObject(kryoByteArrayIn, RpcRequest.class);
	            return req;
	        }
	        catch (IOException e)
	        {
	            REQLOG.error("Error deserialising response from server", e);
	            throw e;
	        }
	    }
	}

}

