package com.example.sftp.autoconfiguration.bridge;

import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DynamicSftpBridgeManagerTest {

  @Mock
  private IntegrationFlowContext integrationFlowContext;

  @Mock
  private IntegrationFlowContext.IntegrationFlowRegistrationBuilder registrationBuilder;

  @InjectMocks
  private DynamicSftpBridgeManager bridgeManager;

  @Test
  public void registerUploadBridgeSuccess() {
    when(integrationFlowContext.registration(any(IntegrationFlow.class))).thenReturn(registrationBuilder);
    when(registrationBuilder.id(anyString())).thenReturn(registrationBuilder);

    // Given a valid Sftp server configuration with dynamic upload bridging enabled.
    SftpServerConfig serverConfig = SftpServerConfig.builder()
        .name("myServer")
        .dynamicUploadBridgeEnabled(true)
        .uploadSource("kafkaInboundChannel")
        .build();
    String sftpUploadChannelName = "sftpUploadOutboundChannel-myServer";

    // When
    String flowId = bridgeManager.registerUploadBridge(serverConfig, sftpUploadChannelName);

    // Then: Expect the computed flow id to be returned and registration calls to be invoked.
    assertThat(flowId).isEqualTo("dynamicUploadBridge-myServer");
    verify(integrationFlowContext, times(1)).registration(any(IntegrationFlow.class));
    verify(registrationBuilder, times(1)).id("dynamicUploadBridge-myServer");
    verify(registrationBuilder, times(1)).register();
  }

  @Test
  public void registerUploadBridgeNotEnabled() {
    // Given a configuration where dynamic upload bridging is disabled.
    SftpServerConfig serverConfig = SftpServerConfig.builder()
        .name("myServer")
        .dynamicUploadBridgeEnabled(false)
        .uploadSource("kafkaInboundChannel")
        .build();
    String sftpUploadChannelName = "sftpUploadOutboundChannel-myServer";

    // When
    String flowId = bridgeManager.registerUploadBridge(serverConfig, sftpUploadChannelName);

    // Then: Expect a null value with no registration attempted.
    assertThat(flowId).isNull();
    verify(integrationFlowContext, never()).registration(any(IntegrationFlow.class));
  }

  @Test
  public void registerUploadBridgeMissingUploadSource() {
    // Given a configuration where dynamic upload bridging is enabled, but with uploadSource missing.
    SftpServerConfig serverConfig = SftpServerConfig.builder()
        .name("myServer")
        .dynamicUploadBridgeEnabled(true)
        .uploadSource("")
        .build();
    String sftpUploadChannelName = "sftpUploadOutboundChannel-myServer";

    // When
    String flowId = bridgeManager.registerUploadBridge(serverConfig, sftpUploadChannelName);

    // Then: Expect a null value and no registration attempt.
    assertThat(flowId).isNull();
    verify(integrationFlowContext, never()).registration(any(IntegrationFlow.class));
  }

  @Test
  public void registerDownloadBridgeSuccess() {
    when(integrationFlowContext.registration(any(IntegrationFlow.class))).thenReturn(registrationBuilder);
    when(registrationBuilder.id(anyString())).thenReturn(registrationBuilder);

    // Given a valid Sftp server configuration with dynamic download bridging enabled.
    SftpServerConfig serverConfig = SftpServerConfig.builder()
        .name("myServer")
        .dynamicDownloadBridgeEnabled(true)
        .downloadTarget("mqDownloadChannel")
        .build();
    String sftpDownloadChannelName = "sftpDownloadInboundChannel-myServer";

    // When
    String flowId = bridgeManager.registerDownloadBridge(serverConfig, sftpDownloadChannelName);

    // Then: Expect the computed flow id to be returned and registration calls invoked.
    assertThat(flowId).isEqualTo("dynamicDownloadBridge-myServer");
    verify(integrationFlowContext, times(1)).registration(any(IntegrationFlow.class));
    verify(registrationBuilder, times(1)).id("dynamicDownloadBridge-myServer");
    verify(registrationBuilder, times(1)).register();
  }

  @Test
  public void registerDownloadBridgeNotEnabled() {
    // Given a configuration where dynamic download bridging is disabled.
    SftpServerConfig serverConfig = SftpServerConfig.builder()
        .name("myServer")
        .dynamicDownloadBridgeEnabled(false)
        .downloadTarget("mqDownloadChannel")
        .build();
    String sftpDownloadChannelName = "sftpDownloadInboundChannel-myServer";

    // When
    String flowId = bridgeManager.registerDownloadBridge(serverConfig, sftpDownloadChannelName);

    // Then: Expect a null value with no registration attempted.
    assertThat(flowId).isNull();
    verify(integrationFlowContext, never()).registration(any(IntegrationFlow.class));
  }

  @Test
  public void registerDownloadBridgeMissingDownloadTarget() {
    // Given a configuration where dynamic download bridging is enabled, but downloadTarget is empty.
    SftpServerConfig serverConfig = SftpServerConfig.builder()
        .name("myServer")
        .dynamicDownloadBridgeEnabled(true)
        .downloadTarget("")
        .build();
    String sftpDownloadChannelName = "sftpDownloadInboundChannel-myServer";

    // When
    String flowId = bridgeManager.registerDownloadBridge(serverConfig, sftpDownloadChannelName);

    // Then: Expect a null value and no registration attempt.
    assertThat(flowId).isNull();
    verify(integrationFlowContext, never()).registration(any(IntegrationFlow.class));
  }

  @Test
  public void registerArchiveBridgeSuccess() {
    when(integrationFlowContext.registration(any(IntegrationFlow.class))).thenReturn(registrationBuilder);
    when(registrationBuilder.id(anyString())).thenReturn(registrationBuilder);

    // Given a valid Sftp server configuration with dynamic archive bridging enabled.
    SftpServerConfig serverConfig = SftpServerConfig.builder()
        .name("myServer")
        .dynamicArchiveBridgeEnabled(true)
        .archiveTarget("mqArchiveChannel")
        .build();
    String sftpArchiveChannelName = "sftpArchiveChannel-myServer";

    // When
    String flowId = bridgeManager.registerArchiveBridge(serverConfig, sftpArchiveChannelName);

    // Then: Expect the computed flow id to be returned and registration calls invoked.
    assertThat(flowId).isEqualTo("dynamicArchiveBridge-myServer");
    verify(integrationFlowContext, times(1)).registration(any(IntegrationFlow.class));
    verify(registrationBuilder, times(1)).id("dynamicArchiveBridge-myServer");
    verify(registrationBuilder, times(1)).register();
  }

  @Test
  public void registerArchiveBridgeNotEnabled() {
    // Given a configuration where dynamic archive bridging is disabled.
    SftpServerConfig serverConfig = SftpServerConfig.builder()
        .name("myServer")
        .dynamicArchiveBridgeEnabled(false)
        .archiveTarget("mqArchiveChannel")
        .build();
    String sftpArchiveChannelName = "sftpArchiveChannel-myServer";

    // When
    String flowId = bridgeManager.registerArchiveBridge(serverConfig, sftpArchiveChannelName);

    // Then: Expect a null value with no registration attempted.
    assertThat(flowId).isNull();
    verify(integrationFlowContext, never()).registration(any(IntegrationFlow.class));
  }

  @Test
  public void registerArchiveBridgeMissingArchiveTarget() {
    // Given a configuration where dynamic archive bridging is enabled, but archiveTarget is missing.
    SftpServerConfig serverConfig = SftpServerConfig.builder()
        .name("myServer")
        .dynamicArchiveBridgeEnabled(true)
        .archiveTarget("")
        .build();
    String sftpArchiveChannelName = "sftpArchiveChannel-myServer";

    // When
    String flowId = bridgeManager.registerArchiveBridge(serverConfig, sftpArchiveChannelName);

    // Then: Expect a null value and no registration attempt.
    assertThat(flowId).isNull();
    verify(integrationFlowContext, never()).registration(any(IntegrationFlow.class));
  }
}
