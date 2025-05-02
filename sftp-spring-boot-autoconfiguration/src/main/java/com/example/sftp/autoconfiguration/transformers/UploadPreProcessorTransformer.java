package com.example.sftp.autoconfiguration.transformers;

import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties.RetryProperties;
import com.example.sftp.autoconfiguration.util.RetryUtils;
import org.springframework.integration.core.GenericTransformer;

import java.io.File;

/**
 * Transformer that wraps a call to processor.beforeUpload in retry logic.
 */
public class UploadPreProcessorTransformer implements GenericTransformer<File, File> {

  private final SftpFileProcessor processor;
  private final String serverName;
  private final RetryProperties retryProps;

  public UploadPreProcessorTransformer(SftpFileProcessor processor, String serverName, RetryProperties retryProps) {
    this.processor = processor;
    this.serverName = serverName;
    this.retryProps = retryProps;
  }

  @Override
  public File transform(File file) {
    return RetryUtils.retryCall(() -> {
      processor.beforeUpload(file, serverName);
      return file;
    }, retryProps.getInitialInterval(), retryProps.getMultiplier(), retryProps.getMaxInterval(), retryProps.getMaxAttempts(), serverName, "beforeUpload");
  }
}
