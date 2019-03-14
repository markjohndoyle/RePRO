package org.mjd.repro.util.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.mjd.repro.util.ArgumentValues;
import org.mjd.repro.util.ArgumentValues.ArgumentValuePair;

public final class ArgumentValueSerialiser extends Serializer<ArgumentValues> {

	@Override
	public void write(final Kryo kryo, final Output output, final ArgumentValues object) {
		output.writeInt(object.size());
		object.iterator().forEachRemaining(argpair -> kryo.writeObject(output, argpair));
	}

	@Override
	public ArgumentValues read(final Kryo kryo, final Input input, final Class<? extends ArgumentValues> type) {
		final ArgumentValues vals = ArgumentValues.none();
		final int size = input.readInt();
		if(size == 0) {
			return vals;
		}
		for(int i =0; i < size; i++) {
			final ArgumentValuePair pair = kryo.readObject(input, ArgumentValuePair.class);
			vals.put(pair.getValue().getClass(), pair.getName(), pair.getValue());
		}
		return vals;
	}
}
