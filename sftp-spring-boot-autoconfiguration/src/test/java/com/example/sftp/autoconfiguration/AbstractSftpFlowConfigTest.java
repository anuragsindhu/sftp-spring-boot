package com.example.sftp.autoconfiguration;

import org.apache.sshd.sftp.client.SftpClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.file.filters.CompositeFileListFilter;
import org.springframework.integration.file.filters.RegexPatternFileListFilter;
import org.springframework.integration.handler.advice.ExpressionEvaluatingRequestHandlerAdvice;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DummySftpFileProcessor implements SftpFileProcessor {
}

class DummySftpFlowConfig extends AbstractSftpFlowConfig {
  public DummySftpFlowConfig(ApplicationContext applicationContext, SftpProperties sftpProperties, SftpFileProcessor globalFileProcessor, IntegrationFlowContext flowContext) {
    super(applicationContext, sftpProperties, globalFileProcessor, flowContext);
  }
}

public class AbstractSftpFlowConfigTest {

  @Test
  void shouldDetermineLocalDirectoryUsingOverride() {
    // given
    String serverLocalOverride = "/tmp/customDir";
    String globalLocalDir = "/tmp/globalDir";
    String suffix = "download";
    String serverName = "server1";
    ApplicationContext context = Mockito.mock(ApplicationContext.class);
    SftpProperties props = SftpProperties.builder().build();
    SftpFileProcessor processor = Mockito.mock(SftpFileProcessor.class);
    IntegrationFlowContext flowContext = Mockito.mock(IntegrationFlowContext.class);
    AbstractSftpFlowConfig config = new DummySftpFlowConfig(context, props, processor, flowContext);

    // when
    File dir = config.determineLocalDirectory(serverLocalOverride, globalLocalDir, suffix, serverName);

    // then
    assertThat(dir.getAbsolutePath()).isEqualTo(new File("/tmp/customDir").getAbsolutePath());
    // cleanup
    dir.delete();
  }

  @Test
  void shouldDetermineLocalDirectoryUsingGlobalDirectory() {
    // given
    String serverLocalOverride = "";
    String globalLocalDir = "/tmp/globalDir";
    String suffix = "upload";
    String serverName = "server2";
    ApplicationContext context = Mockito.mock(ApplicationContext.class);
    SftpProperties props = SftpProperties.builder().localDownloadDir(globalLocalDir).build();
    SftpFileProcessor processor = Mockito.mock(SftpFileProcessor.class);
    IntegrationFlowContext flowContext = Mockito.mock(IntegrationFlowContext.class);
    AbstractSftpFlowConfig config = new DummySftpFlowConfig(context, props, processor, flowContext);

    // when
    File dir = config.determineLocalDirectory(serverLocalOverride, globalLocalDir, suffix, serverName);

    // then
    String expected = new File("/tmp/globalDir/upload/server2").getAbsolutePath();
    assertThat(dir.getAbsolutePath()).isEqualTo(expected);
    // cleanup
    dir.delete();
  }

  @Test
  void shouldExecuteInTransaction() {
    // given
    ApplicationContext context = Mockito.mock(ApplicationContext.class);
    SftpProperties props = SftpProperties.builder().build();
    SftpFileProcessor processor = Mockito.mock(SftpFileProcessor.class);
    IntegrationFlowContext flowContext = Mockito.mock(IntegrationFlowContext.class);
    PlatformTransactionManager txManager = Mockito.mock(PlatformTransactionManager.class);
    AbstractSftpFlowConfig config = new DummySftpFlowConfig(context, props, processor, flowContext);

    // when
    String result = config.executeInTransaction("test", String::toUpperCase, txManager);

    // then
    assertThat(result).isEqualTo("TEST");
  }

  @Test
  void shouldBuildPollerMetadataForFixedPoller() {
    SftpProperties.PollerProperties poller = new SftpProperties.PollerProperties();
    poller.setType("fixed");
    poller.setFixedInterval(1000L);
    poller.setFallbackFixedDelay(2000L); // ignored when fixedInterval is set
    ExpressionEvaluatingRequestHandlerAdvice advice = new ExpressionEvaluatingRequestHandlerAdvice();

    ApplicationContext context = Mockito.mock(ApplicationContext.class);
    SftpProperties props = SftpProperties.builder().build();
    SftpFileProcessor processor = Mockito.mock(SftpFileProcessor.class);
    IntegrationFlowContext flowContext = Mockito.mock(IntegrationFlowContext.class);
    DummySftpFlowConfig config = new DummySftpFlowConfig(context, props, processor, flowContext);

    PollerMetadata metadata = config.buildPollerMetadata(poller, advice);
    assertThat(metadata).isNotNull();
  }

