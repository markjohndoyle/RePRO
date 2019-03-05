package org.mjd.sandbox.nio.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.mjd.sandbox.nio.handlers.message.SubscriptionRegistrar;
import org.mjd.sandbox.nio.handlers.message.SubscriptionRegistrar.Subscriber;


/**
 * Fake target for RPC calls. A server will have direct access to something like this
 * when it's setup.
 */
public final class FakeRpcTarget implements AutoCloseable
{
    public static final Map<String, Object> methodNamesAndReturnValues = new HashMap<>();
    private final ExecutorService generator = Executors.newSingleThreadExecutor();
    private final List<Subscriber> subs = Collections.synchronizedList(new ArrayList<>());
    private final Random random = new Random();

    public FakeRpcTarget() {
		methodNamesAndReturnValues.put("callMeString", "callMeString");
		methodNamesAndReturnValues.put("genString", "generated string");
		methodNamesAndReturnValues.put("whatIsTheString", "it's this string...");
		generator.execute(this::generateNotifications);
	}

    public void callMeVoid() { }
    public String callMeString() { return "callMeString"; }
    public String genString() { return "generated string"; }
    public String whatIsTheString() { return "it's this string..."; }
    public String hackTheGibson(int password) { return "password sent: " + password; }

    @SubscriptionRegistrar
	public void subscribe(Subscriber sub) {
    	System.err.println("Adding sub " + sub);
    	subs.add(sub);
    }

    private void generateNotifications() {
    	while(true) {
	    	for(Subscriber sub :subs) {
	    		System.err.println("GOT A SUB " + sub + "- NOTFIYING");
	    		sub.receive("Things just seem so much better in theory than in practice..." + random.nextLong());
	    	}
	    	try {
	    		Thread.sleep(250);
	    	}
	    	catch (InterruptedException e) {
	    		Thread.currentThread().interrupt();
	    	}
    	}
    }

	@Override
	public void close() throws Exception {
		subs.clear();
	}
}


