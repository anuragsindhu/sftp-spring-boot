package com.example.sftp.autoconfiguration;

import org.apache.sshd.sftp.client.SftpClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.integration.file.remote.session.SessionFactory;

import java.util.Collections;

class SftpSessionFactoryProviderTest {

  @Test
  void testGetFactoryForValidServer() {
    SftpProperties.SftpServerConfig server = SftpProperties.SftpServerConfig.builder()
        .name("testServer")
        .host("localhost")
        .port(22)
        .username("user")
        .password("pass")
        .cacheSize(5)
        .build();
    SftpProperties properties = SftpProperties.builder()
        .servers(Collections.singletonList(server))
        .build();
    SftpSessionFactoryProvider provider = new SftpSessionFactoryProvider(properties);
    SessionFactory<SftpClient.DirEntry> factory = provider.getFactory("testServer");
    Assertions.assertThat(factory).isNotNull();
  }

  @Test
  void testGetFactoryThrowsForInvalidServer() {
    SftpProperties properties = SftpProperties.builder().servers(Collections.emptyList()).build();
    SftpSessionFactoryProvider provider = new SftpSessionFactoryProvider(properties);
    Assertions.assertThatThrownBy(() -> provider.getFactory("nonexistent"))
        .hasMessageContaining("No SFTP Factory found");
  }
}
