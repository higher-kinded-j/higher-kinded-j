// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** Shared response-writing helpers for the Path return value handlers. */
final class JsonResponses {

  private JsonResponses() {}

  /**
   * Sets the JSON content type with an explicit UTF-8 charset. The servlet default response
   * encoding is ISO-8859-1, which would corrupt non-Latin body text.
   *
   * @param response the HTTP response
   */
  static void setJsonContentType(HttpServletResponse response) {
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
  }

  /**
   * Whether the given status forbids a response body per HTTP semantics: {@code 204 No Content},
   * {@code 205 Reset Content}, and {@code 304 Not Modified}. Successful response writers must skip
   * serialisation for these.
   *
   * @param status the HTTP status code
   * @return {@code true} when the status must not carry a body
   */
  static boolean isBodilessStatus(int status) {
    return status == HttpStatus.NO_CONTENT.value()
        || status == HttpStatus.RESET_CONTENT.value()
        || status == HttpStatus.NOT_MODIFIED.value();
  }

  /**
   * Unwraps only the async wrapper exceptions ({@link CompletionException}, {@link
   * ExecutionException}); a domain exception's own cause chain is preserved.
   *
   * @param throwable the raw failure from an async computation
   * @return the wrapped cause, or the throwable itself
   */
  static Throwable unwrapAsyncException(Throwable throwable) {
    return (throwable instanceof CompletionException || throwable instanceof ExecutionException)
            && throwable.getCause() != null
        ? throwable.getCause()
        : throwable;
  }

  /**
   * Materialises a one-shot {@link Iterable} error payload into a list so it can be traversed more
   * than once (status resolution, headers, then the JSON body). Re-traversable payloads — {@link
   * Collection}, {@link NonEmptyList}, and arrays — are returned unchanged so their serialised
   * shape is preserved. A one-shot iterable would otherwise be exhausted by the first pass and
   * reach the body writer empty.
   *
   * @param errors the error payload (any type)
   * @return a re-traversable equivalent of {@code errors}
   */
  static Object materialiseErrors(Object errors) {
    if (errors instanceof Collection<?>
        || errors instanceof NonEmptyList<?>
        || !(errors instanceof Iterable<?> iterable)) {
      return errors;
    }
    List<Object> list = new ArrayList<>();
    iterable.forEach(list::add);
    return list;
  }
}
