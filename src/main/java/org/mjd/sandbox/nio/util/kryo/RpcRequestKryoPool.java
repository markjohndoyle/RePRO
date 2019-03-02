package org.mjd.sandbox.nio.util.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import org.mjd.sandbox.nio.message.RpcRequest;
import org.mjd.sandbox.nio.util.ArgumentValues;
import org.mjd.sandbox.nio.util.ArgumentValues.ArgumentValuePair;

public final class RpcRequestKryoPool extends Pool<Kryo> {

	public RpcRequestKryoPool(boolean threadSafe, boolean softReferences, int maximumCapacity) {
		super(threadSafe, softReferences, maximumCapacity);
	}

	@Override
    protected Kryo create () {
        Kryo kryo = new Kryo();
        kryo.register(RpcRequest.class);
        kryo.register(ArgumentValues.class);
        kryo.register(ArgumentValuePair.class);
        return kryo;
    }
}