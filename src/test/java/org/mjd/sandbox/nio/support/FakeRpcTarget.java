package org.mjd.sandbox.nio.support;

import java.util.HashMap;
import java.util.Map;

/**
 * Fake target for RPC calls. A server will have direct access to something like this
 * when it's setup.
 */
public final class FakeRpcTarget
{
    public static final Map<String, Object> methodNamesAndReturnValues = new HashMap<>();

	public void callMeVoid() { }
    public String callMeString() { return "callMeString"; }
    public String genString() { return "generated string"; }
    public String whatIsTheString() { return "it's this string..."; }
    public String hackTheGibson(int password) { return "password sent: " + password; }

    public FakeRpcTarget() {
		methodNamesAndReturnValues.put("callMeString", "callMeString");
		methodNamesAndReturnValues.put("genString", "generated string");
		methodNamesAndReturnValues.put("whatIsTheString", "it's this string...");
	}
}