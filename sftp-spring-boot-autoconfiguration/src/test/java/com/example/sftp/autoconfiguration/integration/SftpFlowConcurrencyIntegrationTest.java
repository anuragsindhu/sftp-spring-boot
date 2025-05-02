package com.example.sftp.autoconfiguration.integration;

import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public class SftpFlowConcurrencyIntegrationTest extends BaseSftpIntegrationTest {
  private static final List<String> threadNames = new CopyOnWriteArrayList<>();

  @Test
  void shouldProcessFilesConcurrentlyUsingThreadPool() throws Exception {
    // Use server2 for upload operations.
    SftpServerConfig config = sftpProperties.getServers().stream()
        .filter(s -> "server2".equals(s.getName()))
        .findFirst().orElseThrow();

    var sessionFactory = sessionFactoryProvider.getFactory(config.getName());
    var executor = Executors.newFixedThreadPool(5, r -> {
      Thread t = new Thread(r);
      t.setName("Test-" + t.getId());
      return t;
    });
    List<Callable<Void>> tasks = new java.util.ArrayList<>();
    File baseTestFile = new File("src/test/resources/test-upload.txt");

    for (int i = 0; i < 5; i++) {
      int fileIndex = i;
      tasks.add(() -> {
        try (InputStream fis = new FileInputStream(baseTestFile)) {
          try (var session = sessionFactory.getSession()) {
            if (!session.exists("remote/upload2"))
              session.mkdir("remote/upload2");
            session.write(fis, "remote/upload2/test-upload-" + fileIndex + ".txt");
            threadNames.add(Thread.currentThread().getName());
          }
        }
        return null;
      });
    }
    executor.invokeAll(tasks);
    executor.shutdown();

    boolean found = threadNames.stream().anyMatch(name -> name.contains("Test-"));
    Assertions.assertThat(found).isTrue();
  }
}
