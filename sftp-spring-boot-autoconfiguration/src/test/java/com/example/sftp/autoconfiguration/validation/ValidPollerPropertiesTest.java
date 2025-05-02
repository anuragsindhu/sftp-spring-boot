package com.example.sftp.autoconfiguration.validation;

import com.example.sftp.autoconfiguration.SftpProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidPollerPropertiesTest {

  private static Validator validator;

  @BeforeAll
  public static void setUpValidator() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  // --- Fixed Poller Tests ---

  @Test
  void validFixedPollerShouldBeValid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("fixed")
        .fixedInterval(1000L)
        .build();

    Set<ConstraintViolation<SftpProperties.PollerProperties>> violations = validator.validate(poller);
    assertThat(violations)
        .as("A fixed poller with a positive fixedInterval should be valid.")
        .isEmpty();
  }

  @Test
  void fixedPollerMissingFixedIntervalShouldBeInvalid() {
    // When type is "fixed", fixedInterval is required.
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("fixed")
        .build();

    Set<ConstraintViolation<SftpProperties.PollerProperties>> violations = validator.validate(poller);
    assertThat(violations)
        .as("A fixed poller missing fixedInterval should be invalid.")
        .isNotEmpty();
  }

  @Test
  void fixedPollerWithNonPositiveFixedIntervalShouldBeInvalid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("fixed")
        .fixedInterval(0L)
        .build();

    Set<ConstraintViolation<SftpProperties.PollerProperties>> violations = validator.validate(poller);
    assertThat(violations)
        .as("A fixed poller with zero or negative fixedInterval should be invalid.")
        .isNotEmpty();
  }

  // --- Time Window Poller Tests ---

  @Test
  void validTimeWindowPollerShouldBeValid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("timeWindow")
        .startTime("08:00")
        .endTime("17:00")
        .windowInterval(60000L)
        .timeZone("UTC")
        .build();

    Set<ConstraintViolation<SftpProperties.PollerProperties>> violations = validator.validate(poller);
    assertThat(violations)
        .as("A timeWindow poller with startTime, endTime, windowInterval, and timeZone should be valid.")
        .isEmpty();
  }

  @Test
  void timeWindowPollerMissingStartTimeShouldBeInvalid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("timeWindow")
        .endTime("17:00")
        .windowInterval(60000L)
        .timeZone("UTC")
        .build();

    Set<ConstraintViolation<SftpProperties.PollerProperties>> violations = validator.validate(poller);
    assertThat(violations)
        .as("A timeWindow poller missing startTime should be invalid.")
        .isNotEmpty();
  }

  @Test
  void timeWindowPollerMissingEndTimeShouldBeInvalid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("timeWindow")
        .startTime("08:00")
        .windowInterval(60000L)
        .timeZone("UTC")
        .build();

    Set<ConstraintViolation<SftpProperties.PollerProperties>> violations = validator.validate(poller);
    assertThat(violations)
        .as("A timeWindow poller missing endTime should be invalid.")
        .isNotEmpty();
  }

  @Test
  void timeWindowPollerMissingWindowIntervalShouldBeInvalid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("timeWindow")
        .startTime("08:00")
        .endTime("17:00")
        .timeZone("UTC")
        .build();

    Set<ConstraintViolation<SftpProperties.PollerProperties>> violations = validator.validate(poller);
    assertThat(violations)
        .as("A timeWindow poller missing windowInterval should be invalid.")
        .isNotEmpty();
  }

  @Test
  void timeWindowPollerMissingTimeZoneShouldBeInvalid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("timeWindow")
        .startTime("08:00")
        .endTime("17:00")
        .windowInterval(60000L)
        .build();

    Set<ConstraintViolation<SftpProperties.PollerProperties>> violations = validator.validate(poller);
    assertThat(violations)
        .as("A timeWindow poller missing timeZone should be invalid.")
        .isNotEmpty();
  }

  // --- Unknown Poller Type ---

  @Test
  void pollerWithUnknownTypeShouldBeInvalid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("invalidType")
        .build();

    Set<ConstraintViolation<SftpProperties.PollerProperties>> violations = validator.validate(poller);
    assertThat(violations)
        .as("A poller with an unknown type should be invalid.")
        .isNotEmpty();
  }
}
