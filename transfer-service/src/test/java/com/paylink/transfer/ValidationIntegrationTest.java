package com.paylink.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ValidationIntegrationTest extends IntegrationTest {

    static Stream<Arguments> invalidRequests() {
        String key = UUID.randomUUID().toString();
        return Stream.of(
                Arguments.of("zero amount", key, transferBody("W1001", "W2002", "0")),
                Arguments.of("negative amount", key, transferBody("W1001", "W2002", "-1")),
                Arguments.of("three decimal places", key, transferBody("W1001", "W2002", "1.234")),
                Arguments.of("above the maximum", key, transferBody("W1001", "W2002", "1000000.01")),
                Arguments.of("same wallet on both sides", key, transferBody("W1001", "W1001", "100.00")),
                Arguments.of("missing key", null, transferBody("W1001", "W2002", "100.00")),
                Arguments.of("non-UUID key", "not-a-uuid", transferBody("W1001", "W2002", "100.00")),
                Arguments.of("suspended wallet", key, transferBody("W4004", "W2002", "100.00")),
                Arguments.of("USD wallet", key, transferBody("W1001", "W5005", "100.00")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void invalidRequestGives400(String description, String key, String body) {
        int transfersBefore = allTransfers();

        HttpResponse<String> response = postTransfer(key, body);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(field(response, "code")).isEqualTo("INVALID_REQUEST");
        assertThat(allTransfers()).isEqualTo(transfersBefore);
    }

    private int allTransfers() {
        return jdbc.sql("SELECT COUNT(*) FROM transfers").query(Integer.class).single();
    }
}
