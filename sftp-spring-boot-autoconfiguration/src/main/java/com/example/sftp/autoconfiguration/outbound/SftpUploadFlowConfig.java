package com.example.sftp.autoconfiguration.outbound;

import com.example.sftp.autoconfiguration.AbstractSftpFlowConfig;
import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties;
import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import com.example.sftp.autoconfiguration.SftpSessionFactoryProvider;
import com.example.sftp.autoconfiguration.transformers.UploadPreProcessorTransformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.file.filters.CompositeFileListFilter;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;

@Slf4j
public class SftpUploadFlowConfig extends AbstractSftpFlowConfig {

  private final SftpProperties sftpProperties;
  private final SftpSessionFactoryProvider factoryProvider;
  private final ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice;
  private final PlatformTransactionManager transactionManager;
  private final SftpServerConfig serverConfig;

  public SftpUploadFlowConfig(ApplicationContext applicationContext,
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
    String serverName = (serverConfig.getName() != null ? serverConfig.getName() : "default");
    SftpFileProcessor fileProcessor = obtainProcessor(serverConfig);

    // Determine local upload directory using helper method from the abstract class.
    File localUploadDirectory = determineLocalDirectory(serverConfig.getLocalUploadDir(),
        sftpProperties.getLocalDownloadDir(),
        "upload",
        serverName);

    // Create the local file source using SI 6.x DSL.
    var fileSourceSpec = Files.inboundAdapter(localUploadDirectory)
        .autoCreateDirectory(true);
    CompositeFileListFilter<File> compositeFilter = createLocalCompositeFilter(serverConfig);
    if (compositeFilter != null) {
      fileSourceSpec.filter(compositeFilter);
    }

    // Build poller metadata and assign a custom executor.
    var pollerMetadata = buildPollerMetadata(sftpProperties.getDefaultPoller(), errorHandlingAdvice);
    pollerMetadata.setTaskExecutor(buildTaskExecutor());

    SftpProperties.RetryProperties effectiveRetry =
        serverConfig.getRetry() != null ? serverConfig.getRetry() : sftpProperties.getDefaultRetry();

    String flowId = "sftpUploadFlow-" + serverName;
    log.info("Registering SFTP upload flow [{}] for server [{}].", flowId, serverName);

    // Pre-process the file (validate/prepare) using the transformer, wrapped in a transaction.
    UploadPreProcessorTransformer transformer =
        new UploadPreProcessorTransformer(fileProcessor, serverName, effectiveRetry);

    // Get SFTP session factory for outbound adapter.
    var factory = factoryProvider.getFactory(serverName);

    // Build integration flow:
    // - Read file from the local directory.
    // - Enrich with flow-identifying header.
    // - Pre-process the file in a transaction.
    // - Finally, handle the file using an outbound adapter which uploads the file to the remote directory.
    IntegrationFlow uploadFlow = IntegrationFlow.from(fileSourceSpec.getObject(), c -> c.poller(pollerMetadata))
        .enrichHeaders(h -> h.header("sftpFlowId", flowId))
        .transform(File.class, file -> executeInTransaction(file, transformer::transform, transactionManager))
        .handle(Sftp.outboundAdapter(factory)
                .remoteDirectory(serverConfig.getTo())
                .autoCreateDirectory(true),
            spec -> spec.advice(errorHandlingAdvice))
        .get();

    registerFlow(flowId, uploadFlow);
  }
}
