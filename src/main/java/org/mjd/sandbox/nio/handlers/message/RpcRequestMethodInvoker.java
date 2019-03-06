package org.mjd.sandbox.nio.handlers.message;

import org.mjd.sandbox.nio.message.RpcRequest;

public interface RpcRequestMethodInvoker {
	Object invoke(RpcRequest request);
}
