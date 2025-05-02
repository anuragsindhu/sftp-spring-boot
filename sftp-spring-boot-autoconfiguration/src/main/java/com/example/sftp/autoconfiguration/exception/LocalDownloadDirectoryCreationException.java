package com.example.sftp.autoconfiguration.exception;

/**
 * Exception thrown when the local download directory cannot be created.
 */
public class LocalDownloadDirectoryCreationException extends RuntimeException {

  /**
   * Constructs a new LocalDownloadDirectoryCreationException with the specified detail message.
   *
   * @param message the detail message.
   */
  public LocalDownloadDirectoryCreationException(String message) {
    super(message);
  }

  /**
   * Constructs a new LocalDownloadDirectoryCreationException with the specified detail message and cause.
   *
   * @param message the detail message.
   * @param cause   the cause.
   */
  public LocalDownloadDirectoryCreationException(String message, Throwable cause) {
    super(message, cause);
  }
}
