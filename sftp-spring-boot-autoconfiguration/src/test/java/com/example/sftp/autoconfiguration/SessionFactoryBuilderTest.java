package com.example.sftp.autoconfiguration;

import org.apache.sshd.sftp.client.SftpClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.integration.file.remote.session.SessionFactory;

class SessionFactoryBuilderTest {

  @Test
  void testBuildUsesPassword() {
    SessionFactory<SftpClient.DirEntry> factory = SessionFactoryBuilder.builder()
        .host("localhost")
        .port(22)
        .username("user")
        .applyAuthentication("password", null, null)
        .cacheSize(5)
        .build();
    Assertions.assertThat(factory).isNotNull();
  }

  @Test
  void testBuildUsesPrivateKey() {
    SessionFactory<SftpClient.DirEntry> factory = SessionFactoryBuilder.builder()
        .host("localhost")
        .port(22)
        .username("user")
        .applyAuthentication(null, "dummyKey", "dummyPass")
        .cacheSize(5)
        .build();
    Assertions.assertThat(factory).isNotNull();
  }

  @Test
  void testBuildFailsWithoutAuthentication() {
    Assertions.assertThatThrownBy(() ->
        SessionFactoryBuilder.builder()
            .host("localhost")
            .port(22)
            .username("user")
            .build()
    ).hasMessageContaining("No authentication details provided");
  }
}
