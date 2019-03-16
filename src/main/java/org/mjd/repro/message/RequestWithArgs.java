package org.mjd.repro.message;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import org.mjd.repro.util.ArgumentValues;

@DefaultSerializer(JavaSerializer.class)
public class RequestWithArgs extends Request {
	private static final long serialVersionUID = 1L;

	private Object[] argValues = new Object[0];

	public RequestWithArgs(final long id) {
		this(id, ArgumentValues.none());
	}

	public RequestWithArgs(final long id, final Object[] argValues) {
		super(id);
		this.argValues = argValues;
	}

	@Deprecated
	public RequestWithArgs(final long id, final ArgumentValues argValues) {
		super(id);
		this.argValues = argValues.asObjArray();
	}

	/**
	 * @return the {@link ArgumentValues} of this request
	 */
	public final Object[] getArgValues() {
		return argValues;
	}
}