  @Test
  void shouldBuildPollerMetadataForTimeWindowPoller() {
    SftpProperties.PollerProperties poller = new SftpProperties.PollerProperties();
    poller.setType("timeWindow");
    poller.setWindowInterval(5000L);
    poller.setStartTime("08:00");
    poller.setEndTime("17:00");
    poller.setTimeZone("UTC");
    poller.setFallbackFixedDelay(3000L);
    ExpressionEvaluatingRequestHandlerAdvice advice = new ExpressionEvaluatingRequestHandlerAdvice();

    ApplicationContext context = Mockito.mock(ApplicationContext.class);
    SftpProperties props = SftpProperties.builder().build();
    SftpFileProcessor processor = Mockito.mock(SftpFileProcessor.class);
    IntegrationFlowContext flowContext = Mockito.mock(IntegrationFlowContext.class);
    DummySftpFlowConfig config = new DummySftpFlowConfig(context, props, processor, flowContext);

    PollerMetadata metadata = config.buildPollerMetadata(poller, advice);
    assertThat(metadata).isNotNull();
  }

  @Test
  void shouldBuildPollerMetadataFallbackWhenPollerIsInvalid() {
    // given a poller configuration that doesn't satisfy fixed or timeWindow conditions.
    SftpProperties.PollerProperties poller = new SftpProperties.PollerProperties();
    ExpressionEvaluatingRequestHandlerAdvice advice = new ExpressionEvaluatingRequestHandlerAdvice();

    ApplicationContext context = Mockito.mock(ApplicationContext.class);
    SftpProperties props = SftpProperties.builder().build();
    SftpFileProcessor processor = Mockito.mock(SftpFileProcessor.class);
    IntegrationFlowContext flowContext = Mockito.mock(IntegrationFlowContext.class);
    DummySftpFlowConfig config = new DummySftpFlowConfig(context, props, processor, flowContext);

    PollerMetadata metadata = config.buildPollerMetadata(poller, advice);
    assertThat(metadata).isNotNull();
  }

  @Test
  void shouldCreateAndApplyRemoteCompositeFilter() {
    SftpProperties.SftpServerConfig serverConfig = new SftpProperties.SftpServerConfig();
    serverConfig.setEnableMetadataStore(true);
    serverConfig.setFilePattern(".*\\.csv");
    serverConfig.setMinFileSize(100L);
    serverConfig.setMaxFileSize(1000L);
    serverConfig.setName("remoteServer");

    ApplicationContext context = Mockito.mock(ApplicationContext.class);
    // Return a dummy metadata store when requested.
    org.springframework.integration.metadata.ConcurrentMetadataStore metadataStore =
        Mockito.mock(org.springframework.integration.metadata.ConcurrentMetadataStore.class);
    Mockito.when(context.getBean(org.springframework.integration.metadata.ConcurrentMetadataStore.class))
        .thenReturn(metadataStore);

    SftpProperties props = SftpProperties.builder().build();
    SftpFileProcessor processor = Mockito.mock(SftpFileProcessor.class);
    IntegrationFlowContext flowContext = Mockito.mock(IntegrationFlowContext.class);
    DummySftpFlowConfig config = new DummySftpFlowConfig(context, props, processor, flowContext);

    CompositeFileListFilter<SftpClient.DirEntry> remoteFilter = config.createRemoteCompositeFilter(serverConfig);
    assertThat(remoteFilter).isNotNull();
    SftpClient.DirEntry entryMatching = Mockito.mock(SftpClient.DirEntry.class);
    SftpClient.Attributes attrsMatching = Mockito.mock(SftpClient.Attributes.class);
    Mockito.when(attrsMatching.getSize()).thenReturn(500L);
    Mockito.when(attrsMatching.getModifyTime()).thenReturn(FileTime.fromMillis(System.currentTimeMillis()));
    Mockito.when(entryMatching.getFilename()).thenReturn("data.csv");
    Mockito.when(entryMatching.getAttributes()).thenReturn(attrsMatching);
    // Expect a match.
    assertThat(remoteFilter.accept(entryMatching)).isTrue();

    SftpClient.DirEntry entryWrongName = Mockito.mock(SftpClient.DirEntry.class);
    SftpClient.Attributes attrsWrongName = Mockito.mock(SftpClient.Attributes.class);
    Mockito.when(attrsWrongName.getModifyTime()).thenReturn(FileTime.fromMillis(System.currentTimeMillis()));
    Mockito.when(attrsWrongName.getSize()).thenReturn(500L);
    Mockito.when(entryWrongName.getFilename()).thenReturn("data.txt");
    Mockito.when(entryWrongName.getAttributes()).thenReturn(attrsWrongName);
    assertThat(remoteFilter.accept(entryWrongName)).isFalse();

    SftpClient.DirEntry entryTooSmall = Mockito.mock(SftpClient.DirEntry.class);
    SftpClient.Attributes attrsTooSmall = Mockito.mock(SftpClient.Attributes.class);
    Mockito.when(attrsTooSmall.getSize()).thenReturn(50L);
    Mockito.when(attrsTooSmall.getModifyTime()).thenReturn(FileTime.fromMillis(System.currentTimeMillis()));
    Mockito.when(entryTooSmall.getFilename()).thenReturn("data.csv");
    Mockito.when(entryTooSmall.getAttributes()).thenReturn(attrsTooSmall);
    assertThat(remoteFilter.accept(entryTooSmall)).isFalse();
  }

