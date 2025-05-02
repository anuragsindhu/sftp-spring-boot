package com.example.sftp.autoconfiguration.transformers;

import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties.RetryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class UploadPreProcessorTransformerTest {

  private SftpFileProcessor processor;
  private RetryProperties retryProperties;
  private UploadPreProcessorTransformer transformer;

  @BeforeEach
  void setUp() {
    processor = mock(SftpFileProcessor.class);
    retryProperties = RetryProperties.builder()
        .maxAttempts(3).initialInterval(1000L).multiplier(2.0).maxInterval(4000L).build();
    transformer = new UploadPreProcessorTransformer(processor, "testServer", retryProperties);
  }

  @Test
  void shouldTransformUploadFileSuccessfully() {
    // given
    File file = new File("uploadFile.txt");
    doNothing().when(processor).beforeUpload(file, "testServer");
    // when
    File result = transformer.transform(file);
    // then
    assertThat(result).isEqualTo(file);
    verify(processor, times(1)).beforeUpload(file, "testServer");
  }
}