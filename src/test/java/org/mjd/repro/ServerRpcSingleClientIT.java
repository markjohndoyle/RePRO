package org.mjd.repro;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import com.esotericsoftware.kryo.Kryo;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.runner.RunWith;
import org.mjd.repro.handlers.factories.RpcHandlers;
import org.mjd.repro.handlers.message.MessageHandler;
import org.mjd.repro.message.RpcRequest;
import org.mjd.repro.message.factory.MarshallerMsgFactory;
import org.mjd.repro.serialisation.Marshaller;
import org.mjd.repro.support.FakeRpcTarget;
import org.mjd.repro.support.KryoMarshaller;
import org.mjd.repro.support.KryoPool;
import org.mjd.repro.support.KryoRpcUtils;
import org.mjd.repro.support.RpcKryo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.afterEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.before;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.ONE_MINUTE;
import static org.awaitility.Duration.TEN_SECONDS;
import static org.mjd.repro.handlers.response.provided.RpcRequestRefiners.prepend;
import static org.mjd.repro.support.ResponseReader.readResponse;

/**
 * RPC integration test using a single client that makes multiple asynchronous calls.
 */
@RunWith(OleasterRunner.class)
public class ServerRpcSingleClientIT
{
    private static final Logger LOG = LoggerFactory.getLogger(ServerRpcSingleClientIT.class);
    private static final AtomicLong reqId = new AtomicLong();
    private final KryoPool kryos = KryoPool.newThreadSafePool(1000, RpcKryo::configure);
    private final Marshaller marshaller = new KryoMarshaller(1000, RpcKryo::configure);
    private ExecutorService serverService;
    private Server<RpcRequest> rpcServer;
    private FakeRpcTarget rpcTarget;
    private Socket clientSocket;
    private MessageHandler<RpcRequest> rpcInvoker;

	// TEST BLOCK
    {
        before(()->{
        	rpcTarget = new FakeRpcTarget();
        	rpcInvoker = RpcHandlers.singleThreadRpcInvoker(marshaller, rpcTarget);
            serverService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Server").build());
        });

        describe("When a single client", () -> {
        	beforeEach(() -> {
        		startServer();
        		clientSocket = new Socket("localhost", rpcServer.getPort());
        		await().atMost(TEN_SECONDS.multiply(12)).until(() -> clientSocket.isConnected());
        		LOG.debug("Client isConnected!");
        	});
        	afterEach(() -> {
        		clientSocket.close();
        		shutdownServer();
        	});
        	describe("sends multiple different consecutive kryo RPC request/reply RpcRequests asynchronously", () -> {
	        	it("it should recieve all of the responses", () -> {
	        		final int numCalls = FakeRpcTarget.methodNamesAndReturnValues.size();
	        		final ExecutorService executor = Executors.newFixedThreadPool(numCalls);

	        		final DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());
	        		LOG.trace("Running client");
	        		final ConcurrentHashMap<Long, Future<?>> calls = new ConcurrentHashMap<>();

	        		FakeRpcTarget.methodNamesAndReturnValues.forEach((methodName, returnValue) -> {
	        			final long id = reqId.getAndIncrement();
	        			calls.put(id, executor.submit(() -> makeRpcCall(clientOut, id, methodName)));
	        		});

	        		// Fire off a few method calls with args
	        		final long argCallId = reqId.getAndIncrement();
	        		calls.put(argCallId,
	        				  executor.submit(() -> makeRpcCall(clientOut, argCallId, "hackTheGibson", 543)));

	        		final long argCallId2 = reqId.getAndIncrement();
	        		calls.put(argCallId2,
	        				  executor.submit(() -> makeRpcCall(clientOut, argCallId2, "hackTheGibson", 999)));

	        		final long argCallId3 = reqId.getAndIncrement();
	        		calls.put(argCallId3,
	        				  executor.submit(() -> makeRpcCall(clientOut, argCallId3, "hackTheGibson",  98995786)));

	        		final DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream());

	        		final Kryo kryo = kryos.obtain();
	        		for(int i = 0; i < calls.size(); i++) {
	        			LOG.debug("Reading response; iteration {} ", i);
	        			final Pair<Long, Object> response = readResponse(kryo, dataIn);
	        			final Long responseId = response.getLeft();
	        			LOG.debug("Got reponse {}; getting the call with that ID", responseId);
	        			final Future<?> call = calls.get(responseId);
	        			LOG.debug("Got request from call future...");
	        			final RpcRequest requestMade = (RpcRequest) call.get();
	        			expect(responseId).toEqual(requestMade.getId());
	        			LOG.info("Asserted; iteration {} ", i);
	        		}
	        		kryos.free(kryo);
	        	});
        	});
        	describe("sends a large amount of consecutive kryo RPC request/reply RpcRequests asynchronously", () -> {
	        	it("it should recieve all of the responses", () -> {
	        		final int numCalls = 5000;
	        		final ExecutorService executor = Executors.newFixedThreadPool(50);

	        		final DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());
	        		final ConcurrentHashMap<Long, Future<?>> calls = new ConcurrentHashMap<>();

	        		for(int i = 0; i < numCalls; i++) {
	        			final long argCallId = reqId.getAndIncrement();
	        			calls.put(argCallId,
	        					  executor.submit(() -> makeRpcCall(clientOut, argCallId, "hackTheGibson", 543)));
	        		}

	        		final DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream());

	        		final Kryo kryo = kryos.obtain();
	        		for(int i = 0; i < numCalls; i++) {
	        			final Pair<Long, Object> response = readResponse(kryo, dataIn);
	        			final Long responseId = response.getLeft();
	        			final Future<?> call = calls.get(responseId);
	        			final RpcRequest requestMade = (RpcRequest) call.get();
	        			expect(responseId).toEqual(requestMade.getId());
	        			LOG.info("Asserted {} ", i);
	        		}
	        		kryos.free(kryo);
	        	});
        	});
        	describe("sends a void return method request", () -> {
        		it("it should recieve an empty response as an acknowledgement", () -> {
        			final DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());
        			final RpcRequest sentRequest = makeRpcCall(clientOut, 1701L, "callMeVoid");

        			final DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream());
        			final Kryo kryo = kryos.obtain();
        			final Pair<Long, Object> response = readResponse(kryo, dataIn);

        			expect(response.getLeft()).toEqual(sentRequest.getId());
        			expect(response.getRight()).toBeNull();
        		});
        	});
        });
    }

	private RpcRequest makeRpcCall(final DataOutputStream clientOut, final long id, final String methodName,
			final Object... args) throws IOException
    {
    	final Kryo kryo = kryos.obtain();
    	try {
    		final RpcRequest request = new RpcRequest(id, methodName, args);
    		LOG.debug("Preparing to call request {}", id);
    		KryoRpcUtils.writeKryoWithHeader(kryo, clientOut, request).flush();
    		LOG.trace("Request {} written to server from client", request);
    		return request;
    	}
    	finally {
    		kryos.free(kryo);
    	}
    }

    private void startServer()
    {
        rpcServer = new Server<>(new MarshallerMsgFactory<>(marshaller, RpcRequest.class));

        rpcServer.addHandler(rpcInvoker::handle)
        		 .addHandler(prepend::requestId);

        serverService.submit(() -> rpcServer.start());

        await().until(() -> rpcServer.isAvailable());
    }

	private void shutdownServer()
    {
        LOG.info("Test is shutting down server....");
        serverService.shutdownNow();
        await().atMost(ONE_MINUTE).until(() -> { return rpcServer.isShutdown();});
        await().atMost(ONE_MINUTE).until(() -> { return serverService.isTerminated();});
    }

}

