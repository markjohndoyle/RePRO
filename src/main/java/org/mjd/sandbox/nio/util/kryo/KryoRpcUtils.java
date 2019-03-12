package org.mjd.sandbox.nio.util.kryo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.primitives.Ints;

public final class KryoRpcUtils {
	private KryoRpcUtils() { }

	public synchronized static DataOutputStream
	writeKryoWithHeader(final Kryo kryo, final DataOutputStream clientOut, final Object request) throws IOException {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 Output kryoByteArrayOut = new Output(bos)) {
			kryo.writeObject(kryoByteArrayOut, request);
			kryoByteArrayOut.flush();
			bos.flush();
			final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			outputStream.write(Ints.toByteArray(bos.size()));
			outputStream.write(bos.toByteArray());
			clientOut.write(outputStream.toByteArray());
			return clientOut;
		}
	}

	public static byte[] objectToKryoBytes(final Kryo kryo, final Object obj)
	{
	    try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
	        Output kryoByteArrayOut = new Output(bos))
	    {
	        kryo.writeObject(kryoByteArrayOut, obj);
	        kryoByteArrayOut.flush();
	        return bos.toByteArray();
	    }
		catch (final IOException e) {
			throw new IllegalStateException("Error serialising " + obj + " to bytes with kryo", e);
		}
	}

}
