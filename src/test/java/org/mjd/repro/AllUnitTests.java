package org.mjd.repro;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.mjd.repro.readers.RequestReaderTest;
import org.mjd.repro.readers.body.SingleMessageBodyReaderTest;
import org.mjd.repro.readers.header.IntHeaderReaderTest;

/**
 * Runs all unit tests. Integration tests are ignored.
 *
 */
@RunWith(Suite.class)
@SuiteClasses({IntHeaderReaderTest.class,
			   RequestReaderTest.class,
			   SingleMessageBodyReaderTest.class})
public final class AllUnitTests {
	// nothing to do, all annotations.
}
