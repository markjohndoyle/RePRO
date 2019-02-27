package org.mjd.sandbox.nio.message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class JavaSerialisedMessage<T> implements Message<T> {

	private T value;
	private byte[] array;

	public JavaSerialisedMessage(T value) throws IOException {
		try(ByteArrayOutputStream out = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(out))
		{
			this.value = value;
			oos.writeObject(value);
			oos.flush();
			array = out.toByteArray();
		}
	}


	@Override
	public T getValue() {
		return value;
	}

	@Override
	public int size() {
		return array.length;
	}

	@Override
	public byte[] asByteArray() {
		return array;
	}

	public static <T> Message<T> from(byte[] bytes, Class<T> type) throws IOException, ClassNotFoundException
	{
		try(ByteArrayInputStream in = new ByteArrayInputStream(bytes);
			ObjectInputStream ooi = new ObjectInputStream(in))
		{
			return new JavaSerialisedMessage<>(type.cast(ooi.readObject()));
		}

	}
}