  @Test
  void shouldReturnNullRemoteCompositeFilterWhenNoFilterApplicable() {
    SftpProperties.SftpServerConfig serverConfig = new SftpProperties.SftpServerConfig();
    serverConfig.setEnableMetadataStore(null);
    serverConfig.setFilePattern("");
    serverConfig.setMinFileSize(null);
    serverConfig.setMaxFileSize(null);

    ApplicationContext context = Mockito.mock(ApplicationContext.class);
    SftpProperties props = SftpProperties.builder().build();
    SftpFileProcessor processor = Mockito.mock(SftpFileProcessor.class);
    IntegrationFlowContext flowContext = Mockito.mock(IntegrationFlowContext.class);
    DummySftpFlowConfig config = new DummySftpFlowConfig(context, props, processor, flowContext);

    CompositeFileListFilter<SftpClient.DirEntry> remoteFilter = config.createRemoteCompositeFilter(serverConfig);
    assertThat(remoteFilter).isNull();
  }

  @Test
  void shouldCreateLocalCompositeFilterWhenFiltersAreAdded() {
    SftpProperties.SftpServerConfig serverConfig = new SftpProperties.SftpServerConfig();
    serverConfig.setEnableMetadataStore(true);
    serverConfig.setFilePattern(".*\\.txt");
    serverConfig.setMinFileSize(50L);
    serverConfig.setMaxFileSize(500L);

    ApplicationContext context = Mockito.mock(ApplicationContext.class);
    SftpProperties props = SftpProperties.builder().build();
    SftpFileProcessor processor = Mockito.mock(SftpFileProcessor.class);
    IntegrationFlowContext flowContext = Mockito.mock(IntegrationFlowContext.class);
    DummySftpFlowConfig config = new DummySftpFlowConfig(context, props, processor, flowContext);

    CompositeFileListFilter<File> localFilter = config.createLocalCompositeFilter(serverConfig);
    assertThat(localFilter).isNotNull();
    // Create a dummy File that should be accepted.
    File matchingFile = Mockito.mock(File.class);
    Mockito.when(matchingFile.getName()).thenReturn("sample.txt");
    Mockito.when(matchingFile.length()).thenReturn(100L);
    assertThat(localFilter.accept(matchingFile)).isTrue();

    // Create a file with non-matching name.
    File wrongName = Mockito.mock(File.class);
    Mockito.when(wrongName.getName()).thenReturn("sample.doc");
    Mockito.when(wrongName.length()).thenReturn(100L);
    assertThat(localFilter.accept(wrongName)).isFalse();

    // Create a file with size too small.
    File tooSmall = Mockito.mock(File.class);
    Mockito.when(tooSmall.getName()).thenReturn("sample.txt");
    Mockito.when(tooSmall.length()).thenReturn(10L);
    assertThat(localFilter.accept(tooSmall)).isFalse();
  }

