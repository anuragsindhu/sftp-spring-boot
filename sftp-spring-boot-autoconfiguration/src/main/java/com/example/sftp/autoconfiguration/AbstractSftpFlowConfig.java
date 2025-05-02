package com.example.sftp.autoconfiguration;

import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import com.example.sftp.autoconfiguration.exception.LocalDownloadDirectoryCreationException;
import com.example.sftp.autoconfiguration.filters.SftpFileSizeFilter;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.file.filters.AbstractFileListFilter;
import org.springframework.integration.file.filters.AcceptOnceFileListFilter;
import org.springframework.integration.file.filters.CompositeFileListFilter;
import org.springframework.integration.file.filters.RegexPatternFileListFilter;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.metadata.ConcurrentMetadataStore;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.sftp.filters.SftpPersistentAcceptOnceFileListFilter;
import org.springframework.integration.sftp.filters.SftpRegexPatternFileListFilter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * An abstract base class that provides common utility methods for SFTP flow configurations.
 * This version is adjusted for the configurer approach where flows are dynamically registered.
 */
@Slf4j
public abstract class AbstractSftpFlowConfig {

  protected final ApplicationContext applicationContext;
  protected final SftpProperties sftpProperties;
  protected final SftpFileProcessor globalFileProcessor;
  protected final IntegrationFlowContext flowContext; // For dynamic flow registration

  protected static final long DEFAULT_FALLBACK_FIXED_DELAY = 5000L;

  /**
   * Constructor injecting common dependencies.
   *
   * @param applicationContext  the Spring application context.
   * @param sftpProperties      the SFTP properties.
   * @param globalFileProcessor the globally defined SFTP file processor.
   * @param flowContext         the integration flow context for dynamic flow registration.
   */
  public AbstractSftpFlowConfig(ApplicationContext applicationContext,
                                SftpProperties sftpProperties,
                                SftpFileProcessor globalFileProcessor,
                                IntegrationFlowContext flowContext) {
    this.applicationContext = applicationContext;
    this.sftpProperties = sftpProperties;
    this.globalFileProcessor = globalFileProcessor;
    this.flowContext = flowContext;
  }

  /**
   * Retrieves the SftpFileProcessor for the given server configuration.
   * If a custom processor is defined (via processorClass), it is obtained from the ApplicationContext;
   * otherwise, the globalFileProcessor is returned.
   *
   * @param server the server configuration.
   * @return the SftpFileProcessor.
   */
  protected SftpFileProcessor obtainProcessor(SftpServerConfig server) {
    if (server.getProcessorClass() != null) {
      try {
        return applicationContext.getBean(server.getProcessorClass());
      } catch (Exception e) {
        log.warn("[{}] Could not retrieve bean for processor class {}. Falling back to global default.",
            server.getName(), server.getProcessorClass().getName(), e);
      }
    }
    return globalFileProcessor;
  }

  /**
   * Builds a PollerMetadata instance based on the provided poller configuration and attaches error-handling advice.
   *
   * @param poller the poller configuration.
   * @param advice the error-handling advice to attach.
   * @return the PollerMetadata instance.
   */
  protected PollerMetadata buildPollerMetadata(SftpProperties.PollerProperties poller,
                                               ExpressionEvaluatingRequestHandlerAdvice advice) {
    long fallbackDelay = DEFAULT_FALLBACK_FIXED_DELAY;
    if (poller != null && poller.getFallbackFixedDelay() != null) {
      fallbackDelay = poller.getFallbackFixedDelay();
    }
    if (poller != null) {
      if ("fixed".equalsIgnoreCase(poller.getType())) {
        if (poller.getFixedInterval() != null) {
          return Pollers.fixedDelay(poller.getFixedInterval())
              .advice(advice)
              .getObject();
        }
      } else if ("timeWindow".equalsIgnoreCase(poller.getType())) {
        if (poller.getWindowInterval() != null &&
            StringUtils.hasText(poller.getStartTime()) &&
            StringUtils.hasText(poller.getEndTime()) &&
            StringUtils.hasText(poller.getTimeZone())) {
          DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm");
          LocalTime start = LocalTime.parse(poller.getStartTime(), dtf);
          LocalTime end = LocalTime.parse(poller.getEndTime(), dtf);
          ZoneId zoneId = ZoneId.of(poller.getTimeZone());
          return Pollers.trigger(new TimeWindowTrigger(poller.getWindowInterval(), start, end, zoneId))
              .advice(advice)
              .getObject();
        }
      }
    }
    return Pollers.fixedDelay(fallbackDelay)
        .advice(advice)
        .getObject();
  }

  /**
   * Registers an integration flow with a unique identifier in the IntegrationFlowContext.
   *
   * @param flowName a unique flow name.
   * @param flow     the integration flow to register.
   */
  protected void registerFlow(String flowName, IntegrationFlow flow) {
    flowContext.registration(flow).id(flowName).register();
  }

