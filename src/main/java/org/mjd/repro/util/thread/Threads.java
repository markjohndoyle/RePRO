package org.mjd.repro.util.thread;

import java.util.concurrent.ThreadFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Simple Thread utilities and syntactic sugar.
 */
public final class Threads {
	private Threads() {
		// Util class
	}

	public static ThreadFactory called(final String name) {
		return new ThreadFactoryBuilder().setNameFormat(name).build();
	}
}
