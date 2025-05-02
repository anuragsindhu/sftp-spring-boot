package com.example.sftp.autoconfiguration.transformers;

import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties.RetryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class DownloadPostProcessorTransformerTest {

  private SftpFileProcessor processor;
  private RetryProperties retryProperties;
  private DownloadPostProcessorTransformer transformer;

  @BeforeEach
  void setUp() {
    processor = mock(SftpFileProcessor.class);
    retryProperties = RetryProperties.builder()
        .maxAttempts(3).initialInterval(1000L).multiplier(2.0).maxInterval(4000L).build();
    transformer = new DownloadPostProcessorTransformer(processor, "testServer", retryProperties);
  }

  @Test
  void shouldTransformFileSuccessfully() {
    // given
    File file = new File("dummyFile.txt");
    doNothing().when(processor).afterDownload(file, "testServer");
    // when
    File result = transformer.transform(file);
    // then
    assertThat(result).isEqualTo(file);
    verify(processor, times(1)).afterDownload(file, "testServer");
  }
}
