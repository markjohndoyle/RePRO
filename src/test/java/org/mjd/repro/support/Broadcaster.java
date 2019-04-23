package org.mjd.repro.support;

public interface Broadcaster {
	public interface Listener<T> {
		void notify(T notification);
	}

	void register(Listener listener);
}
