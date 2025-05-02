package com.example.sftp.autoconfiguration.inbound;

import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties;
import com.example.sftp.autoconfiguration.SftpProperties.SftpServerConfig;
import com.example.sftp.autoconfiguration.SftpSessionFactoryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.transaction.PlatformTransactionManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SftpDownloadFlowConfigTest {

  @Mock private ApplicationContext applicationContext;
  @Mock private SftpProperties sftpProperties;
  @Mock private SftpFileProcessor fileProcessor;
  @Mock private SftpSessionFactoryProvider factoryProvider;
  @Mock private ExpressionEvaluatingRequestHandlerAdvice errorHandlingAdvice;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private IntegrationFlowContext flowContext;

  private SftpProperties.PollerProperties pollerProperties;
  private SftpProperties.Throughput throughput;
  private SftpServerConfig serverConfig;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    pollerProperties = SftpProperties.PollerProperties.builder()
        .type("fixed")
        .fixedInterval(1000L)
        .build();
    when(sftpProperties.getDefaultPoller()).thenReturn(pollerProperties);
    throughput = SftpProperties.Throughput.builder()
        .corePoolSize(5)
        .maxPoolSize(10)
        .queueCapacity(50)
        .threadNamePrefix("Test-")
        .build();
    when(sftpProperties.getThroughput()).thenReturn(throughput);
    serverConfig = SftpServerConfig.builder()
        .name("testServerDownload")
        .from("/remote/from")
        .localDownloadDir("/tmp/downloadTest")
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
  void shouldRegisterDownloadFlowWhenFromIsConfigured() {
    new SftpDownloadFlowConfig(
        applicationContext, sftpProperties, fileProcessor, factoryProvider, errorHandlingAdvice,
        transactionManager, flowContext, serverConfig);
    verify(flowContext, atLeastOnce()).registration(any());
  }
}
