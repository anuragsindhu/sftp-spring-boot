package com.example.sftp.autoconfiguration.transformers;

import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties.RetryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class ArchivePrePostProcessorTransformerTest {

  private SftpFileProcessor processor;
  private RetryProperties retryProperties;
  private ArchivePrePostProcessorTransformer preTransformer;
  private ArchivePrePostProcessorTransformer postTransformer;

  @BeforeEach
  void setUp() {
    processor = mock(SftpFileProcessor.class);
    retryProperties = RetryProperties.builder()
        .maxAttempts(3).initialInterval(1000L).multiplier(2.0).maxInterval(4000L).build();
    preTransformer = new ArchivePrePostProcessorTransformer(processor, "testServer", true, retryProperties);
    postTransformer = new ArchivePrePostProcessorTransformer(processor, "testServer", false, retryProperties);
  }

  @Test
  void shouldPerformBeforeArchiveProcessing() {
    // given
    String remotePath = "fileToArchive.txt";
    doNothing().when(processor).beforeArchive(remotePath, "testServer");
    // when
    String result = preTransformer.transform(remotePath);
    // then
    assertThat(result).isEqualTo(remotePath);
    verify(processor, times(1)).beforeArchive(remotePath, "testServer");
  }

  @Test
  void shouldPerformAfterArchiveProcessing() {
    // given
    String remotePath = "fileArchived.txt";
    doNothing().when(processor).afterArchive(remotePath, "testServer");
    // when
    String result = postTransformer.transform(remotePath);
    // then
    assertThat(result).isEqualTo(remotePath);
    verify(processor, times(1)).afterArchive(remotePath, "testServer");
  }
}
