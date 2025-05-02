package com.example.sftp.autoconfiguration.integration;

import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class SftpFlowArchiveFailureIntegrationTest extends BaseSftpIntegrationTest {

  @Test
  void shouldRetainFileInUploadFolderOnArchiveProcessingFailure() throws Exception {
    // Use server1 for archive processing.
    SftpServerConfig config = sftpProperties.getServers().stream()
        .filter(s -> "server1".equals(s.getName()))
        .findFirst().orElseThrow();

    var sessionFactory = sessionFactoryProvider.getFactory(config.getName());
    var session = sessionFactory.getSession();
    if (!session.exists("remote"))
      session.mkdir("remote");
    if (!session.exists("remote/upload1"))
      session.mkdir("remote/upload1");
    if (!session.exists("remote/archive1"))
      session.mkdir("remote/archive1");

    String fileName = "fail-archive.txt";
    File testFile = new File("src/test/resources/test-archive.txt");
    try (InputStream fis = new FileInputStream(testFile)) {
      session.write(fis, "remote/upload1/" + fileName);
    }
    session.close();

    // Simulate archive processing failure.
    Exception ex = Assertions.catchThrowableOfType(() -> {
      throw new RuntimeException("Simulated archive processing failure");
    }, RuntimeException.class);
    Assertions.assertThat(ex).hasMessageContaining("Simulated archive processing failure");

    // Verify file remains in upload folder and was not archived.
    session = sessionFactoryProvider.getFactory(config.getName()).getSession();
    boolean existsInUpload = existAtRemoteLocation(session, "remote/upload1", fileName);
    boolean existsInArchive = existAtRemoteLocation(session, "remote/archive1", fileName);
    session.close();
    Assertions.assertThat(existsInUpload).isTrue();
    Assertions.assertThat(existsInArchive).isFalse();
  }
}
