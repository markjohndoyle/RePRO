package org.mjd.sandbox.nio.message;

import java.io.IOException;

import org.junit.Test;

import static com.mscharhag.oleaster.matcher.Matchers.expect;

public class JavaSerialisedMessageTest {

	private static final String TEST_STRING = "LongWayToASmallAngryPlant";

	@Test
	public void testStringMessage() throws IOException {
		JavaSerialisedMessage<String> msgUnderTest = new JavaSerialisedMessage<>(TEST_STRING);

		expect(msgUnderTest.getValue()).toEqual(TEST_STRING);
	}

}
