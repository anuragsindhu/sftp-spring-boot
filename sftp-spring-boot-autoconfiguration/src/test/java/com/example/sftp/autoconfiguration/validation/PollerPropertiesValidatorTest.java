package com.example.sftp.autoconfiguration.validation;

import com.example.sftp.autoconfiguration.SftpProperties;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PollerPropertiesValidatorTest {

  private PollerPropertiesValidator validator;
  private ConstraintValidatorContext context;

  @BeforeEach
  public void setUp() {
    validator = new PollerPropertiesValidator();
    context = mock(ConstraintValidatorContext.class);

    // Create a mock for the violation builder chain.
    ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder =
        mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
    ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilderCustomizableContext =
        mock(ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext.class);

    // Stub the chain calls. When the validator calls:
    // context.buildConstraintViolationWithTemplate(...).addPropertyNode(...).addConstraintViolation()
    // these mocks will return non-null, preventing an NPE.
    when(context.buildConstraintViolationWithTemplate(any(String.class))).thenReturn(violationBuilder);
    when(violationBuilder.addPropertyNode(any(String.class))).thenReturn(nodeBuilderCustomizableContext);
    when(nodeBuilderCustomizableContext.addConstraintViolation()).thenReturn(context);
  }

  // ---------- Null PollerProperties ----------
  @Test
  void nullPollerPropertiesShouldBeValid() {
    // According to common Bean Validation convention,
    // a null value is considered valid.
    boolean valid = validator.isValid(null, context);
    assertThat(valid)
        .as("Null poller properties should be considered valid")
        .isTrue();
  }

  // ---------- Fixed Poller ----------
  @Test
  void fixedPollerWithPositiveFixedIntervalShouldBeValid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("fixed")
        .fixedInterval(1000L)
        .build();
    boolean valid = validator.isValid(poller, context);
    assertThat(valid)
        .as("Fixed poller with a positive fixedInterval should be valid")
        .isTrue();
  }

  @Test
  void fixedPollerMissingFixedIntervalShouldBeInvalid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("fixed")
        .build();
    boolean valid = validator.isValid(poller, context);
    assertThat(valid)
        .as("Fixed poller missing fixedInterval should be invalid")
        .isFalse();
  }

  @Test
  void fixedPollerWithNonPositiveFixedIntervalShouldBeInvalid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("fixed")
        .fixedInterval(0L)
        .build();
    boolean valid = validator.isValid(poller, context);
    assertThat(valid)
        .as("Fixed poller with non-positive fixedInterval should be invalid")
        .isFalse();
  }

  // ---------- Time Window Poller ----------
  @Test
  void timeWindowPollerWithAllRequiredFieldsShouldBeValid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("timeWindow")
        .startTime("08:00")
        .endTime("17:00")
        .windowInterval(60000L)
        .timeZone("UTC")
        .build();
    boolean valid = validator.isValid(poller, context);
    assertThat(valid)
        .as("Time window poller with startTime, endTime, windowInterval, and timeZone should be valid")
        .isTrue();
  }

  @Test
  void timeWindowPollerMissingStartTimeShouldBeInvalid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("timeWindow")
        .endTime("17:00")
        .windowInterval(60000L)
        .timeZone("UTC")
        .build();
    boolean valid = validator.isValid(poller, context);
    assertThat(valid)
        .as("Time window poller missing startTime should be invalid")
        .isFalse();
  }

  @Test
  void timeWindowPollerMissingEndTimeShouldBeInvalid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("timeWindow")
        .startTime("08:00")
        .windowInterval(60000L)
        .timeZone("UTC")
        .build();
    boolean valid = validator.isValid(poller, context);
    assertThat(valid)
        .as("Time window poller missing endTime should be invalid")
        .isFalse();
  }

  @Test
  void timeWindowPollerMissingWindowIntervalShouldBeInvalid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("timeWindow")
        .startTime("08:00")
        .endTime("17:00")
        .timeZone("UTC")
        .build();
    boolean valid = validator.isValid(poller, context);
    assertThat(valid)
        .as("Time window poller missing windowInterval should be invalid")
        .isFalse();
  }

  @Test
  void timeWindowPollerMissingTimeZoneShouldBeInvalid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("timeWindow")
        .startTime("08:00")
        .endTime("17:00")
        .windowInterval(60000L)
        .build();
    boolean valid = validator.isValid(poller, context);
    assertThat(valid)
        .as("Time window poller missing timeZone should be invalid")
        .isFalse();
  }

  // ---------- Unknown Poller Type ----------
  @Test
  void pollerWithUnknownTypeShouldBeInvalid() {
    SftpProperties.PollerProperties poller = SftpProperties.PollerProperties.builder()
        .type("unknown")
        .build();
    boolean valid = validator.isValid(poller, context);
    assertThat(valid)
        .as("Poller with unknown type should be invalid")
        .isFalse();
  }
}