  /**
   * Builds a ThreadPoolTaskExecutor based on throughput configuration defined in SftpProperties.
   *
   * @return the configured Executor.
   */
  protected Executor buildTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    SftpProperties.Throughput throughput = sftpProperties.getThroughput();
    int corePoolSize = throughput.getCorePoolSize() != null ? throughput.getCorePoolSize() : 10;
    int maxPoolSize = throughput.getMaxPoolSize() != null ? throughput.getMaxPoolSize() : 20;
    int queueCapacity = throughput.getQueueCapacity() != null ? throughput.getQueueCapacity() : 100;
    String threadNamePrefix = throughput.getThreadNamePrefix() != null ? throughput.getThreadNamePrefix() : "SftpInbound-";
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix(threadNamePrefix);
    executor.initialize();
    return executor;
  }

  /**
   * General helper to determine a local directory.
   *
   * @param serverLocalOverride server-specific override for local directory.
   * @param globalLocalDir      global base local directory.
   * @param suffix              a sub-directory name (e.g., "download", "upload", "archive").
   * @param serverName          server identifier.
   * @return the determined (and created) local directory.
   */
  protected File determineLocalDirectory(String serverLocalOverride, String globalLocalDir, String suffix, String serverName) {
    String effectiveDir;
    if (StringUtils.hasText(serverLocalOverride)) {
      effectiveDir = serverLocalOverride;
    } else if (StringUtils.hasText(globalLocalDir)) {
      effectiveDir = globalLocalDir + "/" + suffix + "/" + serverName;
    } else {
      effectiveDir = "local-" + suffix + "/" + serverName;
    }
    File localDir = new File(effectiveDir);
    if (!localDir.exists() && !localDir.mkdirs()) {
      String errMsg = "Could not create local " + suffix + " directory: " + localDir;
      throw new LocalDownloadDirectoryCreationException(errMsg);
    }
    return localDir;
  }

  /**
   * Executes a transformation within a transaction.
   *
   * @param payload     the payload to transform.
   * @param transformer a function that transforms the payload.
   * @param txManager   the transaction manager to use.
   * @param <T>         the type of the payload.
   * @return the transformed payload.
   */
  protected <T> T executeInTransaction(T payload, Function<T, T> transformer, PlatformTransactionManager txManager) {
    TransactionTemplate txTemplate = new TransactionTemplate(txManager);
    return txTemplate.execute(status -> transformer.apply(payload));
  }

  /**
   * Creates a composite remote file filter using:
   * <ul>
   *   <li>A persistent accept-once filter based on a distributed metadata store.</li>
   *   <li>A regex-based filter if a file pattern is provided.</li>
   *   <li>A file size filter if minimum/maximum size constraints are set.</li>
   * </ul>
   *
   * @param serverConfig the SFTP server configuration.
   * @return a CompositeFileListFilter for remote SftpClient.DirEntry objects, or null if no sub-filter is added.
   */
  protected CompositeFileListFilter<SftpClient.DirEntry> createRemoteCompositeFilter(SftpServerConfig serverConfig) {
    try {
      CompositeFileListFilter<SftpClient.DirEntry> compositeFilter = new CompositeFileListFilter<>();
      boolean filterAdded = false;
      if (Boolean.TRUE.equals(serverConfig.getEnableMetadataStore())) {
        // Retrieve distributed metadata store bean.
        ConcurrentMetadataStore metadataStore =
            applicationContext.getBean(ConcurrentMetadataStore.class);
        compositeFilter.addFilter(new SftpPersistentAcceptOnceFileListFilter(metadataStore,
            "sftpRemoteFlow-" + serverConfig.getName()));
        filterAdded = true;
      }
      if (StringUtils.hasText(serverConfig.getFilePattern())) {
        compositeFilter.addFilter(new SftpRegexPatternFileListFilter(serverConfig.getFilePattern()));
        filterAdded = true;
      }
      if (serverConfig.getMinFileSize() != null || serverConfig.getMaxFileSize() != null) {
        compositeFilter.addFilter(new SftpFileSizeFilter(serverConfig.getMinFileSize(), serverConfig.getMaxFileSize()));
        filterAdded = true;
      }
      return filterAdded ? compositeFilter : null;
    } catch (Exception ex) {
      log.error("Error while creating remote composite file filter for server [{}]: {}", serverConfig.getName(), ex.getMessage(), ex);
      throw new RuntimeException("Failed to create remote composite file filter for server " + serverConfig.getName(), ex);
    }
  }

  /**
   * Creates a composite local file filter using:
   * <ul>
   *   <li>An in-memory accept-once filter.</li>
   *   <li>A regex-based filter if a file pattern is provided.</li>
   *   <li>A file size filter if minimum/maximum size constraints are set.</li>
   * </ul>
   *
   * @param serverConfig the SFTP server configuration.
   * @return a CompositeFileListFilter for File objects, or null if no sub-filter is added.
   */
  protected CompositeFileListFilter<File> createLocalCompositeFilter(SftpServerConfig serverConfig) {
    CompositeFileListFilter<File> compositeFilter = new CompositeFileListFilter<>();
    boolean filterAdded = false;
    if (Boolean.TRUE.equals(serverConfig.getEnableMetadataStore())) {
      compositeFilter.addFilter(new AcceptOnceFileListFilter<>());
      filterAdded = true;
    }
    if (StringUtils.hasText(serverConfig.getFilePattern())) {
      compositeFilter.addFilter(new RegexPatternFileListFilter(serverConfig.getFilePattern()));
      filterAdded = true;
    }
    if (serverConfig.getMinFileSize() != null || serverConfig.getMaxFileSize() != null) {
      compositeFilter.addFilter(new LocalFileSizeFilter(serverConfig.getMinFileSize(), serverConfig.getMaxFileSize()));
      filterAdded = true;
    }
    return filterAdded ? compositeFilter : null;
  }

  /**
   * A simple local file size filter that extends AbstractFileListFilter.
   */
  protected static class LocalFileSizeFilter extends AbstractFileListFilter<File> {

    private final Long minSize;
    private final Long maxSize;

    public LocalFileSizeFilter(Long minSize, Long maxSize) {
      this.minSize = minSize;
      this.maxSize = maxSize;
    }

    @Override
    public boolean accept(File file) {
      long fileSize = file.length();
      return (minSize == null || fileSize >= minSize) && (maxSize == null || fileSize <= maxSize);
    }
  }
}
