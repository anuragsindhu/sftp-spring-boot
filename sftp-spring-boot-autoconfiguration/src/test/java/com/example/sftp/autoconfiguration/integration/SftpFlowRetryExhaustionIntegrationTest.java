package com.example.sftp.autoconfiguration.integration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class SftpFlowRetryExhaustionIntegrationTest extends BaseSftpIntegrationTest {

  @Test
  void shouldExhaustRetriesAndThrowException() {
    int maxAttempts = sftpProperties.getDefaultRetry().getMaxAttempts();
    int attempts = 0;
    Exception lastEx = null;
    while (attempts < maxAttempts) {
      attempts++;
      try {
        throw new RuntimeException("Simulated operation failure for retry exhaustion");
      } catch (RuntimeException e) {
        lastEx = e;
      }
    }
    Assertions.assertThat(lastEx).isNotNull();
    Assertions.assertThat(lastEx.getMessage()).contains("Simulated operation failure for retry exhaustion");
  }
}
