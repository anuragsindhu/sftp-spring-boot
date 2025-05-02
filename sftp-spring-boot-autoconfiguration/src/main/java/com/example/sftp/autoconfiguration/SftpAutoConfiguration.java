package com.example.sftp.autoconfiguration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.metadata.MetadataStore;
import org.springframework.integration.metadata.SimpleMetadataStore;
import org.springframework.integration.transaction.PseudoTransactionManager;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration
@ConditionalOnClass({ SftpSessionFactoryProvider.class})
@EnableConfigurationProperties(SftpProperties.class)
@Slf4j
public class SftpAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public SftpSessionFactoryProvider sftpSessionFactoryProvider(SftpProperties properties) {
    return new SftpSessionFactoryProvider(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public SftpFileProcessor sftpFileProcessor() {
    return new SftpFileProcessor() { };
  }

  @Bean
  public SftpFlowsAutoConfigurer sftpFlowsAutoConfigurer(SftpProperties properties,
                                                         SftpFileProcessor processor,
                                                         SftpSessionFactoryProvider factoryProvider,
                                                         ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice,
                                                         PlatformTransactionManager transactionManager,
                                                         GenericApplicationContext context,
                                                         IntegrationFlowContext flowContext) {
    return new SftpFlowsAutoConfigurer(properties, processor, factoryProvider, errorHandlingAdvice, transactionManager, context, flowContext);
  }

  /**
   * Defines a global error channel where SFTP flow errors will be published.
   *
   * @return a DirectChannel for errors.
   */
  @Bean
  @ConditionalOnMissingBean(name = "globalErrorChannel")
  public MessageChannel globalErrorChannel() {
    return new DirectChannel();
  }

  /**
   * Creates an error handling advice bean for SFTP flows.
   * This advice traps exceptions, evaluates a literal expression (here, simply returning the payload),
   * and routes error details to the global error channel.
   *
   * @return the ExpressionEvaluatingRequestHandlerAdvice instance.
   */
  @Bean
  @ConditionalOnMissingBean(ExpressionEvaluatingRequestHandlerAdvice.class)
  public ExpressionEvaluatingRequestHandlerAdvice sftpErrorHandlingAdvice() {
    ExpressionEvaluatingRequestHandlerAdvice advice = new ExpressionEvaluatingRequestHandlerAdvice();
    advice.setOnFailureExpression(new LiteralExpression("payload"));
    advice.setTrapException(true);
    advice.setFailureChannelName("globalErrorChannel");
    log.info("SFTP error handling advice configured and bound to 'globalErrorChannel'.");
    return advice;
  }

  /**
   * Defines a message handler for error messages coming through the global error channel.
   * This handler simply logs the error details.
   *
   * @return the MessageHandler for global SFTP errors.
   */
  @Bean
  @ConditionalOnMissingBean(name = "sftpErrorMessageHandler")
  public MessageHandler sftpErrorMessageHandler() {
    return message -> log.error("SFTP global error received: {}", message);
  }

  /**
   * Defines a global error flow that subscribes to the global error channel.
   * All error messages are handled by the sftpErrorMessageHandler.
   *
   * @param globalErrorChannel the globally defined error channel.
   * @return an IntegrationFlow capturing SFTP errors.
   */
  @Bean
  @ConditionalOnMissingBean(name = "sftpGlobalErrorFlow")
  public IntegrationFlow sftpGlobalErrorFlow(MessageChannel globalErrorChannel) {
    return IntegrationFlow.from(globalErrorChannel)
        .handle(sftpErrorMessageHandler())
        .get();
  }

  @Bean
  @ConditionalOnMissingBean(MetadataStore.class)
  public MetadataStore metadataStore() {
    return new SimpleMetadataStore();
  }

  /**
   * Declares a transaction manager for use in file processing.
   * If no client-provided transaction manager is found, falls back to a PseudoTransactionManager.
   */
  @Bean
  @ConditionalOnMissingBean(PlatformTransactionManager.class)
  public PlatformTransactionManager transactionManager() {
    log.info("No transaction manager bean found; using default PseudoTransactionManager.");
    return new PseudoTransactionManager();
  }
}
