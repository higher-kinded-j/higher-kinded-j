// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runtime translators that fold an HTTP exchange into a Higher-Kinded-J Effect Path, decoding a
 * typed error from non-2xx responses.
 *
 * <p>Each translator runs a supplier that performs the HTTP call (typically a method on a Spring
 * {@code @HttpExchange} proxy returning {@link ResponseEntity}). A 2xx outcome becomes the success
 * arm of the Path; a {@link RestClientResponseException} (an HTTP error <em>response</em>) is
 * decoded via the supplied {@link ResponseErrorDecoder} into the error arm. This is the client-side
 * inverse of the server's {@code *PathReturnValueHandler}s.
 *
 * <p><b>Exception boundary.</b> Only an HTTP error response ({@code RestClientResponseException})
 * is folded into the typed error arm. Transport failures (connection refused, timeout — Spring's
 * {@code ResourceAccessException}) and decode failures ({@link ResponseErrorDecodeException}) are
 * <em>not</em> typed domain errors and so propagate. They surface synchronously from the eager
 * {@link #either}/{@link #maybe} translators and as a failed task from the deferred {@link
 * #eitherVTask}/{@link #vstream} translators — matching the eager-vs-deferred nature of {@code
 * EitherPath} vs {@code VTaskPath}/{@code VStreamPath}.
 */
public final class HkjClientExchange {

  /**
   * Upper bound on a single SSE line and on the accumulated data of one frame, so a malicious or
   * faulty server cannot exhaust the caller's heap with one unbounded line/frame (the response body
   * is untrusted input). 1 MiB of characters is generous for a single event payload.
   */
  private static final int MAX_SSE_FRAME_CHARS = 1 << 20;

  private HkjClientExchange() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Folds a blocking HTTP call into an {@link EitherPath}: 2xx → {@code Right(body)}, 4xx/5xx →
   * {@code Left(decoded error)}. A 2xx response with an empty body (e.g. 204) yields {@code
   * Right(null)}; if your endpoint may return no body, declare {@code T} accordingly or guard the
   * success value.
   *
   * @param call performs the HTTP call and returns the response entity
   * @param decoder decodes a failed response into a typed error
   * @param <E> the typed error
   * @param <T> the success body type
   * @return an {@code EitherPath} carrying the outcome
   */
  public static <E, T> EitherPath<E, T> either(
      Supplier<ResponseEntity<T>> call, ResponseErrorDecoder<E> decoder) {
    try {
      ResponseEntity<T> response = call.get();
      return Path.right(response.getBody());
    } catch (RestClientResponseException ex) {
      return Path.left(decoded(decoder, ex));
    }
  }

  /**
   * Folds an HTTP call into a {@link VTaskPath} deferred on a virtual thread, yielding {@code
   * Either<E, T>}. The call is not made until the task is run, so callers can layer {@code
   * withRetry}/{@code withCircuitBreaker}/{@code timeout} on the returned path.
   *
   * @param call performs the HTTP call and returns the response entity
   * @param decoder decodes a failed response into a typed error
   * @param <E> the typed error
   * @param <T> the success body type
   * @return a deferred {@code VTaskPath} carrying the {@code Either} outcome
   */
  public static <E, T> VTaskPath<Either<E, T>> eitherVTask(
      Supplier<ResponseEntity<T>> call, ResponseErrorDecoder<E> decoder) {
    return Path.vtask(
        () -> {
          try {
            ResponseEntity<T> response = call.get();
            return Either.right(response.getBody());
          } catch (RestClientResponseException ex) {
            return Either.left(decoded(decoder, ex));
          }
        });
  }

