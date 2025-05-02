package com.example.sftp.autoconfiguration.integration;

import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class SftpFlowMetadataRollbackIntegrationTest extends BaseSftpIntegrationTest {

  @Test
  void shouldAllowReprocessingWhenMetadataStoreRollsBack() throws Exception {
    // Use server3 and enable metadata store.
    SftpServerConfig config = sftpProperties.getServers().stream()
        .filter(s -> "server3".equals(s.getName()))
        .findFirst().orElseThrow();
    config.setEnableMetadataStore(true);

    var sessionFactory = sessionFactoryProvider.getFactory(config.getName());
    var session = sessionFactory.getSession();
    if (!session.exists("remote/download3"))
      session.mkdir("remote/download3");

    File testFile = new File("src/test/resources/test-download.txt");
    // Write file twice.
    try (InputStream fis = new FileInputStream(testFile)) {
      session.write(fis, "remote/download3/reprocess.txt");
    }
    try (InputStream fis = new FileInputStream(testFile)) {
      session.write(fis, "remote/download3/reprocess.txt");
    }
    session.close();

    session = sessionFactory.getSession();
    long count = listDirEntry(session, "remote/download3")
        .stream()
        .filter(dirEntry -> "reprocess.txt".equals(dirEntry.getFilename()))
        .count();
    session.close();

    Assertions.assertThat(count).isEqualTo(1);
  }
}
