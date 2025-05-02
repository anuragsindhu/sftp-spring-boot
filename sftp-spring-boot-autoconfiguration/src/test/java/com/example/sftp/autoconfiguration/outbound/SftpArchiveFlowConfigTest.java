package com.example.sftp.autoconfiguration.outbound;

import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties;
import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import com.example.sftp.autoconfiguration.SftpSessionFactoryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class SftpArchiveFlowConfigTest {

  @Mock
  private ApplicationContext applicationContext;
  @Mock
  private SftpProperties sftpProperties;
  @Mock
  private SftpFileProcessor globalFileProcessor;
  @Mock
  private SftpSessionFactoryProvider factoryProvider;
  @Mock
  private ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice;
  @Mock
  private PlatformTransactionManager transactionManager;
  @Mock
  private IntegrationFlowContext flowContext;

  // Using JUnit 5 @TempDir to create a temporary directory for local archive files.
  @TempDir
  File tempDir;

  private SftpProperties.PollerProperties pollerProperties;
  private SftpProperties.Throughput throughput;
  private SftpProperties.RetryProperties retryProperties;
  private SftpServerConfig serverConfig;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    // Set up poller properties; using fixed polling for simplicity.
    pollerProperties = SftpProperties.PollerProperties.builder()
        .type("fixed")
        .fixedInterval(1000L)
        .build();
    when(sftpProperties.getDefaultPoller()).thenReturn(pollerProperties);

    // Set up throughput configuration.
    throughput = SftpProperties.Throughput.builder()
        .corePoolSize(5)
        .maxPoolSize(10)
        .queueCapacity(50)
        .threadNamePrefix("Test-")
        .build();
    when(sftpProperties.getThroughput()).thenReturn(throughput);

    // Set up retry properties.
    retryProperties = SftpProperties.RetryProperties.builder()
        .maxAttempts(3)
        .initialInterval(1500L)
        .multiplier(2.0)
        .maxInterval(5000L)
        .build();
    when(sftpProperties.getDefaultRetry()).thenReturn(retryProperties);

    // Instead of a hardcoded remote path, use a temporary directory located inside tempDir.
    String archiveLocalDir = new File(tempDir, "archiveTest").getAbsolutePath();
    serverConfig = SftpServerConfig.builder()
        .name("archiveTest")
        .archive(archiveLocalDir)   // Use temp folder as archive directory.
        .retry(retryProperties)
        .build();

    DefaultSftpSessionFactory dummyFactory = new DefaultSftpSessionFactory();
    dummyFactory.setHost("dummyHost");
    dummyFactory.setPort(22);
    dummyFactory.setUser("dummyUser");
    dummyFactory.setPassword("dummyPassword");
    when(factoryProvider.getFactory(anyString())).thenReturn(dummyFactory);

    IntegrationFlowContext.IntegrationFlowRegistrationBuilder dummyRegistrationBuilder = mock(IntegrationFlowContext.IntegrationFlowRegistrationBuilder.class);
    when(dummyRegistrationBuilder.id(any(String.class))).thenReturn(dummyRegistrationBuilder);
    when(flowContext.registration(any(IntegrationFlow.class))).thenReturn(dummyRegistrationBuilder);
  }

  @Test
  void shouldRegisterArchiveFlowWhenArchiveIsConfigured() {
    new SftpArchiveFlowConfig(
        applicationContext,
        sftpProperties,
        globalFileProcessor,
        factoryProvider,
        errorHandlingAdvice,
        transactionManager,
        flowContext,
        serverConfig
    );
    verify(flowContext, atLeastOnce()).registration(any());
  }
}
