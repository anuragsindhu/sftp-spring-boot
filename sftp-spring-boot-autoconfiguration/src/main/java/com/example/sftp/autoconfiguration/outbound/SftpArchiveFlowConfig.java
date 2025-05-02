package com.example.sftp.autoconfiguration.outbound;

import com.example.sftp.autoconfiguration.AbstractSftpFlowConfig;
import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties;
import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import com.example.sftp.autoconfiguration.SftpSessionFactoryProvider;
import com.example.sftp.autoconfiguration.transformers.ArchivePrePostProcessorTransformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;
import java.util.Objects;

@Slf4j
public class SftpArchiveFlowConfig extends AbstractSftpFlowConfig {

  private final SftpProperties sftpProperties;
  private final SftpSessionFactoryProvider factoryProvider;
  private final ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice;
  private final PlatformTransactionManager transactionManager;
  private final SftpServerConfig serverConfig;

  public SftpArchiveFlowConfig(ApplicationContext applicationContext,
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

    // Determine local archive directory using helper method.
    File localArchiveDirectory = determineLocalDirectory(serverConfig.getArchive(), sftpProperties.getLocalDownloadDir(), "archive", serverName);

    var fileSourceSpec = Files.inboundAdapter(localArchiveDirectory)
        .autoCreateDirectory(true);
    var compositeFilter = createLocalCompositeFilter(serverConfig);
    if (compositeFilter != null) {
      fileSourceSpec.filter(compositeFilter);
    }

    var pollerMetadata = buildPollerMetadata(sftpProperties.getDefaultPoller(), errorHandlingAdvice);
    pollerMetadata.setTaskExecutor(buildTaskExecutor());

    String flowId = "sftpArchiveFlow-" + serverName;
    log.info("Registering SFTP archive flow [{}] for server [{}].", flowId, serverName);

    SftpProperties.RetryProperties effectiveRetry =
        serverConfig.getRetry() != null ? serverConfig.getRetry() : sftpProperties.getDefaultRetry();

    ArchivePrePostProcessorTransformer preTransformer =
        new ArchivePrePostProcessorTransformer(fileProcessor, serverName, true, effectiveRetry);
    ArchivePrePostProcessorTransformer postTransformer =
        new ArchivePrePostProcessorTransformer(fileProcessor, serverName, false, effectiveRetry);

    IntegrationFlow archiveFlow = IntegrationFlow.from(fileSourceSpec.getObject(), c -> c.poller(pollerMetadata))
        .enrichHeaders(h -> h.header("sftpFlowId", flowId)
            .header("destinationPath", serverConfig.getArchive()))
        .transform(String.class, remotePath -> executeInTransaction(remotePath, preTransformer::transform, transactionManager))
        .filter(Objects::nonNull)
        .handle(
            Sftp.outboundGateway(factory, "mv", "payload")
                .renameExpression("headers.destinationPath"),
            spec -> spec.advice(errorHandlingAdvice))
        .transform(String.class, remotePath -> executeInTransaction(remotePath, postTransformer::transform, transactionManager))
        .get();

    registerFlow(flowId, archiveFlow);
  }
}