package tech.liganex.studio.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test
    void unexpectedErrorResponseStaysGeneric() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleUnexpected(new IllegalStateException(
                "provider failed with api-key=secret-token"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.INTERNAL_ERROR.message());
        assertThat(response.getBody().message()).doesNotContain("secret-token");
    }
}
