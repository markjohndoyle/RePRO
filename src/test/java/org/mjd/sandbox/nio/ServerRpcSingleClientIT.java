package org.mjd.sandbox.nio;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.Duration;
import org.junit.runner.RunWith;
import org.mjd.sandbox.nio.handlers.message.RpcRequestInvoker;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.message.factory.KryoRpcRequestMsgFactory;
import org.mjd.sandbox.nio.support.FakeRpcTarget;
import org.mjd.sandbox.nio.util.ArgumentValues;
import org.mjd.sandbox.nio.util.ArgumentValues.ArgumentValuePair;
import org.mjd.sandbox.nio.util.kryo.KryoRpcUtils;
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
    private FakeRpcTarget rpcTarget;
    private Socket clientSocket;
    private AtomicLong reqId;

    private Pool<Kryo> kryos = new Pool<Kryo>(true, false, 10000) {
        @Override
        protected Kryo create () {
            Kryo kryo = new Kryo();
            kryo.register(RpcRequest.class);
            kryo.register(ArgumentValues.class);
            kryo.register(ArgumentValuePair.class);
            return kryo;
        }
    };

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

        describe("When a single client", () -> {
        	beforeEach(() -> {
        		reqId = new AtomicLong();
        		clientSocket = new Socket("localhost", 12509);
        		await().atMost(TEN_SECONDS.multiply(12)).until(() -> clientSocket.isConnected());
        		LOG.debug("Client isConnected!");
        	});
        	afterEach(() -> {
        		clientSocket.close();
        	});
        	describe("sends multiple different consecutive kryo RPC request/reply RpcRequests asynchronously", () -> {
	        	it("it should recieve all of the responses", () -> {
	        		final int numCalls = FakeRpcTarget.methodNamesAndReturnValues.size();
	        		ExecutorService executor = Executors.newFixedThreadPool(numCalls);

	        		DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());
	        		LOG.trace("Running client");
	        		ConcurrentHashMap<Long, Future<?>> calls = new ConcurrentHashMap<>();

	        		FakeRpcTarget.methodNamesAndReturnValues.forEach((methodName, returnValue) -> {
	        			final long id = reqId.getAndIncrement();
	        			calls.put(id, executor.submit(() -> makeRpcCall(clientOut, methodName, ArgumentValues.none(), id)));
	        		});

	        		// Fire off a few method calls with args
	        		long argCallId = reqId.getAndIncrement();
	        		calls.put(argCallId, executor.submit(() ->
	        		makeRpcCall(clientOut, "hackTheGibson",  ArgumentValues.of("password", 543), argCallId)));

	        		long argCallId2 = reqId.getAndIncrement();
	        		calls.put(argCallId2, executor.submit(() ->
	        		makeRpcCall(clientOut, "hackTheGibson",  ArgumentValues.of("password", 999), argCallId2)));

	        		long argCallId3 = reqId.getAndIncrement();
	        		calls.put(argCallId3, executor.submit(() ->
	        		makeRpcCall(clientOut, "hackTheGibson",  ArgumentValues.of("password", 98995786), argCallId3)));

	        		DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream());

	        		Kryo kryo = kryos.obtain();
	        		for(int i = 0; i < calls.size(); i++) {
	        			Pair<Long, String> response = readResponse(kryo, dataIn);
	        			final Long responseId = response.getLeft();
	        			Future<?> call = calls.get(responseId);
	        			RpcRequest requestMade = (RpcRequest) call.get();
	        			expect(responseId).toEqual(requestMade.getId());
	        		}
	        		kryos.free(kryo);
	        	});
        	});
        	describe("sends a large amount of consecutive kryo RPC request/reply RpcRequests asynchronously", () -> {
	        	it("many version - it should recieve all of the responses", () -> {
	        		final int numCalls = 5000;
	        		ExecutorService executor = Executors.newFixedThreadPool(50);

	        		DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());
	        		ConcurrentHashMap<Long, Future<?>> calls = new ConcurrentHashMap<>();

	        		for(int i = 0; i < numCalls; i++) {
	        			long argCallId = reqId.getAndIncrement();
	        			calls.put(argCallId, executor.submit(() ->
	        			makeRpcCall(clientOut, "hackTheGibson",  ArgumentValues.of("password", 543), argCallId)));
	        		}

	        		DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream());

	        		Kryo kryo = kryos.obtain();
	        		for(int i = 0; i < numCalls; i++) {
	        			Pair<Long, String> response = readResponse(kryo, dataIn);
	        			final Long responseId = response.getLeft();
	        			Future<?> call = calls.get(responseId);
	        			RpcRequest requestMade = (RpcRequest) call.get();
	        			expect(responseId).toEqual(requestMade.getId());
	        		}
	        		kryos.free(kryo);
	        	});
        	});
        });
    }

	private RpcRequest makeRpcCall(DataOutputStream clientOut, String methodName, ArgumentValues args, final long id) {
		Kryo kryo = kryos.obtain();
		RpcRequest request = new RpcRequest(id, methodName, args);
		try {
			LOG.debug("Preparing to call request {}", id);
			KryoRpcUtils.writeKryoWithHeader(kryo, clientOut, request).flush();
			LOG.trace("Request {} written to server from client", request);
			return request;
		}
		catch (IOException e) {
			LOG.error(e.toString());
			e.printStackTrace();
			return RpcRequest.ERROR;
		}
		finally {
			kryos.free(kryo);
		}
	}

    private void startServer()
    {
        rpcServer = new Server<>(new KryoRpcRequestMsgFactory());

        rpcServer.addHandler(new RpcRequestInvoker(kryos.obtain(), rpcTarget))
        		 .addHandler((rpcRequest, writeBuffer) -> ByteBuffer.allocate(Long.BYTES + writeBuffer.capacity())
				  										            .putLong(rpcRequest.getId())
				  										            .put(writeBuffer));

        serverService.submit(() -> rpcServer.start());

        await().until(() -> rpcServer.isAvailable());
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

}

