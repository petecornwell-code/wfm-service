package com.wfm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wfm.exception.BambooHRRateLimitedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Verifies that HttpBambooHRClient translates 503/429 responses into
 * BambooHRRateLimitedException with the correct retryAfterSeconds value.
 */
class HttpBambooHRClient503Test {

    private MockRestServiceServer mockServer;
    private HttpBambooHRClient client;

    @BeforeEach
    void setUp() {
        // Build a RestClient.Builder we can bind MockRestServiceServer to
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        // Create client using the test constructor
        client = new HttpBambooHRClient(builder, new ObjectMapper());
    }

    @Test
    void listEmployees_503WithRetryAfterHeader_throwsWithParsedSeconds() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/reports/custom")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(
                        withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                                .header(HttpHeaders.RETRY_AFTER, "42")
                );

        assertThatThrownBy(() -> client.listEmployees("1", "TestProject"))
                .isInstanceOf(BambooHRRateLimitedException.class)
                .satisfies(ex -> {
                    BambooHRRateLimitedException rle = (BambooHRRateLimitedException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isEqualTo(42);
                    assertThat(rle.getMessage()).contains("42");
                });
    }

    @Test
    void listEmployees_503WithNoRetryAfterHeader_defaultsTo60Seconds() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/reports/custom")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(
                        withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                );

        assertThatThrownBy(() -> client.listEmployees("1", "TestProject"))
                .isInstanceOf(BambooHRRateLimitedException.class)
                .satisfies(ex -> {
                    BambooHRRateLimitedException rle = (BambooHRRateLimitedException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isEqualTo(60);
                });
    }
}
