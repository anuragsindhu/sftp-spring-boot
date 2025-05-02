package com.example.sftp.autoconfiguration.integration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class SftpStandardOperationsIntegrationTest extends BaseSftpIntegrationTest {

  @Test
  void testServer1DownloadFilePresence() throws Exception {
    var sessionFactory = sessionFactoryProvider.getFactory("server1");
    var session = sessionFactory.getSession();
    if (!session.exists("remote"))
      session.mkdir("remote");
    if (!session.exists("remote/download1"))
      session.mkdir("remote/download1");

    File testFile = new File("src/test/resources/test-download.txt");
    try (InputStream fis = new FileInputStream(testFile)) {
      session.write(fis, "remote/download1/test-download.txt");
    }
    session.close();

    session = sessionFactory.getSession();
    boolean exists = existAtRemoteLocation(session, "remote/download1", "test-download.txt");
    session.close();
    Assertions.assertThat(exists).isTrue();
  }

  @Test
  void testServer1ArchiveFile() throws Exception {
    var sessionFactory = sessionFactoryProvider.getFactory("server1");
    var session = sessionFactory.getSession();
    if (!session.exists("remote"))
      session.mkdir("remote");
    if (!session.exists("remote/upload1"))
      session.mkdir("remote/upload1");
    if (!session.exists("remote/archive1"))
      session.mkdir("remote/archive1");

    File testFile = new File("src/test/resources/test-archive.txt");
    try (InputStream fis = new FileInputStream(testFile)) {
      session.write(fis, "remote/upload1/test-archive.txt");
    }
    session.rename("remote/upload1/test-archive.txt", "remote/archive1/test-archive.txt");
    session.close();

    session = sessionFactory.getSession();
    boolean exists = existAtRemoteLocation(session, "remote/archive1", "test-archive.txt");
    session.close();
    Assertions.assertThat(exists).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"test-upload.txt", "alternate-upload.txt"})
  void testServer2UploadFile(String fileName) throws Exception {
    // In this parameterized test we assume both files exist in src/test/resources.
    var sessionFactory = sessionFactoryProvider.getFactory("server2");
    var session = sessionFactory.getSession();
    if (!session.exists("remote"))
      session.mkdir("remote");
    if (!session.exists("remote/upload2"))
      session.mkdir("remote/upload2");

    File localFile = new File("src/test/resources/" + fileName);
    try (InputStream fis = new FileInputStream(localFile)) {
      session.write(fis, "remote/upload2/" + fileName);
    }
    session.close();

    session = sessionFactory.getSession();
    boolean exists = existAtRemoteLocation(session, "remote/upload2", fileName);
    session.close();
    Assertions.assertThat(exists).isTrue();
  }
}
