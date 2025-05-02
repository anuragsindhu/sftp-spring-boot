package com.example.sftp.autoconfiguration;

import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.util.StringUtils;

/**
 * A fluent builder that collects SFTP connection settings and authentication details,
 * builds a DefaultSftpSessionFactory, and wraps it with a CachingSessionFactory.
 * The final return type is SessionFactory<SftpClient.DirEntry>.
 */
public class SessionFactoryBuilder {

  private String host;
  private int port = 22;
  private String username;
  private String password;
  private String privateKey;
  private String privateKeyPassphrase;

  // Optionally, allow configuration of the pool size. Default set to 10.
  private int cacheSize = 10;

  private SessionFactoryBuilder() {
  }

  /**
   * Creates a new builder instance.
   *
   * @return a new SftpSessionFactoryBuilder.
   */
  public static SessionFactoryBuilder builder() {
    return new SessionFactoryBuilder();
  }

  public SessionFactoryBuilder host(String host) {
    this.host = host;
    return this;
  }

  public SessionFactoryBuilder port(int port) {
    this.port = port;
    return this;
  }

  public SessionFactoryBuilder username(String username) {
    this.username = username;
    return this;
  }

  /**
   * Configures authentication details. If a non-blank privateKey is provided,
   * key-based authentication is enabled; otherwise, the password is used.
   *
   * @param password             the password for authentication (if no key is provided)
   * @param privateKey           the private key content (or file path) for key-based authentication
   * @param privateKeyPassphrase an optional passphrase for the private key
   * @return the current builder instance
   */
  public SessionFactoryBuilder applyAuthentication(String password, String privateKey, String privateKeyPassphrase) {
    this.password = password;
    this.privateKey = privateKey;
    this.privateKeyPassphrase = privateKeyPassphrase;
    return this;
  }

  /**
   * Optionally configure the cache (pool) size (i.e. number of sessions to cache).
   *
   * @param cacheSize the maximum number of sessions to cache (default is 10)
   * @return the current builder instance
   */
  public SessionFactoryBuilder cacheSize(int cacheSize) {
    this.cacheSize = cacheSize;
    return this;
  }

  /**
   * Builds and returns a SessionFactory parameterized with SftpClient.DirEntry.
   * The underlying DefaultSftpSessionFactory is wrapped in a CachingSessionFactory.
   *
   * @return a cached SessionFactory to create SFTP sessions.
   */
  public SessionFactory<SftpClient.DirEntry> build() {
    // Create the raw SFTP session factory.
    DefaultSftpSessionFactory delegateFactory = new DefaultSftpSessionFactory();
    delegateFactory.setHost(host);
    delegateFactory.setPort(port);
    delegateFactory.setUser(username);

    if (privateKey != null && !privateKey.isBlank()) {
      // Use key-based authentication.
      delegateFactory.setPrivateKey(new ByteArrayResource(privateKey.getBytes()));
      if (privateKeyPassphrase != null && !privateKeyPassphrase.isBlank()) {
        delegateFactory.setPrivateKeyPassphrase(privateKeyPassphrase);
      }
    } else if (StringUtils.hasText(password)) {
      // Use password-based authentication.
      delegateFactory.setPassword(password);
    } else {
      throw new IllegalArgumentException("No authentication details provided—supply either a password or a private key.");
    }

    delegateFactory.setAllowUnknownKeys(false);

    // Wrap the delegate factory in a caching session factory.
    CachingSessionFactory<SftpClient.DirEntry> cachingFactory =
        new CachingSessionFactory<>(delegateFactory);
    cachingFactory.setPoolSize(cacheSize);

    return cachingFactory;
  }
}
