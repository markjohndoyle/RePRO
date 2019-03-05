package org.mjd.sandbox.nio;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.apache.commons.lang3.tuple.Pair;
import org.awaitility.Duration;
import org.junit.runner.RunWith;
import org.mjd.sandbox.nio.handlers.message.MessageHandler;
import org.mjd.sandbox.nio.handlers.message.SubscriptionInvoker;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.message.factory.KryoRpcRequestMsgFactory;
import org.mjd.sandbox.nio.support.FakeRpcTarget;
import org.mjd.sandbox.nio.util.ArgumentValues;
import org.mjd.sandbox.nio.util.kryo.KryoRpcUtils;
import org.mjd.sandbox.nio.util.kryo.RpcRequestKryoPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.afterEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.beforeEach;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.TEN_SECONDS;
import static org.mjd.sandbox.nio.handlers.response.provided.RpcRequestRefiners.prepend;
import static org.mjd.sandbox.nio.support.ResponseReader.readResponse;

/**
 * RPC integration test using a single client that makes multiple asynchronous calls.
 */
@RunWith(OleasterRunner.class)
public class ServerRpcSingleClientSubscribeIT
{
    private static final Logger LOG = LoggerFactory.getLogger(ServerRpcSingleClientSubscribeIT.class);
    private ExecutorService serverService;
    private Server<RpcRequest> rpcServer;
    private FakeRpcTarget rpcTarget;
    private Socket clientSocket;
    private AtomicLong reqId;
    private Pool<Kryo> kryos = new RpcRequestKryoPool(true, false, 1000);
    private MessageHandler<RpcRequest> rpcInvoker;

	// TEST BLOCK
    {
        beforeEach(()-> {
        	rpcTarget = new FakeRpcTarget();
        	rpcInvoker = new SubscriptionInvoker(kryos.obtain(), rpcTarget);
            serverService = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Server").build());
            startServer();
        });

        afterEach(() -> {
        	rpcTarget.close();
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
        	describe("requests a subscription", () -> {
        		describe("and waits for at least 5 notifications", () -> {
		        	it("it should recieve all of the notifications", () -> {
		        		final int numNotifications = 5;
		        		DataOutputStream clientOut = new DataOutputStream(clientSocket.getOutputStream());

		        		long subscriptionId = reqId.getAndIncrement();
	        			makeRpcCall(clientOut, "subscribe", ArgumentValues.none(), subscriptionId);

	        			Kryo kryo = kryos.obtain();

		        		DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream());
		        		ExecutorService receiverService = Executors.newSingleThreadExecutor();
		        		Future<?> receiverJob = receiverService.submit(() -> {
		        			int notifications = 0;
		        			while(notifications <  numNotifications) {
			        			try {
			        				Pair<Long, String> readResponse = readResponse(kryo, dataIn);
			        				if(readResponse.getLeft() == subscriptionId) {
			        		    		expect(readResponse.getLeft()).toEqual(subscriptionId);
			        		    		expect(readResponse.getRight()).toStartWith("Things just seem so much better in theory than in practice");
			        		    		notifications++;
			        		    	}
								}
								catch (IOException e) {
									e.printStackTrace();
								}
		        			}
		        		});

		        		receiverJob.get();
		        		dataIn.close();
		        		clientOut.close();
		        		kryos.free(kryo);
		        	});
	        	});
        	});
        });
    }

	private RpcRequest makeRpcCall(DataOutputStream clientOut, String methodName, ArgumentValues args, final long id)
			throws IOException {
		Kryo kryo = kryos.obtain();
		try {
			RpcRequest request = new RpcRequest(id, methodName, args);
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
        rpcServer = new Server<>(new KryoRpcRequestMsgFactory());
        rpcServer.addHandler(rpcInvoker::handle)
        		 .addHandler(prepend::requestId);

        serverService.submit(() -> rpcServer.start());
        await().until(() -> rpcServer.isAvailable());
    }

	private void shutdownServer()
    {
        LOG.info("Test is shutting down server....");
        serverService.shutdownNow();
        await().atMost(Duration.TEN_SECONDS).until(() -> { return rpcServer.isShutdown();});
        await().atMost(Duration.TEN_SECONDS).until(() -> { return serverService.isTerminated();});
    }

}

