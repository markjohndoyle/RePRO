package org.mjd.sandbox.nio.support;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.primitives.Ints;

public final class KryoRpcUtils {
	private KryoRpcUtils() { }

	public synchronized static DataOutputStream
	writeKryoWithHeader(Kryo kryo, DataOutputStream clientOut, Object request) throws IOException {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); Output kryoByteArrayOut = new Output(bos)) {
			kryo.writeObject(kryoByteArrayOut, request);
			kryoByteArrayOut.flush();
			bos.flush();
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			outputStream.write(Ints.toByteArray(bos.size()));
			outputStream.write(bos.toByteArray());
			clientOut.write(outputStream.toByteArray());
			return clientOut;
		}
	}

}
