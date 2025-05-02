package com.example.sftp.autoconfiguration;

import java.io.File;

/**
 * Interface for processing SFTP files.
 */
public interface SftpFileProcessor {

  /**
   * Called after a file is downloaded from SFTP.
   *
   * @param downloadedFile the downloaded file
   * @param serverName     the SFTP server name
   */
  default void afterDownload(File downloadedFile, String serverName) {}

  /**
   * Called before a file is uploaded to SFTP.
   *
   * @param file       the file to upload
   * @param serverName the SFTP server name
   */
  default void beforeUpload(File file, String serverName) {}

  /**
   * Called before archiving a file on the remote SFTP server.
   *
   * @param remotePath the remote file path
   * @param serverName the SFTP server name
   */
  default void beforeArchive(String remotePath, String serverName) {}

  /**
   * Called after archiving a file on the remote SFTP server.
   *
   * @param remotePath the remote file path
   * @param serverName the SFTP server name
   */
  default void afterArchive(String remotePath, String serverName) {}
}
