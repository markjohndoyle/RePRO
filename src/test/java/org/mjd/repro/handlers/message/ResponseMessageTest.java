package org.mjd.repro.handlers.message;

import com.mscharhag.oleaster.runner.OleasterRunner;
import org.junit.runner.RunWith;

import static com.mscharhag.oleaster.matcher.Matchers.expect;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.describe;
import static com.mscharhag.oleaster.runner.StaticRunnerSupport.it;

@RunWith(OleasterRunner.class)
public class ResponseMessageTest {

	private static final String VALUE_MSG_ID = "client-0";
	private static final String ERROR_MSG_ID = "client-1";
	private static final String VOID_MSG_ID = "client-2";

	// TEST INSTANCE BLOCK
	{
		final ResponseMessage<Integer> valueMsg = new ResponseMessage<>(VALUE_MSG_ID, 2);
		final ResponseMessage<Object> errorMsg = ResponseMessage.error(ERROR_MSG_ID, new IllegalStateException());
		final ResponseMessage<Void> voidMsg = ResponseMessage.voidMsg(VOID_MSG_ID);

		describe("a value message", () -> {
			it("should return the correct ID", () -> {
				expect(valueMsg.getId()).toEqual(VALUE_MSG_ID);
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
				expect(errorMsg.getId()).toEqual(ERROR_MSG_ID);
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
				expect(voidMsg.getId()).toEqual(VOID_MSG_ID);
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
