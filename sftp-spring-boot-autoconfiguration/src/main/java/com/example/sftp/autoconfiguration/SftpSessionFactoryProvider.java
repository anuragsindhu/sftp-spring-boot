package com.example.sftp.autoconfiguration;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.integration.file.remote.session.SessionFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides SFTP session factories for each configured SFTP server.
 * <p>
 * Uses the updated {@link SessionFactoryBuilder} which builds a caching session factory
 * returning a {@code SessionFactory<SftpClient.DirEntry>}. The provider iterates over the list
 * of SFTP server configurations (from {@code SftpProperties}) and builds a session factory for each,
 * keyed by the server's unique name.
 * </p>
 */
@Slf4j
public class SftpSessionFactoryProvider {

  private final SftpProperties sftpProperties;
  private final Map<String, SessionFactory<SftpClient.DirEntry>> factoryMap = new HashMap<>();

  /**
   * Constructs the provider and initializes session factories.
   *
   * @param sftpProperties the SFTP properties.
   */
  public SftpSessionFactoryProvider(SftpProperties sftpProperties) {
    this.sftpProperties = sftpProperties;
    initFactories();
  }

  private void initFactories() {
    if (sftpProperties.getServers() != null) {
      for (SftpProperties.SftpServerConfig server : sftpProperties.getServers()) {
        try {
          // Use the configured cache size from server properties.
          SessionFactory<SftpClient.DirEntry> factory =
              SessionFactoryBuilder.builder()
                  .host(server.getHost())
                  .port(server.getPort())
                  .username(server.getUsername())
                  .applyAuthentication(server.getPassword(),
                      server.getPrivateKey(),
                      server.getPrivateKeyPassphrase())
                  .cacheSize(server.getCacheSize())
                  .build();
          factoryMap.put(server.getName(), factory);
          log.info("Initialized SFTP Session Factory for server: {}", server.getName());
        } catch (Exception e) {
          log.error("Error initializing SFTP Session Factory for server {}: {}",
              server.getName(), e.getMessage(), e);
        }
      }
    }
  }

  /**
   * Retrieves the session factory associated with the given server name.
   *
   * @param serverName the unique name of the SFTP server.
   * @return the {@code SessionFactory<SftpClient.DirEntry>} for that server.
   * @throws IllegalArgumentException if no factory is found for the given name.
   */
  public SessionFactory<SftpClient.DirEntry> getFactory(String serverName) {
    SessionFactory<SftpClient.DirEntry> factory = factoryMap.get(serverName);
    if (factory == null) {
      throw new IllegalArgumentException("No SFTP Factory found for server: " + serverName);
    }
    return factory;
  }
}
