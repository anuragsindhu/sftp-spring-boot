package com.example.sftp.autoconfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.integration.dsl.context.IntegrationFlowContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class SftpAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SftpAutoConfiguration.class))
      .withBean(IntegrationFlowContext.class, () -> mock(IntegrationFlowContext.class));

  @Test
  void testSftpPropertiesAreCreated() {
    // Provide minimal properties so that SftpProperties can be bound.
    contextRunner.withPropertyValues(
        "sftp.localDownloadDir=/tmp/downloads",
        "sftp.defaultPoller.type=fixed",
        "sftp.defaultPoller.fixedInterval=1000",
        "sftp.defaultRetry.maxAttempts=3",
        "sftp.defaultRetry.initialInterval=1500",
        "sftp.defaultRetry.multiplier=2.0",
        "sftp.defaultRetry.maxInterval=5000",
        "sftp.throughput.corePoolSize=10",
        "sftp.throughput.maxPoolSize=20",
        "sftp.throughput.queueCapacity=100",
        "sftp.throughput.threadNamePrefix=SftpInbound-"
    ).run(context -> {
      // Verify that an SftpProperties bean is created with expected values.
      assertThat(context).hasSingleBean(SftpProperties.class);
      SftpProperties properties = context.getBean(SftpProperties.class);
      assertThat(properties.getLocalDownloadDir()).isEqualTo("/tmp/downloads");
      assertThat(properties.getDefaultPoller().getType()).isEqualTo("fixed");
      assertThat(properties.getDefaultRetry().getMaxAttempts()).isEqualTo(3);
      assertThat(properties.getThroughput().getCorePoolSize()).isEqualTo(10);
    });
  }

  @Test
  void testSftpSessionFactoryProviderIsCreatedWhenServerConfigIsPresent() {
    // Provide properties including the server configuration.
    contextRunner.withPropertyValues(
        "sftp.localDownloadDir=/tmp/downloads",
        "sftp.defaultPoller.type=fixed",
        "sftp.defaultPoller.fixedInterval=1000",
        "sftp.defaultRetry.maxAttempts=3",
        "sftp.defaultRetry.initialInterval=1500",
        "sftp.defaultRetry.multiplier=2.0",
        "sftp.defaultRetry.maxInterval=5000",
        "sftp.throughput.corePoolSize=10",
        "sftp.throughput.maxPoolSize=20",
        "sftp.throughput.queueCapacity=100",
        "sftp.throughput.threadNamePrefix=SftpInbound-",
        // Define a server configuration for "server1"
        "sftp.servers[0].name=server1",
        "sftp.servers[0].host=localhost",
        "sftp.servers[0].username=user",
        "sftp.servers[0].password=password",
        "sftp.servers[0].port=22",
        "sftp.servers[0].from=remote/download1",
        "sftp.servers[0].archive=remote/archive1"
    ).run(context -> {
      // Verify that the SftpSessionFactoryProvider bean is defined.
      assertThat(context).hasSingleBean(SftpSessionFactoryProvider.class);
      SftpSessionFactoryProvider provider = context.getBean(SftpSessionFactoryProvider.class);
      // Verify that a session factory for "server1" can be obtained.
      assertThat(provider.getFactory("server1")).isNotNull();
    });
  }

  @Test
  void testAutoConfigurationActivatesEvenWhenRequiredPropertiesAreMissing() {
    // In this test, we intentionally do not provide any SFTP properties
    // (i.e. required properties such as sftp.localDownloadDir are missing).
    // In our auto-configuration, a bean of type SftpProperties is still created (with null or default values).
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SftpAutoConfiguration.class))
        .withBean(IntegrationFlowContext.class, () -> mock(IntegrationFlowContext.class))
        .run(context -> {
          // We expect that default SftpProperties is present even when some required properties are missing.
          assertThat(context).hasBean("sftp-com.example.sftp.autoconfiguration.SftpProperties");
        });
  }
}
