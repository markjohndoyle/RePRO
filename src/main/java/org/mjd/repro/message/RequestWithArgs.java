package org.mjd.repro.message;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;

@DefaultSerializer(JavaSerializer.class)
public class RequestWithArgs extends Request {
	private static final long serialVersionUID = 1L;

	private final Object[] argValues;

	public RequestWithArgs(final long id) {
		super(id);
		this.argValues = new Object[0];
	}

	public RequestWithArgs(final long id, final Object[] argValues) {
		super(id);
		this.argValues = argValues;
	}

	/**
	 * @return the argument values of this request
	 */
	public final Object[] getArgValues() {
		return argValues;
	}
}
