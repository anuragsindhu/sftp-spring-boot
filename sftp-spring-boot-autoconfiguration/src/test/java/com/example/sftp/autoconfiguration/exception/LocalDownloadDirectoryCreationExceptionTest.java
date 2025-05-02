package com.example.sftp.autoconfiguration.exception;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class LocalDownloadDirectoryCreationExceptionTest {

  @Test
  void testExceptionMessage() {
    String msg = "Test error message";
    LocalDownloadDirectoryCreationException ex = new LocalDownloadDirectoryCreationException(msg);
    Assertions.assertThat(ex.getMessage()).isEqualTo(msg);
  }
}
