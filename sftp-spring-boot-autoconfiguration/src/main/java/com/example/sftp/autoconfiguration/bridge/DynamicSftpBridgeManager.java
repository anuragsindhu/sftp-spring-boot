package com.example.sftp.autoconfiguration.bridge;

import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Provides methods to dynamically register bridge flows that connect external channels
 * to the internal SFTP integration channels.
 * <p>
 * For example, a bridge can be registered to send messages from a Kafka inbound channel (configured
 * via uploadSource) to the SFTP upload outbound channel for a given server.
 * </p>
 */
@Slf4j
@Component
public class DynamicSftpBridgeManager {

  private final IntegrationFlowContext integrationFlowContext;

  /**
   * Constructs the dynamic bridge manager with the given IntegrationFlowContext.
   *
   * @param integrationFlowContext the context used for runtime registration of flows
   */
  public DynamicSftpBridgeManager(IntegrationFlowContext integrationFlowContext) {
    this.integrationFlowContext = integrationFlowContext;
  }

  /**
   * Registers a dynamic bridge for upload flows.
   * This bridge routes messages from the external source channel (defined in {@code uploadSource})
   * to the internal SFTP upload outbound channel.
   *
   * @param serverConfig          the SFTP server configuration containing dynamic bridge settings
   * @param sftpUploadChannelName the name of the internal SFTP upload channel (e.g. "sftpUploadOutboundChannel-myServer")
   * @return the dynamic flow registration id or {@code null} if bridging is not enabled
   */
  public String registerUploadBridge(SftpServerConfig serverConfig, String sftpUploadChannelName) {
    if (Boolean.TRUE.equals(serverConfig.getDynamicUploadBridgeEnabled())
        && StringUtils.hasText(serverConfig.getUploadSource())) {
      String sourceChannelName = serverConfig.getUploadSource();
      String flowId = "dynamicUploadBridge-" + serverConfig.getName();

      try {
        IntegrationFlow flow = IntegrationFlow.from(sourceChannelName)
            .channel(sftpUploadChannelName)
            .get();

        integrationFlowContext.registration(flow)
            .id(flowId)
            .register();

        log.info("Dynamic upload bridge flow [{}] registered: external source [{}] => internal channel [{}].",
            flowId, sourceChannelName, sftpUploadChannelName);
        return flowId;
      } catch (Exception e) {
        log.error("Failed to register dynamic upload bridge for server [{}]: {}",
            serverConfig.getName(), e.getMessage(), e);
        throw new RuntimeException("Dynamic upload bridge registration failed for server: " + serverConfig.getName(), e);
      }
    }
    log.info("Dynamic upload bridge not registered for server [{}] because bridging is disabled or uploadSource is not provided.",
        serverConfig.getName());
    return null;
  }

  /**
   * Registers a dynamic bridge for download flows.
   * This bridge routes messages from the internal SFTP download inbound channel to an external target channel.
   *
   * @param serverConfig             the SFTP server configuration
   * @param sftpDownloadChannelName  the name of the SFTP download inbound channel (e.g. "sftpDownloadInboundChannel-myServer")
   * @return the dynamic flow registration id or {@code null} if bridging is not enabled
   */
  public String registerDownloadBridge(SftpServerConfig serverConfig, String sftpDownloadChannelName) {
    if (Boolean.TRUE.equals(serverConfig.getDynamicDownloadBridgeEnabled())
        && StringUtils.hasText(serverConfig.getDownloadTarget())) {
      String targetChannelName = serverConfig.getDownloadTarget();
      String flowId = "dynamicDownloadBridge-" + serverConfig.getName();

      try {
        IntegrationFlow flow = IntegrationFlow.from(sftpDownloadChannelName)
            .channel(targetChannelName)
            .get();

        integrationFlowContext.registration(flow)
            .id(flowId)
            .register();

        log.info("Dynamic download bridge flow [{}] registered: internal channel [{}] => external target [{}].",
            flowId, sftpDownloadChannelName, targetChannelName);
        return flowId;
      } catch (Exception e) {
        log.error("Failed to register dynamic download bridge for server [{}]: {}",
            serverConfig.getName(), e.getMessage(), e);
        throw new RuntimeException("Dynamic download bridge registration failed for server: " + serverConfig.getName(), e);
      }
    }
    log.info("Dynamic download bridge not registered for server [{}] because bridging is disabled or downloadTarget is not provided.",
        serverConfig.getName());
    return null;
  }

  /**
   * Registers a dynamic bridge for archive flows.
   * This bridge routes messages from the internal SFTP archive channel to an external target channel.
   *
   * @param serverConfig           the SFTP server configuration
   * @param sftpArchiveChannelName the name of the SFTP archive channel (e.g. "sftpArchiveChannel-myServer")
   * @return the dynamic flow registration id or {@code null} if bridging is not enabled
   */
  public String registerArchiveBridge(SftpServerConfig serverConfig, String sftpArchiveChannelName) {
    if (Boolean.TRUE.equals(serverConfig.getDynamicArchiveBridgeEnabled())
        && StringUtils.hasText(serverConfig.getArchiveTarget())) {
      String targetChannelName = serverConfig.getArchiveTarget();
      String flowId = "dynamicArchiveBridge-" + serverConfig.getName();

      try {
        IntegrationFlow flow = IntegrationFlow.from(sftpArchiveChannelName)
            .channel(targetChannelName)
            .get();

        integrationFlowContext.registration(flow)
            .id(flowId)
            .register();

        log.info("Dynamic archive bridge flow [{}] registered: internal channel [{}] => external target [{}].",
            flowId, sftpArchiveChannelName, targetChannelName);
        return flowId;
      } catch (Exception e) {
        log.error("Failed to register dynamic archive bridge for server [{}]: {}",
            serverConfig.getName(), e.getMessage(), e);
        throw new RuntimeException("Dynamic archive bridge registration failed for server: " + serverConfig.getName(), e);
      }
    }
    log.info("Dynamic archive bridge not registered for server [{}] because bridging is disabled or archiveTarget is not provided.",
        serverConfig.getName());
    return null;
  }
}
