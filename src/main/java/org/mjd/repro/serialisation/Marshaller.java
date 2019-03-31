package org.mjd.repro.serialisation;

public interface Marshaller {

	<T> byte[] marshall(T object, Class<T> type);

	<T> T unmarshall(byte[] bytesRead, Class<T> type);

}