  /**
   * Folds a blocking HTTP call into a {@link MaybePath}: 2xx with a body → {@code Just(body)}, a
   * 404 or an empty 2xx body → {@code Nothing}. Other failures propagate as the original exception.
   *
   * @param call performs the HTTP call and returns the response entity
   * @param <T> the success body type
   * @return a {@code MaybePath} carrying the outcome
   */
  public static <T> MaybePath<T> maybe(Supplier<ResponseEntity<T>> call) {
    try {
      // Path.maybe folds null (an empty 2xx body) to Nothing and a present body to Just.
      return Path.maybe(call.get().getBody());
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404) {
        return Path.nothing();
      }
      throw ex;
    }
  }

  /**
   * Folds a Server-Sent-Event response into a {@link VStreamPath}: each {@code data:} frame is
   * decoded into {@code T}, an {@code event: complete} frame ends the stream, and an {@code event:
   * error} frame fails it with an {@link SseStreamException}. This is the client-side inverse of
   * the server's {@code VStreamPathReturnValueHandler} SSE format.
   *
   * <p>Consumption is lazy: the supplier is invoked, and frames are read and decoded one at a time
   * as the path is pulled. The stream is closed once the path is drained to completion or fails
   * (bracket semantics over {@link VStream#unfold}). Prefer a draining or {@code take}-bounded
   * terminal (e.g. {@code toList()}, {@code take(n).toList()}); a short-circuiting terminal such as
   * {@code headOption()}/{@code find(...)} returns before the stream completes and may leave the
   * underlying HTTP response open, so avoid those on an SSE source.
   *
   * @param source supplies the SSE response body stream
   * @param elementType the element type each {@code data:} frame decodes into
   * @param mapper the Jackson 3.x mapper used to decode each frame
   * @param <T> the element type
   * @return a {@code VStreamPath} over the decoded elements
   */
  public static <T> VStreamPath<T> vstream(
      Supplier<InputStream> source, Class<T> elementType, JsonMapper mapper) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(elementType, "elementType");
    Objects.requireNonNull(mapper, "mapper");
    return Path.vstreamBracket(
        VTask.of(() -> Objects.requireNonNull(source.get(), "SSE source stream must not be null")),
        stream ->
            VStream.unfold(
                new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)),
                reader -> VTask.of(() -> readSseFrame(reader, elementType, mapper))),
        stream ->
            VTask.of(
                () -> {
                  stream.close();
                  return Unit.INSTANCE;
                }));
  }

  /**
   * Reads and decodes the next SSE {@code data:} frame from {@code reader}, returning the decoded
   * element paired with the (same, advanced) reader as the next unfold state, or empty when the
   * stream completes ({@code event: complete} or end-of-input). An {@code event: error} frame
   * throws {@link SseStreamException}. Because this runs inside a pulled {@code VTask}, any failure
   * (parse, I/O, or error frame) surfaces as a failed step that the surrounding bracket releases.
   */
  static <T> Optional<VStream.Seed<T, BufferedReader>> readSseFrame(
      BufferedReader reader, Class<T> elementType, JsonMapper mapper) throws IOException {
    String event = null;
    StringBuilder data = new StringBuilder();
    String line;
    while ((line = readBoundedLine(reader)) != null) {
      if (line.isEmpty()) {
        // Blank line ends a frame. A `complete` event ends the stream; otherwise emit any
        // accumulated data, or (a keepalive/comment frame with none) reset and read the next frame.
        if ("complete".equals(event)) {
          return Optional.empty();
        }
        Optional<VStream.Seed<T, BufferedReader>> frame =
            emitDataFrame(event, data, reader, elementType, mapper);
        if (frame.isPresent()) {
          return frame;
        }
        event = null;
        data.setLength(0);
      } else if (line.startsWith("event:")) {
        event = line.substring("event:".length()).trim();
      } else if (line.startsWith("data:")) {
        if (!data.isEmpty()) {
          data.append('\n');
        }
        data.append(line.substring("data:".length()).trim());
        if (data.length() > MAX_SSE_FRAME_CHARS) {
          throw new SseStreamException("SSE frame exceeded " + MAX_SSE_FRAME_CHARS + " characters");
        }
      }
      // other SSE fields (id:, retry:) and comment lines (":") are ignored
    }
    // End-of-input: fail a pending error, emit a final unterminated frame, else complete.
    return emitDataFrame(event, data, reader, elementType, mapper);
  }

  /**
   * Resolves a completed SSE frame, shared by the blank-line frame boundary and end-of-input so the
   * terminal decision lives in one place: an {@code error} event fails with {@link
   * SseStreamException}; a non-{@code complete} frame carrying data decodes into a {@link
   * VStream.Seed}; anything else (a {@code complete} frame, or a frame with no data) yields empty.
   * The caller decides what empty means — continue reading mid-stream, or finish at end-of-input.
   */
  private static <T> Optional<VStream.Seed<T, BufferedReader>> emitDataFrame(
      @Nullable String event,
      StringBuilder data,
      BufferedReader reader,
      Class<T> elementType,
      JsonMapper mapper) {
    String payload = data.toString().trim();
    if ("error".equals(event)) {
      throw new SseStreamException(payload);
    }
    if (!"complete".equals(event) && !data.isEmpty()) {
      return Optional.of(new VStream.Seed<>(mapper.readValue(payload, elementType), reader));
    }
    return Optional.empty();
  }

  /**
   * Reads one line, normalising CRLF, but aborts with {@link SseStreamException} once a single line
   * exceeds {@link #MAX_SSE_FRAME_CHARS} — unlike {@link BufferedReader#readLine()}, which would
   * buffer an unbounded line from an untrusted stream. Returns {@code null} at end-of-input.
   */
  private static @Nullable String readBoundedLine(BufferedReader reader) throws IOException {
    int c = reader.read();
    if (c == -1) {
      return null;
    }
    StringBuilder line = new StringBuilder();
    // SSE lines are terminated by LF, CR, or CRLF (W3C). Stop on either terminator.
    while (c != -1 && c != '\n' && c != '\r') {
      if (line.length() >= MAX_SSE_FRAME_CHARS) {
        throw new SseStreamException("SSE line exceeded " + MAX_SSE_FRAME_CHARS + " characters");
      }
      line.append((char) c);
      c = reader.read();
    }
    if (c == '\r') {
      // Consume a following LF if this is a CRLF pair; otherwise leave it for the next read.
      reader.mark(1);
      if (reader.read() != '\n') {
        reader.reset();
      }
    }
    return line.toString();
  }

  /** Decodes a failed response, enforcing the {@link ResponseErrorDecoder} non-null contract. */
  private static <E> E decoded(ResponseErrorDecoder<E> decoder, RestClientResponseException ex) {
    return Objects.requireNonNull(
        decoder.decode(toErrorResponse(ex)), "ResponseErrorDecoder returned null");
  }

  private static ClientErrorResponse toErrorResponse(RestClientResponseException ex) {
    return new ClientErrorResponse(
        ex.getStatusCode(), ex.getResponseBodyAsString(), ex.getResponseHeaders());
  }
}
