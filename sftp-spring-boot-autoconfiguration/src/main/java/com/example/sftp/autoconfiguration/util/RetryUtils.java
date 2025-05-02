package com.example.sftp.autoconfiguration.util;

import java.util.concurrent.Callable;

/**
 * Utility class for performing retry logic.
 */
public class RetryUtils {

  /**
   * Executes the given callable with retry logic.
   *
   * @param callable        the operation to execute.
   * @param initialInterval the initial retry interval in milliseconds.
   * @param multiplier      the multiplier for exponential backoff.
   * @param maxInterval     the maximum retry interval in milliseconds.
   * @param maxAttempts     the maximum number of attempts.
   * @param serverName      the server identifier.
   * @param operation       a string describing the operation.
   * @param <T>             the return type.
   * @return the result of the callable if successful.
   * @throws RuntimeException if all retry attempts fail.
   */
  public static <T> T retryCall(Callable<T> callable,
                                long initialInterval,
                                double multiplier,
                                long maxInterval,
                                int maxAttempts,
                                String serverName,
                                String operation) {
    int attempt = 0;
    long interval = initialInterval;
    RuntimeException lastException = null;
    while (attempt < maxAttempts) {
      try {
        return callable.call();
      } catch (RuntimeException ex) {
        lastException = ex;
        attempt++;
        if (attempt < maxAttempts) {
          try {
            Thread.sleep(interval);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", ie);
          }
          interval = Math.min((long) (interval * multiplier), maxInterval);
        }
      } catch (Exception ex) {
        // Wrap checked exception in RuntimeException
        lastException = new RuntimeException(ex);
        attempt++;
        if (attempt < maxAttempts) {
          try {
            Thread.sleep(interval);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", ie);
          }
          interval = Math.min((long) (interval * multiplier), maxInterval);
        }
      }
    }
    // Throw exception after exhausting attempts.
    throw new RuntimeException("[" + serverName + "] Operation " + operation +
        " failed after " + maxAttempts + " attempts. Last exception: " +
        (lastException != null ? lastException.getMessage() : "null"), lastException);
  }
}