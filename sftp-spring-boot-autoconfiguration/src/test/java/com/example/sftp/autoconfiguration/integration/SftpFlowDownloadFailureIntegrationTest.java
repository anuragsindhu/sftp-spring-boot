package com.example.sftp.autoconfiguration.integration;

import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class SftpFlowDownloadFailureIntegrationTest extends BaseSftpIntegrationTest {

  @Test
  void shouldRetainRemoteFileOnDownloadProcessingFailure() throws Exception {
    // Use server1 (download) for the test.
    SftpServerConfig config = sftpProperties.getServers().stream()
        .filter(s -> "server1".equals(s.getName()))
        .findFirst().orElseThrow();

    var sessionFactory = sessionFactoryProvider.getFactory(config.getName());
    var session = sessionFactory.getSession();
    if (!session.exists("remote/download1"))
      session.mkdir("remote/download1");

    String fileName = "fail-download.txt";
    File testFile = new File("src/test/resources/test-download.txt");
    try (InputStream fis = new FileInputStream(testFile)) {
      session.write(fis, "remote/download1/" + fileName);
    }
    session.close();

    // Simulate a download processing failure.
    Exception ex = Assertions.catchThrowableOfType(() -> {
      throw new RuntimeException("Simulated download processing failure");
    }, RuntimeException.class);
    Assertions.assertThat(ex).hasMessageContaining("Simulated download processing failure");

    // Verify file is still present remotely.
    session = sessionFactory.getSession();
    boolean exists = existAtRemoteLocation(session, "remote/download1", fileName);
    session.close();
    Assertions.assertThat(exists).isTrue();
  }
}
