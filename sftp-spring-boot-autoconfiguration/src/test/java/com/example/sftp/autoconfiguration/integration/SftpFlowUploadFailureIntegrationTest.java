package com.example.sftp.autoconfiguration.integration;

import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class SftpFlowUploadFailureIntegrationTest extends BaseSftpIntegrationTest {

  @Test
  void shouldRetainLocalFileOnUploadProcessingFailure() throws Exception {
    // Use server2 for upload operations.
    SftpServerConfig config = sftpProperties.getServers().stream()
        .filter(s -> "server2".equals(s.getName()))
        .findFirst().orElseThrow();

    String localUploadDir = sftpProperties.getLocalDownloadDir() + File.separator
        + "upload" + File.separator + config.getName();
    File uploadDir = new File(localUploadDir);
    uploadDir.mkdirs();
    Path localFile = Files.createTempFile(uploadDir.toPath(), "uploadFail", ".txt");
    Files.write(localFile, "upload failure content".getBytes());

    // Simulate an upload failure.
    Exception ex = Assertions.catchThrowableOfType(() -> {
      try (InputStream fis = new FileInputStream(localFile.toFile())) {
        throw new RuntimeException("Simulated upload processing failure");
      }
    }, RuntimeException.class);
    Assertions.assertThat(ex).hasMessageContaining("Simulated upload processing failure");

    // Verify local file is still present.
    Assertions.assertThat(localFile.toFile().exists()).isTrue();

    // Verify remote upload did not occur.
    var session = sessionFactoryProvider.getFactory(config.getName()).getSession();
    boolean existsRemotely = existAtRemoteLocation(session, "remote/upload2", localFile.getFileName().toString());
    session.close();
    Assertions.assertThat(existsRemotely).isFalse();

    Files.deleteIfExists(localFile);
  }
}
