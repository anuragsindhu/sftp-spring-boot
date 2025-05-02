package com.example.sftp.autoconfiguration;

import com.example.sftp.autoconfiguration.inbound.SftpDownloadFlowConfig;
import com.example.sftp.autoconfiguration.outbound.SftpArchiveFlowConfig;
import com.example.sftp.autoconfiguration.outbound.SftpUploadFlowConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SftpFlowsAutoConfigurerTest {

  private GenericApplicationContext context;
  private IntegrationFlowContext flowContext;
  private SftpFileProcessor processor;
  private SftpSessionFactoryProvider factoryProvider;
  private ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice;
  private PlatformTransactionManager transactionManager;
  private SftpProperties properties;

  @BeforeEach
  void setUp() {
    // Initialize mocks.
    MockitoAnnotations.openMocks(this);
    flowContext = mock(IntegrationFlowContext.class);
    processor = mock(SftpFileProcessor.class);
    factoryProvider = mock(SftpSessionFactoryProvider.class);
    errorHandlingAdvice = mock(ExpressionEvaluatingRequestHandlerAdvice.class);
    transactionManager = mock(PlatformTransactionManager.class);

    // Stub the flowContext to return a non-null dummy builder such that chaining of .id(String) works.
    IntegrationFlowContext.IntegrationFlowRegistrationBuilder dummyBuilder = mock(IntegrationFlowContext.IntegrationFlowRegistrationBuilder.class);
    when(flowContext.registration(any(IntegrationFlow.class))).thenReturn(dummyBuilder);
    when(dummyBuilder.id(anyString())).thenReturn(dummyBuilder);
    when(dummyBuilder.register()).thenReturn(mock(IntegrationFlowContext.IntegrationFlowRegistration.class));

    // Use a real, empty GenericApplicationContext so that bean registration is visible.
    context = new GenericApplicationContext();
    context.refresh();
  }

  @Test
  void testRegisterFlowsForAllFlowTypes() {
    // Build properties with a single server configuration having all flow directions.
    SftpProperties.SftpServerConfig serverConfig = SftpProperties.SftpServerConfig.builder()
        .name("testServer")
        .host("localhost")
        .port(22)
        .username("user")
        .password("pass")
        .from("remote/download")
        .to("remote/upload")
        .archive("remote/archive")
        .build();

    properties = SftpProperties.builder()
        .localDownloadDir("/tmp/downloads")
        .defaultPoller(SftpProperties.PollerProperties.builder().type("fixed").fixedInterval(1000L).build())
        .defaultRetry(SftpProperties.RetryProperties.builder()
            .maxAttempts(3)
            .initialInterval(1500L)
            .multiplier(2.0)
            .maxInterval(5000L)
            .build())
        .throughput(SftpProperties.Throughput.builder()
            .corePoolSize(10)
            .maxPoolSize(20)
            .queueCapacity(100)
            .threadNamePrefix("SftpInbound-")
            .build())
        .servers(Collections.singletonList(serverConfig))
        .build();

    // Stub the factory provider to return a dummy session factory for "testServer".
    DefaultSftpSessionFactory dummyFactory = mock(DefaultSftpSessionFactory.class);
    when(factoryProvider.getFactory("testServer")).thenReturn(dummyFactory);

    // Instantiate the auto-configurer; its constructor calls registerFlows().
    new SftpFlowsAutoConfigurer(properties,
        processor,
        factoryProvider,
        errorHandlingAdvice,
        transactionManager,
        context,
        flowContext);

    // The auto-configurer should now have registered three beans:
    // 1. Download flow:
    String downloadBeanName = "sftpDownloadFlowConfig-testServer";
    // 2. Upload flow:
    String uploadBeanName = "sftpUploadFlowConfig-testServer";
    // 3. Archive flow:
    String archiveBeanName = "sftpArchiveFlowConfig-testServer";

    // Verify that the beans are registered in the context and have the expected type.
    Object downloadFlow = context.getBean(downloadBeanName);
    Object uploadFlow = context.getBean(uploadBeanName);
    Object archiveFlow = context.getBean(archiveBeanName);

    assertThat(downloadFlow).isInstanceOf(SftpDownloadFlowConfig.class);
    assertThat(uploadFlow).isInstanceOf(SftpUploadFlowConfig.class);
    assertThat(archiveFlow).isInstanceOf(SftpArchiveFlowConfig.class);
  }

  @Test
  void testRegisterFlowsForPartialConfiguration() {
    // Build properties with a server configuration that has only the "from" property defined.
    SftpProperties.SftpServerConfig serverConfig = SftpProperties.SftpServerConfig.builder()
        .name("partialServer")
        .host("localhost")
        .port(22)
        .username("user")
        .password("pass")
        .from("remote/downloadOnly")
        .build();

    properties = SftpProperties.builder()
        .localDownloadDir("/tmp/downloads")
        .defaultPoller(SftpProperties.PollerProperties.builder().type("fixed").fixedInterval(1000L).build())
        .defaultRetry(SftpProperties.RetryProperties.builder()
            .maxAttempts(3)
            .initialInterval(1500L)
            .multiplier(2.0)
            .maxInterval(5000L)
            .build())
        .throughput(SftpProperties.Throughput.builder()
            .corePoolSize(10)
            .maxPoolSize(20)
            .queueCapacity(100)
            .threadNamePrefix("SftpInbound-")
            .build())
        .servers(Collections.singletonList(serverConfig))
        .build();

    // Stub the factory provider for "partialServer" so that a dummy session factory is returned.
    DefaultSftpSessionFactory dummyFactory = mock(DefaultSftpSessionFactory.class);
    when(factoryProvider.getFactory("partialServer")).thenReturn(dummyFactory);

    // Create the auto-configurer instance.
    new SftpFlowsAutoConfigurer(properties,
        processor,
        factoryProvider,
        errorHandlingAdvice,
        transactionManager,
        context,
        flowContext);

    // For the "partialServer" we expect only the download flow to be registered.
    String downloadBeanName = "sftpDownloadFlowConfig-partialServer";

    assertThat(context.containsBean(downloadBeanName)).isTrue();
    // Verify that no upload or archive flows are registered.
    assertThat(context.containsBean("sftpUploadFlowConfig-partialServer")).isFalse();
    assertThat(context.containsBean("sftpArchiveFlowConfig-partialServer")).isFalse();
  }

  @Test
  void testRegisterFlowsWhenNoServerConfigured() {
    // Build properties with no servers.
    properties = SftpProperties.builder()
        .localDownloadDir("/tmp/downloads")
        .defaultPoller(SftpProperties.PollerProperties.builder().type("fixed").fixedInterval(1000L).build())
        .defaultRetry(SftpProperties.RetryProperties.builder()
            .maxAttempts(3)
            .initialInterval(1500L)
            .multiplier(2.0)
            .maxInterval(5000L)
            .build())
        .throughput(SftpProperties.Throughput.builder()
            .corePoolSize(10)
            .maxPoolSize(20)
            .queueCapacity(100)
            .threadNamePrefix("SftpInbound-")
            .build())
        .build();

    // Create the auto-configurer instance.
    new SftpFlowsAutoConfigurer(properties,
        processor,
        factoryProvider,
        errorHandlingAdvice,
        transactionManager,
        context,
        flowContext);

    // No server configurations exist so no SFTP flow beans should be registered.
    String[] beanNames = context.getBeanDefinitionNames();
    boolean flowBeansExist = false;
    for (String name : beanNames) {
      if (name.startsWith("sftpDownloadFlowConfig-") ||
          name.startsWith("sftpUploadFlowConfig-") ||
          name.startsWith("sftpArchiveFlowConfig-")) {
        flowBeansExist = true;
        break;
      }
    }
    assertThat(flowBeansExist).isFalse();
  }
}
