package com.example.sftp.autoconfiguration.bridge;

import com.example.sftp.autoconfiguration.SftpProperties;
import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SftpDynamicBridgeRegistrarTest {

  @Mock
  private SftpProperties sftpProperties;

  @Mock
  private DynamicSftpBridgeManager bridgeManager;

  @InjectMocks
  private SftpDynamicBridgeRegistrar registrar;

  @Test
  public void registerDynamicBridgesSuccess() {
    // Given a single server configuration with all dynamic bridges enabled.
    SftpServerConfig serverConfig = SftpServerConfig.builder()
        .name("myServer")
        .dynamicUploadBridgeEnabled(true)
        .uploadSource("kafkaInboundChannel")
        .dynamicDownloadBridgeEnabled(true)
        .downloadTarget("mqDownloadChannel")
        .dynamicArchiveBridgeEnabled(true)
        .archiveTarget("mqArchiveChannel")
        .build();

    when(sftpProperties.getServers()).thenReturn(Arrays.asList(serverConfig));

    // When
    registrar.registerDynamicBridges();

    // Then: Verify that the bridge manager is called with the expected channel names.
    verify(bridgeManager, times(1)).registerUploadBridge(serverConfig, "sftpUploadOutboundChannel-myServer");
    verify(bridgeManager, times(1)).registerDownloadBridge(serverConfig, "sftpDownloadInboundChannel-myServer");
    verify(bridgeManager, times(1)).registerArchiveBridge(serverConfig, "sftpArchiveChannel-myServer");
  }

  @Test
  public void registerDynamicBridgesNoServers() {
    // Given no server configuration.
    when(sftpProperties.getServers()).thenReturn(null);

    // When
    registrar.registerDynamicBridges();

    // Then: Verify that no dynamic bridge registration occurs.
    verify(bridgeManager, never()).registerUploadBridge(any(SftpServerConfig.class), anyString());
    verify(bridgeManager, never()).registerDownloadBridge(any(SftpServerConfig.class), anyString());
    verify(bridgeManager, never()).registerArchiveBridge(any(SftpServerConfig.class), anyString());
  }
}
