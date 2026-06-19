// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.VStreamPathAssert.assertThatVStreamPath;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("HkjClientExchange.vstream — SSE consumption")
class HkjClientSseTest {

  record Tick(int n) {}

  private final JsonMapper mapper = JsonMapper.builder().build();

  private static Supplier<InputStream> sse(String body) {
    return () -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("decodes each data frame and ends on event: complete")
  void producesElements() {
    Supplier<InputStream> source = sse("data: {\"n\":1}\n\ndata: {\"n\":2}\n\nevent: complete\n\n");

    VStreamPath<Tick> path = HkjClientExchange.vstream(source, Tick.class, mapper);

    assertThatVStreamPath(path).producesElementsInOrder(List.of(new Tick(1), new Tick(2)));
  }

  @Test
  @DisplayName("flushes a trailing data frame at end-of-stream")
  void flushesTrailingFrameAtEof() {
    Supplier<InputStream> source = sse("data: {\"n\":7}\n");

    VStreamPath<Tick> path = HkjClientExchange.vstream(source, Tick.class, mapper);

    assertThatVStreamPath(path).producesElementsInOrder(List.of(new Tick(7)));
  }

  @Test
  @DisplayName("skips id: and comment lines, and joins multi-line data")
  void skipsNonDataLinesAndJoinsMultiline() {
    Supplier<InputStream> source =
        sse("id: 42\n: a comment\ndata: {\"n\":\ndata: 5}\n\nevent: complete\n\n");

    VStreamPath<Tick> path = HkjClientExchange.vstream(source, Tick.class, mapper);

    assertThatVStreamPath(path).producesElementsInOrder(List.of(new Tick(5)));
  }

  @Test
  @DisplayName("parses CRLF line endings")
  void crlfLineEndings() {
    Supplier<InputStream> source =
        sse("data: {\"n\":1}\r\n\r\ndata: {\"n\":2}\r\n\r\nevent: complete\r\n\r\n");

    VStreamPath<Tick> path = HkjClientExchange.vstream(source, Tick.class, mapper);

    assertThatVStreamPath(path).producesElementsInOrder(List.of(new Tick(1), new Tick(2)));
  }

  @Test
  @DisplayName("parses bare CR line endings (W3C SSE)")
  void crLineEndings() {
    Supplier<InputStream> source = sse("data: {\"n\":5}\r\revent: complete\r\r");

    VStreamPath<Tick> path = HkjClientExchange.vstream(source, Tick.class, mapper);

    assertThatVStreamPath(path).producesElementsInOrder(List.of(new Tick(5)));
  }

  @Test
  @DisplayName("fails to materialise when the source stream is null")
  void nullSourceFails() {
    VStreamPath<Tick> path = HkjClientExchange.vstream(() -> null, Tick.class, mapper);

    assertThatVStreamPath(path).failsOnMaterialise();
  }

  @Test
  @DisplayName("fails to materialise when a single line exceeds the size bound")
  void rejectsOversizeLine() {
    Supplier<InputStream> source = sse("data: " + "a".repeat((1 << 20) + 1));

    VStreamPath<Tick> path = HkjClientExchange.vstream(source, Tick.class, mapper);

    assertThatVStreamPath(path).failsOnMaterialise();
  }

  @Test
  @DisplayName("fails to materialise on an event: error frame")
  void errorFrameFails() {
    Supplier<InputStream> source = sse("data: {\"n\":1}\n\nevent: error\ndata: boom\n\n");

    VStreamPath<Tick> path = HkjClientExchange.vstream(source, Tick.class, mapper);

    assertThatVStreamPath(path).failsOnMaterialise();
  }

  @Test
  @DisplayName("closes the stream even when an error frame aborts materialisation")
  void errorFrameClosesStream() {
    AtomicBoolean closed = new AtomicBoolean(false);
    Supplier<InputStream> source =
        () ->
            new ByteArrayInputStream(
                "data: {\"n\":1}\n\nevent: error\ndata: boom\n\n"
                    .getBytes(StandardCharsets.UTF_8)) {
              @Override
              public void close() {
                closed.set(true);
              }
            };

    VStreamPath<Tick> path = HkjClientExchange.vstream(source, Tick.class, mapper);
    try {
      path.toList().unsafeRun();
    } catch (RuntimeException expected) {
      // the error frame fails materialisation
    }

    assertThat(closed).as("stream closed despite the error frame").isTrue();
  }

  @Test
  @DisplayName("defers the call until the path is materialised, then closes the stream")
  void deferredAndResourceSafe() {
    AtomicBoolean opened = new AtomicBoolean(false);
    AtomicBoolean closed = new AtomicBoolean(false);
    Supplier<InputStream> source =
        () -> {
          opened.set(true);
          return new ByteArrayInputStream(
              "data: {\"n\":1}\n\nevent: complete\n\n".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
              closed.set(true);
            }
          };
        };

    VStreamPath<Tick> path = HkjClientExchange.vstream(source, Tick.class, mapper);
    assertThat(opened).as("supplier not called before materialisation").isFalse();

    path.toList().unsafeRun();

    assertThat(opened).isTrue();
    assertThat(closed).as("stream closed after consumption").isTrue();
  }
}
