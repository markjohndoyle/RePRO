package org.mjd.repro.message;

import com.esotericsoftware.kryo.DefaultSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import org.mjd.repro.util.ArgumentValues;

@DefaultSerializer(JavaSerializer.class)
public class RequestWithArgs extends Request {
	private static final long serialVersionUID = 1L;

	private long id;
	private ArgumentValues argValues = new ArgumentValues();

	public RequestWithArgs(final long id) {
		this(id, ArgumentValues.none());
	}

	public RequestWithArgs(final long id, final ArgumentValues argValues) {
		super(id);
		this.argValues = argValues;
	}

	/**
	 * @return the {@link ArgumentValues} of this request
	 */
	public final ArgumentValues getArgValues() {
		return argValues;
	}

	public final void setArgValues(final ArgumentValues argVals) {
		this.argValues = argVals;
	}
}
