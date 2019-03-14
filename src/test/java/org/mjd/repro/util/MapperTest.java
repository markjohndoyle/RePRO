package org.mjd.repro.util;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;
import static org.mjd.repro.util.Mapper.findInMap;

@RunWith(OleasterRunner.class)
public class MapperTest {

    // TEST INSTANCE BLOCK
    {
		final Map<Integer, String> testMap = new HashMap<>(ImmutableMap.of(1, "one", 2, "two", 3, "three"));

		describe("when using the Mapper to find a value", () -> {
			describe("using a present key", () -> {
				it("should be found", () -> {
					expect(findInMap(testMap, 1).found()).toBeTrue();
				});
				it("should return the expected value via get", () -> {
					expect(findInMap(testMap, 1).get()).toEqual("one");
				});
			});
			describe("using a missing key", () -> {
				it("should be missing, that is, not found", () -> {
					expect(findInMap(testMap, 5).found()).toBeFalse();
				});
				it("should throw if an get is called", () -> {
					expect(() -> findInMap(testMap, 5).get()).toThrow(IllegalStateException.class);
				});

				describe("but with an \"or\" instruction", () -> {
					it("should return the alternative provided by the \"or\" instruction", () -> {
						expect(findInMap(testMap, 5).or(() -> "five")).toEqual("five");
					});
				});
			});
		});
    }
}
