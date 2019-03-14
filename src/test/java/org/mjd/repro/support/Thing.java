package org.mjd.repro.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Thing implements Broadcaster {
	private final List<Listener> subs = Collections.synchronizedList(new ArrayList<>());
	private final ExecutorService generator = Executors.newSingleThreadExecutor();
	private final Random random = new Random();

	public Thing() {
		generator.execute(this::generateNotifications);
	}

	@Override
	public void register(Listener listener) {
		subs.add(listener);
	}

	private void generateNotifications() {
		while (true) {
			for (Listener sub : subs) {
				sub.notify("Things just seem so much better in theory than in practice..." + random.nextLong());
			}
			notificationPause();
		}
	}

	private static void notificationPause() {
		try {
			Thread.sleep(250);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public void deregister(Listener listener) {
		subs.remove(listener);
	}

}
