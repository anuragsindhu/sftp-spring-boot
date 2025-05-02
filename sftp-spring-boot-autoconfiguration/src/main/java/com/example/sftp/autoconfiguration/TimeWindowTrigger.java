package com.example.sftp.autoconfiguration;

import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * A custom trigger that activates only within a specified time window.
 * <p>
 * When the current time (in the specified time zone) is within the active window defined by
 * {@code startTime} and {@code endTime}, this trigger fires at a fixed interval.
 * Outside the window, it schedules its next execution at the next occurrence of {@code startTime}.
 * </p>
 */
public class TimeWindowTrigger implements Trigger {

  private final long interval;
  private final LocalTime startTime;
  private final LocalTime endTime;
  private final ZoneId zoneId;

  /**
   * Constructs a new {@code TimeWindowTrigger}.
   *
   * @param interval  the polling interval in milliseconds during the active window
   * @param startTime the start time of the active window (inclusive)
   * @param endTime   the end time of the active window (inclusive)
   * @param zoneId    the time zone used for determining the active window
   */
  public TimeWindowTrigger(long interval, LocalTime startTime, LocalTime endTime, ZoneId zoneId) {
    this.interval = interval;
    this.startTime = startTime;
    this.endTime = endTime;
    this.zoneId = zoneId;
  }

  /**
   * Computes the next execution time as an {@link Instant}.
   * <p>
   * If the current time in the specified zone is within the active window (between {@code startTime}
   * and {@code endTime}), the trigger schedules the next execution after the configured fixed interval.
   * Otherwise, it schedules the next execution at the next occurrence of {@code startTime}.
   * </p>
   *
   * @param triggerContext the current trigger context
   * @return the {@code Instant} when the trigger should next fire
   */
  @Override
  public Instant nextExecution(TriggerContext triggerContext) {
    ZonedDateTime now = ZonedDateTime.now(zoneId);
    LocalTime currentTime = now.toLocalTime();

    if (!currentTime.isBefore(startTime) && !currentTime.isAfter(endTime)) {
      // Inside active window: use the last execution as the baseline if available.
      Instant lastActual = triggerContext.lastActualExecution();
      Instant baseTime = (lastActual != null) ? lastActual : now.toInstant();
      return baseTime.plusMillis(interval);
    } else {
      // Outside the active window: determine the next occurrence of startTime.
      ZonedDateTime nextStart;
      if (currentTime.isBefore(startTime)) {
        nextStart = now.with(startTime);
      } else {
        nextStart = now.plusDays(1).with(startTime);
      }
      return nextStart.toInstant();
    }
  }

  /**
   * Returns a string representation of this {@code TimeWindowTrigger}.
   *
   * @return a string describing the trigger
   */
  @Override
  public String toString() {
    return "TimeWindowTrigger{" +
        "interval=" + interval +
        ", startTime=" + startTime +
        ", endTime=" + endTime +
        ", zoneId=" + zoneId +
        '}';
  }
}
