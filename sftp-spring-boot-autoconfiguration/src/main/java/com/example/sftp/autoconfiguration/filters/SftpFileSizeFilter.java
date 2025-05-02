package com.example.sftp.autoconfiguration.filters;

import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.integration.file.filters.AbstractFileListFilter;

/**
 * A file list filter that accepts files based on their size.
 */
public class SftpFileSizeFilter extends AbstractFileListFilter<SftpClient.DirEntry> {

  private final Long minSize;
  private final Long maxSize;

  public SftpFileSizeFilter(Long minSize, Long maxSize) {
    this.minSize = minSize;
    this.maxSize = maxSize;
  }

  @Override
  public boolean accept(SftpClient.DirEntry file) {
    if (file == null) {
      return false;
    }
    long size = file.getAttributes().getSize();
    if (minSize != null && size < minSize) {
      return false;
    }
    if (maxSize != null && size > maxSize) {
      return false;
    }
    return true;
  }
}
