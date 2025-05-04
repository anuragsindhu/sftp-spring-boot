package com.example.sftp.autoconfiguration;

import com.example.sftp.autoconfiguration.validation.ValidPollerProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for SFTP integration.
 * <p>
 * Global defaults for local download directories, poller settings, retry properties,
 * and throughput (thread pool) configuration may be overridden per server.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "sftp")
public class SftpProperties {

  /**
   * Global default for the local download directory.
   */
  @NotBlank(message = "Local download directory must not be blank")
  private String localDownloadDir;

  /**
   * Global poller configuration.
   */
  @NotNull(message = "Default poller properties must be provided")
  private PollerProperties defaultPoller;

  /**
   * Global default retry configuration.
   */
  @NotNull(message = "Default retry properties must be provided")
  @Builder.Default
  private RetryProperties defaultRetry = RetryProperties.builder()
      .maxAttempts(3)
      .initialInterval(1500L)
      .multiplier(2.0)
      .maxInterval(5000L)
      .build();

  /**
   * List of server-specific SFTP configurations.
   */
  private List<SftpServerConfig> servers;

  /**
   * Global throughput configuration for controlling thread pool behavior.
   */
  @NotNull(message = "Throughput configuration must be provided")
  @Builder.Default
  private Throughput throughput = Throughput.builder()
      .corePoolSize(10)
      .maxPoolSize(20)
      .queueCapacity(100)
      .threadNamePrefix("SftpInbound-")
      .build();

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @NotNull
  @ValidPollerProperties
  public static class PollerProperties {
    /**
     * Type of polling: "fixed" or "timeWindow".
     */
    @NotBlank(message = "Poller type must not be blank")
    private String type;
    /**
     * For fixed polling: the interval (in milliseconds).
     */
    private Long fixedInterval;
    /**
     * For time window polling: the start time (HH:mm).
     */
    private String startTime;
    /**
     * For time window polling: the end time (HH:mm).
     */
    private String endTime;
    /**
     * For time window polling: the polling interval during the active window (in milliseconds).
     */
    private Long windowInterval;
    /**
     * For time window polling: the time zone (e.g., "UTC", "America/New_York").
     */
    private String timeZone;
    /**
     * Fallback fixed delay (in milliseconds) if no valid poller settings are provided.
     */
    private Long fallbackFixedDelay;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RetryProperties {
    /**
     * Maximum number of total attempts (initial attempt + retries).
     */
    @Min(value = 1, message = "Max attempts must be at least 1")
    private Integer maxAttempts;
    /**
     * Initial retry interval (in milliseconds).
     */
    @Min(value = 1, message = "Initial retry interval must be at least 1 millisecond")
    private Long initialInterval;
    /**
     * Multiplier for exponential backoff.
     */
    @NotNull(message = "Multiplier must be provided")
    private Double multiplier;
    /**
     * Maximum interval (in milliseconds) between retries.
     */
    @Min(value = 1, message = "Max retry interval must be at least 1 millisecond")
    private Long maxInterval;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SftpServerConfig {
    /**
     * Unique identifier for the server configuration.
     */
    @NotBlank(message = "Server name must not be blank")
    private String name;
    /**
     * Remote host address.
     */
    @NotBlank(message = "Server host must not be blank")
    private String host;
    /**
     * Remote port; default is 22.
     */
    @Min(value = 1, message = "Server port must be greater than 0")
    private int port;
    /**
     * Username for authentication.
     */
    @NotBlank(message = "Username must not be blank")
    private String username;
    /**
     * Password for authentication.
     */
    private String password;
    /**
     * Content or path of the private key (for key-based authentication).
     */
    private String privateKey;
    /**
     * Passphrase for the private key.
     */
    private String privateKeyPassphrase;
    /**
     * Remote directory from which files are downloaded.
     */
    @NotBlank(message = "Remote 'from' directory must not be blank")
    private String from;
    /**
     * Remote directory to which files will be uploaded.
     */
    private String to;
    /**
     * Remote directory where files will be archived.
     */
    private String archive;
    /**
     * Optional override for the local download directory.
     */
    private String localDownloadDir;
    /**
     * When true, deletes remote files after successful download and processing.
     */
    private Boolean deleteAfterDownload;
    /**
     * When true, enables the metadata store filter to avoid duplicate processing.
     */
    private Boolean enableMetadataStore;
    /**
     * A regex-based file pattern for filtering files.
     */
    private String filePattern;
    /**
     * Optional minimum file size (in bytes) for filtering.
     */
    @Min(value = 0, message = "Minimum file size must be 0 or greater")
    private Long minFileSize;
    /**
     * Optional maximum file size (in bytes) for filtering.
     */
    @Min(value = 1, message = "Maximum file size must be greater than 0")
    private Long maxFileSize;
    /**
     * Optional per-server poller configuration.
     */
    private PollerProperties poller;
    /**
     * Optional per-server retry configuration.
     */
    private RetryProperties retry;
    /**
     * Optional processor class for SFTP file events.
     */
    private Class<? extends SftpFileProcessor> processorClass;
    /**
     * Optional cache size for the SFTP session factory. Defaults to 10 if not provided.
     */
    @Min(value = 1, message = "Cache size must be at least 1")
    @Builder.Default
    private int cacheSize = 10;
    /**
     * Optional override for the local upload directory.
     */
    private String localUploadDir;
    /**
     * Flag indicating whether a dynamic bridge should be created for uploads.
     */
    private Boolean dynamicUploadBridgeEnabled;

    /**
     * The name of the external inbound channel for uploads (e.g. "kafkaInboundChannel").
     */
    private String uploadSource;

    /**
     * Flag indicating whether a dynamic bridge should be created for downloads.
     */
    private Boolean dynamicDownloadBridgeEnabled;

    /**
     * The external target channel for downloads (e.g. "mqDownloadChannel").
     */
    private String downloadTarget;

    /**
     * Flag indicating whether a dynamic bridge should be created for archive operations.
     */
    private Boolean dynamicArchiveBridgeEnabled;

    /**
     * The external target channel for archives (e.g. "mqArchiveChannel").
     */
    private String archiveTarget;
  }

  /**
   * Controls thread pool behavior for inbound file processing.
   * Clients can override these defaults via configuration.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Throughput {
    /**
     * Core pool size for processing. Defaults to 10.
     */
    @Builder.Default
    @Min(value = 1, message = "Core pool size must be at least 1")
    private Integer corePoolSize = 10;
    /**
     * Maximum pool size for processing. Defaults to 20.
     */
    @Builder.Default
    @Min(value = 1, message = "Maximum pool size must be at least 1")
    private Integer maxPoolSize = 20;
    /**
     * Queue capacity for the thread pool. Defaults to 100.
     */
    @Builder.Default
    @Min(value = 1, message = "Queue capacity must be at least 1")
    private Integer queueCapacity = 100;
    /**
     * The thread name prefix for the task executor.
     * Defaults to "SftpInbound-".
     */
    @Builder.Default
    @NotBlank(message = "Thread name prefix must not be blank")
    private String threadNamePrefix = "SftpInbound-";
  }
}
