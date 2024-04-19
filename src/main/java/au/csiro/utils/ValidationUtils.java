package au.csiro.utils;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.ConstraintValidatorContext;
import javax.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

/**
 * Utility classes to facilitate JRS-380 based validation
 */
public final class ValidationUtils {

  private ValidationUtils() {
    // utility class
  }

  // We use the ParameterMessageInterpolator rather than the default one which depends on EL implementation
  // for message interpolation as it causes library conflicts in Databricks environments.
  private static final ValidatorFactory DEFAULT_VALIDATION_FACTORY = Validation.byDefaultProvider()
      .configure()
      .messageInterpolator(new ParameterMessageInterpolator())
      .buildValidatorFactory();

  /**
   * Validates a bean annotated with JSR-380 constraints using the default validation factory.
   *
   * @param bean the bean to validate
   * @param <T> the type of the bean.
   * @return the set of violated constrains, empty if the bean is valid.
   */
  @Nonnull
  public static <T> Set<ConstraintViolation<T>> validate(@Nonnull final T bean) {
    final Validator validator = DEFAULT_VALIDATION_FACTORY.getValidator();
    return validator.validate(bean);
  }

  /**
   * Ensures that a bean annotated with JSR-380 constraints is valid. If validation with the default
   * validation factory results in any violation throws the {@link ConstraintViolationException}.
   *
   * @param bean the bean to validate
   * @param message the message to use as the title of the exception message.
   * @param <T> the type of the bean.
   * @return the valid bean.
   * @throws ConstraintViolationException if any constraints are violated.
   */

  @SuppressWarnings("UnusedReturnValue")
  @Nonnull
  public static <T> T ensureValid(@Nonnull final T bean, @Nonnull final String message)
      throws ConstraintViolationException {
    final Set<ConstraintViolation<T>> constraintViolations = validate(bean);
    if (!constraintViolations.isEmpty()) {
      failValidation(constraintViolations, message);
    }
    return bean;
  }

  /**
   * Fails with the {@link ConstraintViolationException} that includes the violated constraints and
   * the human-readable representation of them.
   *
   * @param constraintViolations the violation to include in the exception.
   * @param messageTitle the title of the error message.
   */
  public static void failValidation(
      @Nonnull final Set<? extends ConstraintViolation<?>> constraintViolations,
      @Nullable final String messageTitle) throws ConstraintViolationException {
    final String exceptionMessage = nonNull(messageTitle)
                                    ? messageTitle + "\n" + formatViolations(constraintViolations)
                                    : formatViolations(constraintViolations);
    throw new ConstraintViolationException(exceptionMessage, constraintViolations);
  }

  /**
   * Formats a set of {@link ConstraintViolation} to a human-readable string.
   *
   * @param constraintViolations the violations to include.
   * @return the  human-readable representaion of the violations.
   */
  @Nonnull
  public static String formatViolations(
      @Nonnull final Set<? extends ConstraintViolation<?>> constraintViolations) {
    return constraintViolations.stream()
        .filter(Objects::nonNull)
        .map(cv -> isNull(cv.getPropertyPath()) || cv.getPropertyPath().toString().isBlank()
                   ? cv.getMessage()
                   : cv.getPropertyPath() + ": " + cv.getMessage())
        .sorted()
        .collect(Collectors.joining("\n"));
  }

  /**
   * Accumulates constraint violations and provides a fluent API to add violations.
   */
  @AllArgsConstructor(staticName = "of")
  public static class ViolationAccumulator {

    @Nonnull
    private final ConstraintValidatorContext context;

    @Getter
    private boolean valid = true;

    private ViolationAccumulator(@Nonnull final ConstraintValidatorContext context) {
      this.context = context;
    }

    /**
     * Checks the assertion and adds a violation if it is false.
     *
     * @param assertion the assertion to check.
     * @param message the message to add to the violation.
     * @param property the property to add to the violation.
     * @return the accumulator.
     */
    public ViolationAccumulator checkThat(boolean assertion, @Nonnull final String message,
        @Nonnull final String property) {
      return checkThat(assertion, message, Optional.of(property));
    }

    /**
     * Checks the assertion and adds a violation if it is false.
     *
     * @param assertion the assertion to check.
     * @param message the message to add to the violation.
     * @return the accumulator.
     */
    public ViolationAccumulator checkThat(boolean assertion, @Nonnull final String message) {
      return checkThat(assertion, message, Optional.empty());
    }

    /**
     * Checks the assertion and adds a violation if it is false.
     *
     * @param assertion the assertion to check.
     * @param message the message to add to the violation.
     * @param maybeProperty the optional maybeProperty to add to the violation.
     * @return the accumulator.
     */
    public ViolationAccumulator checkThat(boolean assertion, @Nonnull final String message,
        @Nonnull final Optional<String> maybeProperty) {
      if (!assertion) {
        return addViolation(message, maybeProperty);
      } else {
        return this;
      }
    }

    /**
     * Adds a violation with the given message and property.
     *
     * @param message the message to add to the violation.
     * @param property the property to add to the violation.
     * @return the accumulator.
     */
    public ViolationAccumulator addViolation(@Nonnull final String message,
        @Nonnull final Optional<String> property) {
      final ConstraintViolationBuilder builder = context.buildConstraintViolationWithTemplate(
          message);
      property.ifPresentOrElse(p -> builder.addPropertyNode(p).addConstraintViolation(),
          builder::addConstraintViolation);
      valid = false;
      return this;
    }

    /**
     * Creates new accumulator with the default violation disabled in the given context.
     *
     * @param context the context to add the violation to.
     * @return the accumulator.
     */
    public static ViolationAccumulator withNoDefault(
        @Nonnull final ConstraintValidatorContext context) {
      context.disableDefaultConstraintViolation();
      return new ViolationAccumulator(context);
    }
  }
}
