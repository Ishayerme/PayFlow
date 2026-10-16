package com.payflow.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionKeepsItsStatusAndMessage() {
        var response = handler.handleApi(ApiException.conflict("Insufficient funds"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error()).isEqualTo("Insufficient funds");
        assertThat(response.getBody().status()).isEqualTo(409);
    }

    @Test
    void factoryMethodsMapToExpectedStatuses() {
        assertThat(ApiException.notFound("x").getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ApiException.badRequest("x").getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ApiException.forbidden("x").getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void validationErrorsListEveryBadField() throws Exception {
        var binding = new BeanPropertyBindingResult(new Sample(), "request");
        binding.rejectValue("amount", "Positive", "must be greater than 0");
        binding.rejectValue("email", "Email", "must be a well-formed email address");
        var parameter = new MethodParameter(Sample.class.getDeclaredMethod("setAmount", int.class), 0);

        var response = handler.handleValidation(new MethodArgumentNotValidException(parameter, binding));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error())
                .isEqualTo("amount: must be greater than 0; email: must be a well-formed email address");
    }

    /** Minimal bean so the binding result has real fields to reject. */
    static class Sample {
        private int amount;
        private String email;

        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }
}
