package com.example.sftp.autoconfiguration.inbound;

import com.example.sftp.autoconfiguration.AbstractSftpFlowConfig;
import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties;
import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import com.example.sftp.autoconfiguration.SftpSessionFactoryProvider;
import com.example.sftp.autoconfiguration.transformers.DownloadPostProcessorTransformer;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.file.filters.CompositeFileListFilter;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.Objects;

@Slf4j
public class SftpDownloadFlowConfig extends AbstractSftpFlowConfig {

  private final SftpProperties sftpProperties;
  private final SftpSessionFactoryProvider factoryProvider;
  private final ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice;
  private final PlatformTransactionManager transactionManager;
  private final SftpServerConfig serverConfig;

  public SftpDownloadFlowConfig(ApplicationContext applicationContext,
                                SftpProperties sftpProperties,
                                SftpFileProcessor globalFileProcessor,
                                SftpSessionFactoryProvider factoryProvider,
                                ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice,
                                PlatformTransactionManager transactionManager,
                                IntegrationFlowContext flowContext,
                                SftpServerConfig serverConfig) {
    super(applicationContext, sftpProperties, globalFileProcessor, flowContext);
    this.sftpProperties = sftpProperties;
    this.factoryProvider = factoryProvider;
    this.errorHandlingAdvice = errorHandlingAdvice;
    this.transactionManager = transactionManager;
    this.serverConfig = serverConfig;
    registerFlowForServer();
  }

  private void registerFlowForServer() {
    String serverName = serverConfig.getName();
    var factory = factoryProvider.getFactory(serverName);
    SftpFileProcessor fileProcessor = obtainProcessor(serverConfig);

    // Determine local download directory using the helper
    File localDownloadDirectory = determineLocalDirectory(serverConfig.getLocalDownloadDir(), sftpProperties.getLocalDownloadDir(), "download", serverName);

    PollerMetadata pollerMetadata = buildPollerMetadata(
        serverConfig.getPoller() != null ? serverConfig.getPoller() : sftpProperties.getDefaultPoller(),
        errorHandlingAdvice);
    pollerMetadata.setTaskExecutor(buildTaskExecutor());

    SftpProperties.RetryProperties effectiveRetry =
        serverConfig.getRetry() != null ? serverConfig.getRetry() : sftpProperties.getDefaultRetry();

    if (StringUtils.hasText(serverConfig.getFrom())) {
      var inboundAdapterBuilder = Sftp.inboundAdapter(factory)
          .preserveTimestamp(true)
          .remoteDirectory(serverConfig.getFrom())
          .localDirectory(localDownloadDirectory)
          .autoCreateLocalDirectory(true)
          .deleteRemoteFiles(!Objects.isNull(serverConfig.getDeleteAfterDownload()) && serverConfig.getDeleteAfterDownload());

      CompositeFileListFilter<SftpClient.DirEntry> compositeFilter = createRemoteCompositeFilter(serverConfig);
      if (compositeFilter != null) {
        inboundAdapterBuilder.filter(compositeFilter);
      }

      String flowId = "sftpDownloadFlow-" + serverName;
      log.info("Registering SFTP download flow [{}] for server [{}].", flowId, serverName);

      DownloadPostProcessorTransformer transformer =
          new DownloadPostProcessorTransformer(fileProcessor, serverName, effectiveRetry);

      IntegrationFlow downloadFlow = IntegrationFlow.from(inboundAdapterBuilder,
              c -> c.poller(pollerMetadata))
          .enrichHeaders(h -> h.header("sftpFlowId", flowId))
          .transform(File.class, file -> executeInTransaction(file, transformer::transform, transactionManager))
          .get();
      registerFlow(flowId, downloadFlow);
    } else {
      log.info("No 'from' directory configured for server {}. Skipping download flow registration.", serverName);
    }
  }
}
