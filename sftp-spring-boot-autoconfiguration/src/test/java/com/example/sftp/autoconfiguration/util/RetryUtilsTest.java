package com.example.sftp.autoconfiguration.util;

import com.example.sftp.autoconfiguration.SftpProperties.RetryProperties;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RetryUtilsTest {

  @Test
  void retryCallShouldReturnValueWhenOperationSucceeds() {
    // given
    RetryProperties retryProperties = RetryProperties.builder()
        .maxAttempts(3)
        .initialInterval(100L)
        .multiplier(1.0)
        .maxInterval(200L)
        .build();

    // when
    String result = RetryUtils.retryCall(
        () -> "success",
        retryProperties.getInitialInterval(),
        retryProperties.getMultiplier(),
        retryProperties.getMaxInterval(),
        retryProperties.getMaxAttempts(),
        "testServer",
        "operation"
    );

    // then
    assertThat(result).isEqualTo("success");
  }

  @Test
  void retryCallShouldThrowExceptionWhenOperationFails() {
    // given
    RetryProperties retryProperties = RetryProperties.builder()
        .maxAttempts(2)
        .initialInterval(50L)
        .multiplier(1.0)
        .maxInterval(100L)
        .build();

    // when / then – the callable always throws a RuntimeException
    assertThatThrownBy(() ->
        RetryUtils.retryCall(
            () -> { throw new RuntimeException("fail"); },
            retryProperties.getInitialInterval(),
            retryProperties.getMultiplier(),
            retryProperties.getMaxInterval(),
            retryProperties.getMaxAttempts(),
            "testServer",
            "operation"
        )
    ).isInstanceOf(RuntimeException.class)
        .hasMessageContaining("fail");
  }

  @Test
  void retryCallShouldWrapCheckedExceptionAndThrow() {
    // given a callable that throws a checked exception
    RetryProperties retryProperties = RetryProperties.builder()
        .maxAttempts(2)
        .initialInterval(50L)
        .multiplier(1.0)
        .maxInterval(100L)
        .build();

    // when / then – checked exceptions should be wrapped in a RuntimeException
    assertThatThrownBy(() ->
        RetryUtils.retryCall(
            () -> { throw new Exception("checked error"); },
            retryProperties.getInitialInterval(),
            retryProperties.getMultiplier(),
            retryProperties.getMaxInterval(),
            retryProperties.getMaxAttempts(),
            "testServer",
            "operation"
        )
    ).isInstanceOf(RuntimeException.class)
        .hasMessageContaining("checked error")
        .hasMessageContaining("failed after 2 attempts");
  }

  /**
   * This test uses Awaitility to wait until the background retry thread
   * enters the TIMED_WAITING state (indicating it is sleeping) and then interrupts it.
   */
  @Test
  @Timeout(5)
  void retryCallShouldThrowRuntimeExceptionWhenSleepInterruptedUsingAwaitility() throws InterruptedException {
    // Create a callable that always fails so that retryCall will go to sleep.
    Callable<String> failingCallable = () -> { throw new RuntimeException("fail"); };

    // Use an AtomicReference to capture the exception thrown by retryCall.
    AtomicReference<RuntimeException> thrownException = new AtomicReference<>();

    // Run retryCall in a separate thread.
    Thread retryThread = new Thread(() -> {
      try {
        // Use a long initial interval to ensure the thread goes to sleep.
        RetryUtils.retryCall(
            failingCallable,
            1000L,
            2.0,
            5000L,
            3,
            "testServer",
            "operation"
        );
      } catch (RuntimeException e) {
        thrownException.set(e);
      }
    });
    retryThread.start();

    // Use Awaitility to wait until the thread is sleeping (TIMED_WAITING).
    Awaitility.await()
        .atMost(2, TimeUnit.SECONDS)
        .until(() -> retryThread.getState() == Thread.State.TIMED_WAITING);

    // Interrupt the thread to trigger the InterruptedException.
    retryThread.interrupt();
    retryThread.join();

    // Assert that the exception contains "Retry interrupted"
    assertThat(thrownException.get())
        .isNotNull()
        .hasMessageContaining("Retry interrupted");
  }
}
