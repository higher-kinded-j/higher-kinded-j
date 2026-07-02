// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assembly;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A located validation error: a message plus a composable path saying <b>where</b> the problem is.
 *
 * <p>{@code FieldError} is the error type of the {@code fields()} assembly flavour. A leaf
 * validator creates an unlabelled error with {@link #of(String)}; the assembly's {@code
 * field(label, value)} call attaches the leaf segment; and nesting a sub-assembly under an outer
 * label <b>prepends</b> further segments, so paths compose outward-in:
 *
 * <pre>{@code
 * FieldError.of("not a postcode")          // "not a postcode"
 *     .at("zip")                           // "zip: not a postcode"
 *     .at("address")                       // "address.zip: not a postcode"
 * }</pre>
 *
 * <p>Path segments are plain strings; the record is deliberately small and {@link
 * org.higherkindedj.hkt.nonemptylist.NonEmptyList}-friendly. Not to be confused with Spring's
 * {@code org.springframework.validation.FieldError}, which serves Spring MVC data binding; this
 * type is HKJ's carrier for accumulating, located validation errors.
 *
 * @param path the segments locating the error, outermost first; never null, defensively copied
 * @param message the human-readable description; never null
 */
public record FieldError(List<String> path, String message) {

  /**
   * Canonical constructor; validates and defensively copies.
   *
   * @throws NullPointerException if {@code path}, any of its segments, or {@code message} is null
   */
  public FieldError {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(message, "message must not be null");
    path = List.copyOf(path);
  }

  /**
   * Creates an unlabelled error. The assembly's {@code field(label, value)} call attaches the
   * location.
   *
   * @param message the human-readable description; must not be null
   * @return a {@code FieldError} with an empty path
   * @throws NullPointerException if {@code message} is null
   */
  public static FieldError of(String message) {
    return new FieldError(List.of(), message);
  }

  /**
   * Returns a new {@code FieldError} with {@code segment} <b>prepended</b> to the path, so that
   * outer labels compose around inner ones ({@code "zip"} becomes {@code "address.zip"}).
   *
   * @param segment the path segment to prepend; must not be null
   * @return a new {@code FieldError}; this instance is unchanged
   * @throws NullPointerException if {@code segment} is null
   */
  public FieldError at(String segment) {
    Objects.requireNonNull(segment, "segment must not be null");
    return new FieldError(Stream.concat(Stream.of(segment), path.stream()).toList(), message);
  }

  /**
   * The dot-joined path, for example {@code "address.zip"}; empty for unlabelled errors.
   *
   * @return the rendered path
   */
  public String pathString() {
    return String.join(".", path);
  }

  /** Renders as {@code "address.zip: message"}, or just the message when unlabelled. */
  @Override
  public String toString() {
    return path.isEmpty() ? message : pathString() + ": " + message;
  }
}
