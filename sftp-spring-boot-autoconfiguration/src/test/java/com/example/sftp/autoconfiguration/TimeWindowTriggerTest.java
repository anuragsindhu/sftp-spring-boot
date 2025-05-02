package com.example.sftp.autoconfiguration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TriggerContext;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

class TimeWindowTriggerTest {

  @Test
  void testNextExecutionWithinWindow() {
    TimeWindowTrigger trigger = new TimeWindowTrigger(1000L, LocalTime.of(0, 0), LocalTime.of(23, 59), ZoneId.systemDefault());
    Instant next = trigger.nextExecution(new DummyTriggerContext());
    Instant now = ZonedDateTime.now(ZoneId.systemDefault()).toInstant();
    Assertions.assertThat(next).isAfterOrEqualTo(now);
  }

  @Test
  void testNextExecutionOutsideWindow() {
    TimeWindowTrigger trigger = new TimeWindowTrigger(1000L, LocalTime.of(23, 0), LocalTime.of(23, 30), ZoneId.systemDefault());
    Instant next = trigger.nextExecution(new DummyTriggerContext());
    Assertions.assertThat(next).isNotNull();
  }

  private static class DummyTriggerContext implements TriggerContext {

    @Override
    public Instant lastScheduledExecution() {
      return Instant.now();
    }

    @Override
    public Instant lastActualExecution() {
      return Instant.now();
    }

    @Override
    public Instant lastCompletion() {
      return Instant.now();
    }
  }
}
