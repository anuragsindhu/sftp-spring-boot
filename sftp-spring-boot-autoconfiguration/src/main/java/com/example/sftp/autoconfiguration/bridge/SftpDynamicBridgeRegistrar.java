package com.example.sftp.autoconfiguration.bridge;

import com.example.sftp.autoconfiguration.SftpProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Automatically registers dynamic bridging flows for all configured SFTP servers.
 * <p>
 * For each server defined in the SftpProperties, if dynamic bridging is enabled
 * (for upload, download, or archive), this registrar creates a bridge that connects the external channel
 * (specified by the configuration) to the internal SFTP flow channel.
 * </p>
 */
@Slf4j
@Component
public class SftpDynamicBridgeRegistrar {

  @Autowired
  private SftpProperties sftpProperties;

  @Autowired
  private DynamicSftpBridgeManager bridgeManager;

  /**
   * Returns the internal SFTP upload outbound channel name for the given server name.
   *
   * @param serverName the server name
   * @return the upload outbound channel name (e.g. "sftpUploadOutboundChannel-myServer")
   */
  private String getSftpUploadChannelName(String serverName) {
    return "sftpUploadOutboundChannel-" + serverName;
  }

  /**
   * Returns the internal SFTP download inbound channel name for the given server name.
   *
   * @param serverName the server name
   * @return the download inbound channel name (e.g. "sftpDownloadInboundChannel-myServer")
   */
  private String getSftpDownloadChannelName(String serverName) {
    return "sftpDownloadInboundChannel-" + serverName;
  }

  /**
   * Returns the internal SFTP archive channel name for the given server name.
   *
   * @param serverName the server name
   * @return the archive channel name (e.g. "sftpArchiveChannel-myServer")
   */
  private String getSftpArchiveChannelName(String serverName) {
    return "sftpArchiveChannel-" + serverName;
  }

  /**
   * Iterates over all configured SFTP servers and registers dynamic bridging flows
   * for upload, download, and archive based on the server settings.
   */
  @PostConstruct
  public void registerDynamicBridges() {
    if (sftpProperties.getServers() != null) {
      sftpProperties.getServers().forEach(server -> {
        String serverName = server.getName();
        log.info("Registering dynamic bridges for server [{}].", serverName);
        bridgeManager.registerUploadBridge(server, getSftpUploadChannelName(serverName));
        bridgeManager.registerDownloadBridge(server, getSftpDownloadChannelName(serverName));
        bridgeManager.registerArchiveBridge(server, getSftpArchiveChannelName(serverName));
      });
    } else {
      log.warn("No SFTP server configurations found. Skipping dynamic bridge registration.");
    }
  }
}
