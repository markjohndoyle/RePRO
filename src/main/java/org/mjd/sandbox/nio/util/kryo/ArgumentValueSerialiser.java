package org.mjd.sandbox.nio.util.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.mjd.sandbox.nio.util.ArgumentValues;
import org.mjd.sandbox.nio.util.ArgumentValues.ArgumentValuePair;

public class ArgumentValueSerialiser extends Serializer<ArgumentValues> {

	@Override
	public void write(Kryo kryo, Output output, ArgumentValues object) {
		output.writeInt(object.size());
		object.iterator().forEachRemaining(argpair -> kryo.writeObject(output, argpair));
	}

	@Override
	public ArgumentValues read(Kryo kryo, Input input, Class<? extends ArgumentValues> type) {
		ArgumentValues vals = ArgumentValues.none();
		int size = input.readInt();
		if(size == 0) {
			return vals;
		}
		for(int i =0; i < size; i++) {
			ArgumentValuePair pair = kryo.readObject(input, ArgumentValuePair.class);
			vals.put(pair.getValue().getClass(), pair.getName(), pair.getValue());
		}
		return vals;
	}
}
