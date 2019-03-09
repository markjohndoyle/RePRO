package org.mjd.sandbox.nio.support;

public interface Broadcaster {
	public interface Listener {
		void notify(String notification);
	}

	void register(Listener listener);
}
