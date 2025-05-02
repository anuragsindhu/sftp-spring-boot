package com.example.sftp.autoconfiguration.validation;

import com.example.sftp.autoconfiguration.SftpProperties.PollerProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;

public class PollerPropertiesValidator implements ConstraintValidator<ValidPollerProperties, PollerProperties> {

  @Override
  public boolean isValid(PollerProperties poller, ConstraintValidatorContext context) {
    if (poller == null) {
      return true; // Other @NotNull annotations must catch a null value.
    }
    boolean valid = true;
    context.disableDefaultConstraintViolation();

    String type = poller.getType();
    if (StringUtils.isBlank(type)) {
      valid = false;
      context.buildConstraintViolationWithTemplate("Poller type must not be blank")
          .addPropertyNode("type")
          .addConstraintViolation();
    } else if ("fixed".equalsIgnoreCase(type)) {
      if (poller.getFixedInterval() == null || poller.getFixedInterval() < 1) {
        valid = false;
        context.buildConstraintViolationWithTemplate("For fixed polling, fixedInterval must be provided and be at least 1 millisecond")
            .addPropertyNode("fixedInterval")
            .addConstraintViolation();
      }
    } else if ("timeWindow".equalsIgnoreCase(type)) {
      if (StringUtils.isBlank(poller.getStartTime())) {
        valid = false;
        context.buildConstraintViolationWithTemplate("For timeWindow polling, startTime must not be blank")
            .addPropertyNode("startTime")
            .addConstraintViolation();
      }
      if (StringUtils.isBlank(poller.getEndTime())) {
        valid = false;
        context.buildConstraintViolationWithTemplate("For timeWindow polling, endTime must not be blank")
            .addPropertyNode("endTime")
            .addConstraintViolation();
      }
      if (poller.getWindowInterval() == null || poller.getWindowInterval() < 1) {
        valid = false;
        context.buildConstraintViolationWithTemplate("For timeWindow polling, windowInterval must be provided and be at least 1 millisecond")
            .addPropertyNode("windowInterval")
            .addConstraintViolation();
      }
      if (StringUtils.isBlank(poller.getTimeZone())) {
        valid = false;
        context.buildConstraintViolationWithTemplate("For timeWindow polling, timeZone must not be blank")
            .addPropertyNode("timeZone")
            .addConstraintViolation();
      }
    } else {
      valid = false;
      context.buildConstraintViolationWithTemplate("Poller type must be either 'fixed' or 'timeWindow'")
          .addPropertyNode("type")
          .addConstraintViolation();
    }

    // Optional: if fallbackFixedDelay is provided, validate its minimum value.
    if (poller.getFallbackFixedDelay() != null && poller.getFallbackFixedDelay() < 1) {
      valid = false;
      context.buildConstraintViolationWithTemplate("Fallback fixed delay must be at least 1 millisecond if provided")
          .addPropertyNode("fallbackFixedDelay")
          .addConstraintViolation();
    }
    return valid;
  }
}