  @Test
  void shouldReturnNullLocalCompositeFilterWhenNoFilterApplicable() {
    SftpProperties.SftpServerConfig serverConfig = new SftpProperties.SftpServerConfig();
    serverConfig.setEnableMetadataStore(null);
    serverConfig.setFilePattern(null);
    serverConfig.setMinFileSize(null);
    serverConfig.setMaxFileSize(null);

    ApplicationContext context = Mockito.mock(ApplicationContext.class);
    SftpProperties props = SftpProperties.builder().build();
    SftpFileProcessor processor = Mockito.mock(SftpFileProcessor.class);
    IntegrationFlowContext flowContext = Mockito.mock(IntegrationFlowContext.class);
    DummySftpFlowConfig config = new DummySftpFlowConfig(context, props, processor, flowContext);

    CompositeFileListFilter<File> filter = config.createLocalCompositeFilter(serverConfig);
    assertThat(filter).isNull();
  }

  @Test
  void shouldAcceptFileWithinSizeLimitsInLocalFileSizeFilter() {
    AbstractSftpFlowConfig.LocalFileSizeFilter filter = new AbstractSftpFlowConfig.LocalFileSizeFilter(100L, 200L);
    File dummyFile = Mockito.mock(File.class);
    Mockito.when(dummyFile.length()).thenReturn(150L);
    assertThat(filter.accept(dummyFile)).isTrue();
  }

  @Test
  void shouldRejectFileTooSmallOrTooLargeInLocalFileSizeFilter() {
    AbstractSftpFlowConfig.LocalFileSizeFilter filter = new AbstractSftpFlowConfig.LocalFileSizeFilter(100L, 200L);
    File dummySmall = Mockito.mock(File.class);
    Mockito.when(dummySmall.length()).thenReturn(50L);
    File dummyLarge = Mockito.mock(File.class);
    Mockito.when(dummyLarge.length()).thenReturn(250L);
    assertThat(filter.accept(dummySmall)).isFalse();
    assertThat(filter.accept(dummyLarge)).isFalse();
  }

  @Test
  void shouldObtainCustomProcessorWhenDefined() {
    SftpProperties.SftpServerConfig serverConfig = new SftpProperties.SftpServerConfig();
    serverConfig.setProcessorClass(DummySftpFileProcessor.class);

    ApplicationContext context = Mockito.mock(ApplicationContext.class);
    DummySftpFileProcessor customProcessor = new DummySftpFileProcessor();
    Mockito.when(context.getBean(DummySftpFileProcessor.class)).thenReturn(customProcessor);

    SftpProperties props = SftpProperties.builder().build();
    SftpFileProcessor globalProcessor = Mockito.mock(SftpFileProcessor.class);
    IntegrationFlowContext flowContext = Mockito.mock(IntegrationFlowContext.class);
    DummySftpFlowConfig config = new DummySftpFlowConfig(context, props, globalProcessor, flowContext);

    SftpFileProcessor obtained = config.obtainProcessor(serverConfig);
    assertThat(obtained).isInstanceOf(DummySftpFileProcessor.class);
  }

  @Test
  void shouldFallbackToGlobalProcessorWhenCustomNotFound() {
    SftpProperties.SftpServerConfig serverConfig = new SftpProperties.SftpServerConfig();
    serverConfig.setProcessorClass(DummySftpFileProcessor.class);

    ApplicationContext context = Mockito.mock(ApplicationContext.class);
    Mockito.when(context.getBean(DummySftpFileProcessor.class)).thenThrow(new RuntimeException("Bean not found"));

    SftpProperties props = SftpProperties.builder().build();
    SftpFileProcessor globalProcessor = Mockito.mock(SftpFileProcessor.class);
    IntegrationFlowContext flowContext = Mockito.mock(IntegrationFlowContext.class);
    DummySftpFlowConfig config = new DummySftpFlowConfig(context, props, globalProcessor, flowContext);

    SftpFileProcessor obtained = config.obtainProcessor(serverConfig);
    assertThat(obtained).isEqualTo(globalProcessor);
  }
}
