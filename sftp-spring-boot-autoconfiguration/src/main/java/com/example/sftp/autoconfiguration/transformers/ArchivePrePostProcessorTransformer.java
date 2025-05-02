package com.example.sftp.autoconfiguration.transformers;

import com.example.sftp.autoconfiguration.SftpFileProcessor;
import com.example.sftp.autoconfiguration.SftpProperties.RetryProperties;
import com.example.sftp.autoconfiguration.util.RetryUtils;
import org.springframework.integration.core.GenericTransformer;

/**
 * Transformer that wraps a call to processor.beforeArchive or processor.afterArchive (based on the flag)
 * in retry logic.
 */
public class ArchivePrePostProcessorTransformer implements GenericTransformer<String, String> {

  private final SftpFileProcessor processor;
  private final String serverName;
  private final boolean isPre;
  private final RetryProperties retryProps;

  public ArchivePrePostProcessorTransformer(SftpFileProcessor processor, String serverName, boolean isPre, RetryProperties retryProps) {
    this.processor = processor;
    this.serverName = serverName;
    this.isPre = isPre;
    this.retryProps = retryProps;
  }

  @Override
  public String transform(String remotePath) {
    String operation = isPre ? "beforeArchive" : "afterArchive";
    return RetryUtils.retryCall(() -> {
      if (isPre) {
        processor.beforeArchive(remotePath, serverName);
      } else {
        processor.afterArchive(remotePath, serverName);
      }
      return remotePath;
    }, retryProps.getInitialInterval(), retryProps.getMultiplier(), retryProps.getMaxInterval(), retryProps.getMaxAttempts(), serverName, operation);
  }
}
