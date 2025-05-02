package com.example.sftp.autoconfiguration;

import com.example.sftp.autoconfiguration.inbound.SftpDownloadFlowConfig;
import com.example.sftp.autoconfiguration.outbound.SftpArchiveFlowConfig;
import com.example.sftp.autoconfiguration.outbound.SftpUploadFlowConfig;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.transaction.PlatformTransactionManager;

public class SftpFlowsAutoConfigurer {

  private final SftpProperties properties;
  private final SftpFileProcessor processor;
  private final SftpSessionFactoryProvider factoryProvider;
  private final ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice;
  private final PlatformTransactionManager transactionManager;
  private final GenericApplicationContext context;
  private final IntegrationFlowContext flowContext;

  public SftpFlowsAutoConfigurer(SftpProperties properties,
                                 SftpFileProcessor processor,
                                 SftpSessionFactoryProvider factoryProvider,
                                 ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice,
                                 PlatformTransactionManager transactionManager,
                                 GenericApplicationContext context,
                                 IntegrationFlowContext flowContext) {
    this.properties = properties;
    this.processor = processor;
    this.factoryProvider = factoryProvider;
    this.errorHandlingAdvice = errorHandlingAdvice;
    this.transactionManager = transactionManager;
    this.context = context;
    this.flowContext = flowContext;
    registerFlows();
  }

  private void registerFlows() {
    if (properties.getServers() != null) {
      for (SftpProperties.SftpServerConfig server : properties.getServers()) {
        // You can compute a bean name suffix based on server name (or index)
        String serverName = server.getName() != null ? server.getName() : "default";
        // Register download flow if "from" property is set.
        if (server.getFrom() != null && !server.getFrom().isEmpty()) {
          String beanName = "sftpDownloadFlowConfig-" + serverName;
          context.registerBean(beanName, SftpDownloadFlowConfig.class,
              () -> new SftpDownloadFlowConfig(
                  context, properties, processor, factoryProvider, errorHandlingAdvice, transactionManager, flowContext, server));
        }
        // Register upload flow if "to" property is set.
        if (server.getTo() != null && !server.getTo().isEmpty()) {
          String beanName = "sftpUploadFlowConfig-" + serverName;
          context.registerBean(beanName, SftpUploadFlowConfig.class,
              () -> new SftpUploadFlowConfig(
                  context, properties, processor, factoryProvider, errorHandlingAdvice, transactionManager, flowContext, server));
        }
        // Register archive flow if "archive" property is set.
        if (server.getArchive() != null && !server.getArchive().isEmpty()) {
          String beanName = "sftpArchiveFlowConfig-" + serverName;
          context.registerBean(beanName, SftpArchiveFlowConfig.class,
              () -> new SftpArchiveFlowConfig(
                  context, properties, processor, factoryProvider, errorHandlingAdvice, transactionManager, flowContext, server));
        }
      }
    }
  }
}
