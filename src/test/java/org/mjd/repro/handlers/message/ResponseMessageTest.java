package org.mjd.repro.handlers.message;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;

@RunWith(OleasterRunner.class)
public class ResponseMessageTest {

	// TEST INSTANCE BLOCK
	{
		final ResponseMessage<Integer> valueMsg = new ResponseMessage<>(0L, 2);
		final ResponseMessage<Object> errorMsg = ResponseMessage.error(1L, new IllegalStateException());
		final ResponseMessage<Void> voidMsg = ResponseMessage.voidMsg(2L);

		describe("a value message", () -> {
			it("should return the correct ID", () -> {
				expect(valueMsg.getId()).toEqual(0L);
			});
			it("should return the correct value", () -> {
				expect(valueMsg.getValue().isPresent()).toBeTrue();
				expect(valueMsg.getValue().get()).toEqual(2);
			});
			it("should not have an error", () -> {
				expect(valueMsg.isError()).toBeFalse();
			});
		});
		describe("an error message", () -> {
			it("should return the correct ID", () -> {
				expect(errorMsg.getId()).toEqual(1L);
			});
			it("should not have a value", () -> {
				expect(errorMsg.getValue().isPresent()).toBeFalse();
			});
			it("should have an error", () -> {
				expect(errorMsg.isError()).toBeTrue();
				expect(errorMsg.getError()).toBeNotNull();
				expect(errorMsg.getError().get()).toBeInstanceOf(IllegalStateException.class);
			});
		});
		describe("a void message", () -> {
			it("should return the correct ID", () -> {
				expect(voidMsg.getId()).toEqual(2L);
			});
			it("should not have a value", () -> {
				expect(voidMsg.getValue().isPresent()).toBeFalse();
			});
			it("should not have an error", () -> {
				expect(voidMsg.isError()).toBeFalse();
			});
		});
	}
}
