package com.example.sftp.autoconfiguration.integration;

import com.example.sftp.autoconfiguration.SftpProperties;
import com.example.sftp.autoconfiguration.SftpSessionFactoryProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = BaseSftpIntegrationTest.TestConfig.class)
@ActiveProfiles("integration-test")
@DirtiesContext
public abstract class BaseSftpIntegrationTest {
  @TempDir
  protected static Path tempDir;

  protected static int sftpPort;

  // Embedded SFTP server instance.
  private static SshServer sshd;

  @Autowired
  protected SftpSessionFactoryProvider sessionFactoryProvider;

  @Autowired
  protected SftpProperties sftpProperties;

  @DynamicPropertySource
  static void dynamicProperties(DynamicPropertyRegistry registry) {
    // Server1: Download and Archive only.
    registry.add("sftp.servers[0].name", () -> "server1");
    registry.add("sftp.servers[0].host", () -> "localhost");
    registry.add("sftp.servers[0].username", () -> "user");
    registry.add("sftp.servers[0].password", () -> "password");
    registry.add("sftp.servers[0].port", () -> String.valueOf(sftpPort));
    registry.add("sftp.servers[0].from", () -> "remote/download1");
    registry.add("sftp.servers[0].archive", () -> "remote/archive1");

    // Server2: Upload only.
    registry.add("sftp.servers[1].name", () -> "server2");
    registry.add("sftp.servers[1].host", () -> "localhost");
    registry.add("sftp.servers[1].username", () -> "user");
    registry.add("sftp.servers[1].password", () -> "password");
    registry.add("sftp.servers[1].port", () -> String.valueOf(sftpPort));
    registry.add("sftp.servers[1].to", () -> "remote/upload2");

    // Server3: Download, Upload, and Archive.
    registry.add("sftp.servers[2].name", () -> "server3");
    registry.add("sftp.servers[2].host", () -> "localhost");
    registry.add("sftp.servers[2].username", () -> "user");
    registry.add("sftp.servers[2].password", () -> "password");
    registry.add("sftp.servers[2].port", () -> String.valueOf(sftpPort));
    registry.add("sftp.servers[2].from", () -> "remote/download3");
    registry.add("sftp.servers[2].to", () -> "remote/upload3");
    registry.add("sftp.servers[2].archive", () -> "remote/archive3");

    // Use temporary directory as the global local download directory.
    registry.add("sftp.localDownloadDir", () -> tempDir.toAbsolutePath().toString());
  }

  @BeforeAll
  public static void setUpSftp() throws Exception {
    sshd = SshServer.setUpDefaultServer();
    sshd.setPort(0); // system assigns an available port.
    sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get("target/hostkey.ser")));
    sshd.setSubsystemFactories(java.util.Collections.singletonList(new SftpSubsystemFactory()));
    sshd.setPasswordAuthenticator((username, password, session) ->
        Objects.equals(username, "user") && Objects.equals(password, "password"));
    sshd.start();
    sftpPort = sshd.getPort();
  }

  @AfterAll
  public static void tearDownSftp() throws Exception {
    if (sshd != null) {
      sshd.stop();
    }
  }

  protected <F> List<SftpClient.DirEntry> listDirEntry(Session<F> session, String location) throws IOException {
    List<SftpClient.DirEntry> result = new ArrayList<>();
    for (Object entryObj : session.list(location)) {
      if (entryObj instanceof SftpClient.DirEntry dirEntry) {
        result.add(dirEntry);
      }
    }

    return result;
  }

  protected <F> boolean existAtRemoteLocation(Session<F> session, String location, String fileName) throws IOException {
    for (Object entryObj : session.list(location)) {
      if (entryObj instanceof SftpClient.DirEntry dirEntry &&
          dirEntry.getFilename().equals(fileName)) {
        return true;
      }
    }

    return false;
  }

  // Test configuration.
  @SpringBootApplication(scanBasePackages = "com.example.sftp.autoconfiguration")
  @EnableConfigurationProperties(SftpProperties.class)
  @Slf4j
  static class TestConfig {

    @Bean
    public SftpSessionFactoryProvider sftpSessionFactoryProvider(SftpProperties sftpProperties) {
      SftpSessionFactoryProvider provider = new SftpSessionFactoryProvider(sftpProperties);
      if (!ObjectUtils.isEmpty(sftpProperties.getServers())) {
        sftpProperties.getServers().forEach(server -> {
          var factory = provider.getFactory(server.getName());
          if (factory instanceof DefaultSftpSessionFactory defaultSftpSessionFactory) {
            defaultSftpSessionFactory.setAllowUnknownKeys(true);
            log.info("Set allowUnknownKeys to true for factory of server: {}", server.getName());
          } else if (factory instanceof CachingSessionFactory cachingSessionFactory) {
            try {
              cachingSessionFactory.setPoolSize(server.getCacheSize());
              Field targetField = cachingSessionFactory.getClass().getDeclaredField("sessionFactory");
              targetField.setAccessible(true);
              Object target = targetField.get(cachingSessionFactory);
              if (target instanceof DefaultSftpSessionFactory defaultSftpSessionFactory) {
                defaultSftpSessionFactory.setAllowUnknownKeys(true);
                log.info("Unwrapped caching factory and set allowUnknownKeys to true for server: {}", server.getName());
              }
            } catch (NoSuchFieldException | IllegalAccessException e) {
              log.warn("Unable to retrieve underlying DefaultSftpSessionFactory for server {}: {}", server.getName(), e.getMessage());
            }
          }
        });
      }
      return provider;
    }
  }
}
