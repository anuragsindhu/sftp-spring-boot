package com.example.sftp.autoconfiguration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

class SftpPropertiesTest {

  @Test
  void testPropertiesBuilder() {
    SftpProperties.SftpServerConfig server = SftpProperties.SftpServerConfig.builder()
        .name("server1")
        .host("localhost")
        .port(22)
        .username("user")
        .password("pass")
        .cacheSize(5)
        .build();
    SftpProperties properties = SftpProperties.builder()
        .localDownloadDir("downloadDir")
        .servers(Collections.singletonList(server))
        .build();
    Assertions.assertThat(properties.getLocalDownloadDir()).isEqualTo("downloadDir");
    Assertions.assertThat(properties.getServers()).isNotNull();
    Assertions.assertThat(properties.getServers().get(0).getName()).isEqualTo("server1");
  }
}
