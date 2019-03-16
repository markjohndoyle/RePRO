package org.mjd.repro.message;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;

/**
 */
@DefaultSerializer(JavaSerializer.class)
public class RpcRequest extends RequestWithArgs {
	private static final long serialVersionUID = 3320910799863854768L;

	private String method;

	public RpcRequest(final long id, final String method) {
		this(id, method, new Object[0]);
	}

	public RpcRequest(final long id, final String method, final Object[] argVals) {
		super(id, argVals);
		this.method = method;
	}

	public final String getMethod() {
		return method;
	}

	@Override
	public final String toString() {
		return "RpcRequest [id=" + getId() + ", method=" + method + ", argValues=" + getArgValues() + "]";
	}
}
