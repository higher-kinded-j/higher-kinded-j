// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.error.ErrorEnvelope;
import org.higherkindedj.hkt.time.TimeSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ErrorEnvelopeProcessor - @GenerateErrorEnvelope companion generation")
class ErrorEnvelopeProcessorTest {

  private static final Instant FROZEN = Instant.parse("2026-07-07T00:00:00Z");

  private static final JavaFileObject CONTEXT =
      JavaFileObjects.forSourceString(
          "com.example.FooErrorContext",
          """
          package com.example;

          public record FooErrorContext(String orderId, String traceId) {}
          """);

  /** The happy-path hierarchy, including the issue's instance-chaining default method. */
  private static final JavaFileObject FOO_ERROR =
      JavaFileObjects.forSourceString(
          "com.example.FooError",
          """
          package com.example;

          import java.util.List;
          import java.util.function.UnaryOperator;
          import org.higherkindedj.hkt.error.ErrorEnvelope;
          import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;

          @GenerateErrorEnvelope
          public sealed interface FooError {
            ErrorEnvelope<FooErrorContext> envelope();

            default FooError editContext(UnaryOperator<FooErrors.ContextBuilder> edit) {
              return FooErrors.editContext(this, edit);
            }

            record OutOfStock(List<String> products, ErrorEnvelope<FooErrorContext> envelope)
                implements FooError {}

            record PaymentDeclined(
                ErrorEnvelope<FooErrorContext> envelope, String card, String reason)
                implements FooError {}

            record SystemHalted(ErrorEnvelope<FooErrorContext> envelope) implements FooError {}
          }
          """);

  private static JavaFileObject errorSource(String name, String body) {
    return JavaFileObjects.forSourceString(
        "com.example." + name,
        """
        package com.example;

        import org.higherkindedj.hkt.error.ErrorEnvelope;
        import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;

        """
            + body);
  }

  private Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new ErrorEnvelopeProcessor()).compile(sources);
  }

  private static String generatedSource(Compilation compilation, String qualifiedName) {
    Optional<JavaFileObject> file =
        compilation.generatedFile(
            StandardLocation.SOURCE_OUTPUT, qualifiedName.replace('.', '/') + ".java");
    Assertions.assertThat(file).isPresent();
    try {
      return file.get().getCharContent(true).toString();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  /** Reflectively invokes an instance method (declared or inherited default) on a fixture value. */
  private static Object invoke(Object target, String name, Object... args) {
    try {
      for (var method : target.getClass().getDeclaredMethods()) {
        if (method.getName().equals(name) && method.getParameterCount() == args.length) {
          method.setAccessible(true);
          return method.invoke(target, args);
        }
      }
      for (var method : target.getClass().getMethods()) {
        if (method.getName().equals(name) && method.getParameterCount() == args.length) {
          method.setAccessible(true);
          return method.invoke(target, args);
        }
      }
      throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  @Nested
  @DisplayName("Happy path: generated source")
  class HappyPathSource {

    @Test
    @DisplayName("the companion carries factories, builder and wither, and compiles")
    void companionCarriesFactoriesBuilderAndWither() {
      Compilation compilation = compile(CONTEXT, FOO_ERROR);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.FooErrors");
      Assertions.assertThat(generated)
          .contains("public final class FooErrors")
          // The all-absent context, nulls through the canonical constructor.
          .contains("new FooErrorContext((String) null, (String) null)")
          // Convenience factory delegates to the timed overload.
          .contains("return outOfStock(TimeSource.system(), products)")
          // Timed factories: UPPER_SNAKE code, humanised message, TimeSource-read timestamp.
          .contains("ErrorEnvelope.of(time, \"OUT_OF_STOCK\", \"Out of stock\", ABSENT_CONTEXT)")
          .contains(
              "ErrorEnvelope.of(time, \"PAYMENT_DECLINED\", \"Payment declined\","
                  + " ABSENT_CONTEXT)")
          .contains("ErrorEnvelope.of(time, \"SYSTEM_HALTED\", \"System halted\", ABSENT_CONTEXT)")
          .contains("Objects.requireNonNull(time, \"time must not be null\")")
          // Context builder over the context record's components.
          .contains("public static ContextBuilder context()")
          .contains("public static final class ContextBuilder")
          .contains("public ContextBuilder orderId(String orderId)")
          .contains("public ContextBuilder traceId(String traceId)")
          .contains("return new FooErrorContext(orderId, traceId)")
          // Exhaustive wither, envelope position preserved per variant.
          .contains(
              "public static FooError editContext(FooError error, UnaryOperator<ContextBuilder>"
                  + " edit)")
          .contains(
              "case FooError.OutOfStock v -> new FooError.OutOfStock(v.products(),"
                  + " rebuild(v.envelope(), edit));")
          .contains(
              "case FooError.PaymentDeclined v -> new FooError.PaymentDeclined("
                  + "rebuild(v.envelope(), edit), v.card(), v.reason());")
          .contains(
              "case FooError.SystemHalted v -> new FooError.SystemHalted(rebuild(v.envelope(),"
                  + " edit));")
          // No live clock anywhere except the explicit system() convenience delegation.
          .doesNotContain("Instant.now()");
    }

    @Test
    @DisplayName("generated timestamps come only from the passed TimeSource")
    void noDirectInstantNow() {
      Compilation compilation = compile(CONTEXT, FOO_ERROR);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.FooErrors");
      Assertions.assertThat(generated).doesNotContain("Instant.now").doesNotContain("Clock.system");
    }

    @Test
    @DisplayName("acronym names, primitive domain components and a mid-record envelope all work")
    void acronymsPrimitivesAndMiddleEnvelope() {
      Compilation compilation =
          compile(
              CONTEXT,
              errorSource(
                  "BarError",
                  """
                  @GenerateErrorEnvelope
                  public sealed interface BarError {
                    record DBTimeout(
                        String stage, ErrorEnvelope<FooErrorContext> envelope, long millis)
                        implements BarError {}

                    record XRayLost(ErrorEnvelope<FooErrorContext> envelope) implements BarError {}
                  }
                  """));
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.BarErrors");
      Assertions.assertThat(generated)
          // Acronym runs stay together in the code and keep their case in the message.
          .contains("ErrorEnvelope.of(time, \"DB_TIMEOUT\", \"DB timeout\", ABSENT_CONTEXT)")
          .contains("ErrorEnvelope.of(time, \"X_RAY_LOST\", \"X ray lost\", ABSENT_CONTEXT)")
          // Primitive domain components pass straight through the factories.
          .contains("return dBTimeout(TimeSource.system(), stage, millis)")
          // The envelope keeps its mid-record position in factory and wither.
          .contains(
              "case BarError.DBTimeout v -> new BarError.DBTimeout(v.stage(),"
                  + " rebuild(v.envelope(), edit), v.millis());");
    }
  }

  @Nested
  @DisplayName("Happy path: generated behaviour")
  class HappyPathBehaviour {

    @Test
    @DisplayName("the timed factory fills code, message, frozen timestamp and absent context")
    void timedFactoryFillsEnvelope() {
      Compilation compilation = compile(CONTEXT, FOO_ERROR);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object error =
            result.invokeStatic(
                "com.example.FooErrors", "outOfStock", TimeSource.fixed(FROZEN), List.of("sku-1"));
        ErrorEnvelope<?> envelope = (ErrorEnvelope<?>) invoke(error, "envelope");
        Assertions.assertThat(envelope.code()).isEqualTo("OUT_OF_STOCK");
        Assertions.assertThat(envelope.message()).isEqualTo("Out of stock");
        Assertions.assertThat(envelope.timestamp()).isEqualTo(FROZEN);
        Assertions.assertThat(invoke(envelope.context(), "orderId")).isNull();
        Assertions.assertThat(invoke(envelope.context(), "traceId")).isNull();
        Assertions.assertThat(invoke(error, "products")).isEqualTo(List.of("sku-1"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("the convenience factory stamps from the system clock")
    void convenienceFactoryUsesSystemClock() {
      Compilation compilation = compile(CONTEXT, FOO_ERROR);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object error =
            result.invokeStatic("com.example.FooErrors", "paymentDeclined", "card-1", "expired");
        ErrorEnvelope<?> envelope = (ErrorEnvelope<?>) invoke(error, "envelope");
        Assertions.assertThat(envelope.code()).isEqualTo("PAYMENT_DECLINED");
        // Generous tolerance: the system clock is not monotonic (see TimeSourceTest).
        Assertions.assertThat(Duration.between(envelope.timestamp(), Instant.now()).abs())
            .isLessThan(Duration.ofMinutes(1));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("the context builder sets components fluently; unset ones stay absent")
    void contextBuilderBuildsTypedContext() {
      Compilation compilation = compile(CONTEXT, FOO_ERROR);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object builder = result.invokeStatic("com.example.FooErrors", "context");
        Object context = invoke(invoke(builder, "orderId", "o-1"), "build");
        Assertions.assertThat(invoke(context, "orderId")).isEqualTo("o-1");
        Assertions.assertThat(invoke(context, "traceId")).isNull();
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("editContext transforms the context and preserves everything else")
    void witherTransformsContextOnly() {
      Compilation compilation = compile(CONTEXT, FOO_ERROR);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object error =
            result.invokeStatic(
                "com.example.FooErrors", "outOfStock", TimeSource.fixed(FROZEN), List.of("sku-1"));
        UnaryOperator<Object> edit =
            builder -> invoke(invoke(builder, "orderId", "o-42"), "traceId", "t-7");
        Object updated = result.invokeStatic("com.example.FooErrors", "editContext", error, edit);
        Assertions.assertThat(updated.getClass().getSimpleName()).isEqualTo("OutOfStock");
        ErrorEnvelope<?> envelope = (ErrorEnvelope<?>) invoke(updated, "envelope");
        Assertions.assertThat(envelope.code()).isEqualTo("OUT_OF_STOCK");
        Assertions.assertThat(envelope.message()).isEqualTo("Out of stock");
        Assertions.assertThat(envelope.timestamp()).isEqualTo(FROZEN);
        Assertions.assertThat(invoke(envelope.context(), "orderId")).isEqualTo("o-42");
        Assertions.assertThat(invoke(envelope.context(), "traceId")).isEqualTo("t-7");
        Assertions.assertThat(invoke(updated, "products")).isEqualTo(List.of("sku-1"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("the issue's instance chaining compiles through a one-line default method")
    void instanceChainingDefaultMethodBehaves() {
      Compilation compilation = compile(CONTEXT, FOO_ERROR);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object error =
            result.invokeStatic(
                "com.example.FooErrors", "outOfStock", TimeSource.fixed(FROZEN), List.of("sku-1"));
        UnaryOperator<Object> edit = builder -> invoke(builder, "orderId", "o-9");
        // error.editContext(ctx -> ctx.orderId("o-9")) - the issue-sketch call shape.
        Object updated = invoke(error, "editContext", edit);
        ErrorEnvelope<?> envelope = (ErrorEnvelope<?>) invoke(updated, "envelope");
        Assertions.assertThat(invoke(envelope.context(), "orderId")).isEqualTo("o-9");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }
  }

  @Nested
  @DisplayName("What/why/fix diagnostics")
  class DiagnosticsTests {

    @Test
    @DisplayName("a class is rejected: only sealed interfaces declare the variant set")
    void classRejected() {
      Compilation compilation =
          compile(
              errorSource(
                  "NotAnInterface",
                  """
                  @GenerateErrorEnvelope
                  public class NotAnInterface {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "can only be applied to sealed interfaces, but 'NotAnInterface' is a class");
      assertThat(compilation).hadErrorContaining("closed set of permitted record variants");
    }

    @Test
    @DisplayName("an open interface is rejected: no permits, no companion")
    void nonSealedInterfaceRejected() {
      Compilation compilation =
          compile(
              errorSource(
                  "OpenError",
                  """
                  @GenerateErrorEnvelope
                  public interface OpenError {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'OpenError' is an interface but is not sealed");
      assertThat(compilation)
          .hadErrorContaining(
              "Add the 'sealed' modifier and make every permitted variant a" + " record");
    }

    @Test
    @DisplayName("a class variant is rejected: factories need record components")
    void nonRecordVariantRejected() {
      Compilation compilation =
          compile(
              CONTEXT,
              errorSource(
                  "ClassVariantError",
                  """
                  @GenerateErrorEnvelope
                  public sealed interface ClassVariantError {
                    ErrorEnvelope<FooErrorContext> envelope();

                    final class Halted implements ClassVariantError {
                      public ErrorEnvelope<FooErrorContext> envelope() {
                        return null;
                      }
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "permitted variant 'Halted' of 'ClassVariantError' is a class, not a record");
      assertThat(compilation).hadErrorContaining("Make 'Halted' a record");
    }

    @Test
    @DisplayName("a non-sealed interface variant is rejected through the same non-record arm")
    void nonSealedInterfaceVariantRejected() {
      Compilation compilation =
          compile(
              CONTEXT,
              errorSource(
                  "MixedError",
                  """
                  @GenerateErrorEnvelope
                  public sealed interface MixedError {
                    non-sealed interface Open extends MixedError {}
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "permitted variant 'Open' of 'MixedError' is an interface, not a record");
      assertThat(compilation).hadErrorContaining("Make 'Open' a record");
    }

    @Test
    @DisplayName("a nested sealed sub-hierarchy is rejected with a flatten-it fix")
    void nestedSealedVariantRejected() {
      Compilation compilation =
          compile(
              CONTEXT,
              errorSource(
                  "OuterError",
                  """
                  @GenerateErrorEnvelope
                  public sealed interface OuterError {
                    sealed interface Inner extends OuterError {
                      record Leaf(ErrorEnvelope<FooErrorContext> envelope) implements Inner {}
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "permitted variant 'Inner' of 'OuterError' is a nested sealed interface, not a"
                  + " record");
      assertThat(compilation).hadErrorContaining("does not recurse into sub-hierarchies");
      assertThat(compilation)
          .hadErrorContaining(
              "make the leaf records of 'Inner' direct permitted variants of 'OuterError'");
    }

    @Test
    @DisplayName("a generic hierarchy is rejected: the companion names it directly")
    void genericHierarchyRejected() {
      Compilation compilation =
          compile(
              CONTEXT,
              errorSource(
                  "GenericError",
                  """
                  @GenerateErrorEnvelope
                  public sealed interface GenericError<T> {
                    record Oops(ErrorEnvelope<FooErrorContext> envelope)
                        implements GenericError<String> {}
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("'GenericError' is generic, which this companion does not support");
      assertThat(compilation).hadErrorContaining("Declare the hierarchy without type parameters");
    }

    @Test
    @DisplayName("a generic variant record is rejected: the switch would go raw")
    void genericVariantRejected() {
      Compilation compilation =
          compile(
              CONTEXT,
              errorSource(
                  "GenericVariantError",
                  """
                  @GenerateErrorEnvelope
                  public sealed interface GenericVariantError {
                    record Boxed<T>(T value, ErrorEnvelope<FooErrorContext> envelope)
                        implements GenericVariantError {}
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "permitted variant 'Boxed' of 'GenericVariantError' is generic, which this companion"
                  + " does not support");
      assertThat(compilation).hadErrorContaining("Declare 'Boxed' with concrete component types");
    }

    @Test
    @DisplayName("a generic context record is rejected: builder fields would be type variables")
    void genericContextRejected() {
      JavaFileObject boxContext =
          JavaFileObjects.forSourceString(
              "com.example.BoxContext",
              """
              package com.example;

              public record BoxContext<T>(T value) {}
              """);
      Compilation compilation =
          compile(
              boxContext,
              errorSource(
                  "BoxedContextError",
                  """
                  @GenerateErrorEnvelope
                  public sealed interface BoxedContextError {
                    record Oops(ErrorEnvelope<BoxContext<String>> envelope)
                        implements BoxedContextError {}
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "context type 'com.example.BoxContext<java.lang.String>' is generic, which this"
                  + " companion does not support");
      assertThat(compilation).hadErrorContaining("Use a concrete (non-generic) context record");
    }

    @Test
    @DisplayName("a primitive context component is rejected: the all-absent instance holds nulls")
    void primitiveContextComponentRejected() {
      JavaFileObject countContext =
          JavaFileObjects.forSourceString(
              "com.example.CountContext",
              """
              package com.example;

              public record CountContext(String orderId, int retries) {}
              """);
      Compilation compilation =
          compile(
              countContext,
              errorSource(
                  "CountError",
                  """
                  @GenerateErrorEnvelope
                  public sealed interface CountError {
                    record Oops(ErrorEnvelope<CountContext> envelope) implements CountError {}
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("context component 'retries' of 'CountContext' is a primitive int");
      assertThat(compilation)
          .hadErrorContaining("Declare the component as 'Integer' or another reference type");
    }

    @Test
    @DisplayName("a wildcard context argument is rejected: no concrete schema to read")
    void wildcardContextArgumentRejected() {
      Compilation compilation =
          compile(
              CONTEXT,
              errorSource(
                  "WildError",
                  """
                  @GenerateErrorEnvelope
                  public sealed interface WildError {
                    record Woolly(ErrorEnvelope<?> envelope) implements WildError {}
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "the ErrorEnvelope component on 'Woolly' does not declare a usable context type"
                  + " argument");
    }

    @Test
    @DisplayName("an empty sealed hierarchy adds no diagnostic: javac already rejects it")
    void emptyHierarchyAddsNoDiagnostic() {
      Compilation compilation =
          compile(
              errorSource(
                  "EmptyError",
                  """
                  @GenerateErrorEnvelope
                  public sealed interface EmptyError {}
                  """));
      assertThat(compilation).failed();
      Assertions.assertThat(compilation.errors())
          .noneMatch(diagnostic -> diagnostic.getMessage(null).contains("@GenerateErrorEnvelope"));
    }

    @Test
    @DisplayName("a hand-written type on the companion name gets the collision diagnostic")
    void companionNameCollisionReported() {
      JavaFileObject clash =
          JavaFileObjects.forSourceString(
              "com.example.FooErrors",
              """
              package com.example;

              public final class FooErrors {}
              """);
      Compilation compilation = compile(CONTEXT, FOO_ERROR, clash);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "could not write the generated companion for 'FooError': the class already exists");
      assertThat(compilation).hadErrorContaining("Rename the hierarchy or the clashing type");
    }

    @Test
    @DisplayName("a non-collision IOException reports the what/why/fix write failure")
    void ioExceptionReportsWriteFailure() {
      List<String> messages = new java.util.ArrayList<>();
      var messager =
          (javax.annotation.processing.Messager)
              java.lang.reflect.Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {javax.annotation.processing.Messager.class},
                  (proxy, method, args) -> {
                    if ("printMessage".equals(method.getName())) {
                      messages.add(String.valueOf(args[1]));
                    }
                    return null;
                  });
      var filer =
          (javax.annotation.processing.Filer)
              java.lang.reflect.Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {javax.annotation.processing.Filer.class},
                  (proxy, method, args) -> {
                    throw new IOException("disk full");
                  });
      var env =
          (javax.annotation.processing.ProcessingEnvironment)
              java.lang.reflect.Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {javax.annotation.processing.ProcessingEnvironment.class},
                  (proxy, method, args) ->
                      switch (method.getName()) {
                        case "getFiler" -> filer;
                        case "getMessager" -> messager;
                        default -> null;
                      });
      var simpleName =
          (javax.lang.model.element.Name)
              java.lang.reflect.Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {javax.lang.model.element.Name.class},
                  (proxy, method, args) ->
                      "toString".equals(method.getName()) ? "BrokenError" : null);
      var ifaceElement =
          (javax.lang.model.element.TypeElement)
              java.lang.reflect.Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {javax.lang.model.element.TypeElement.class},
                  (proxy, method, args) ->
                      "getSimpleName".equals(method.getName()) ? simpleName : null);

      ErrorEnvelopeProcessor processor = new ErrorEnvelopeProcessor();
      processor.init(env);
      processor.writeFile(
          ifaceElement,
          "com.example",
          com.palantir.javapoet.TypeSpec.classBuilder("Broken").build());

      Assertions.assertThat(messages)
          .singleElement()
          .asString()
          .contains("could not write the generated companion for 'BrokenError'")
          .contains("disk full")
          .contains("Check build-output permissions and free disk space");
    }

    @Test
    @DisplayName("a variant without the envelope component is rejected")
    void missingEnvelopeComponentRejected() {
      Compilation compilation =
          compile(
              CONTEXT,
              errorSource(
                  "NoEnvelopeError",
                  """
                  @GenerateErrorEnvelope
                  public sealed interface NoEnvelopeError {
                    record Bare(String reason) implements NoEnvelopeError {}
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "variant 'Bare' of 'NoEnvelopeError' declares no ErrorEnvelope component; exactly"
                  + " one is required");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare exactly one 'ErrorEnvelope<YourContext> envelope' component");
    }

    @Test
    @DisplayName("a variant with two envelope components is rejected")
    void doubleEnvelopeComponentRejected() {
      Compilation compilation =
          compile(
              CONTEXT,
              errorSource(
                  "DoubleEnvelopeError",
                  """
                  @GenerateErrorEnvelope
                  public sealed interface DoubleEnvelopeError {
                    record Twice(
                        ErrorEnvelope<FooErrorContext> first, ErrorEnvelope<FooErrorContext> second)
                        implements DoubleEnvelopeError {}
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "variant 'Twice' of 'DoubleEnvelopeError' declares 2 ErrorEnvelope" + " components");
    }

    @Test
    @DisplayName("variants disagreeing on the context type are rejected")
    void contextDisagreementRejected() {
      JavaFileObject otherContext =
          JavaFileObjects.forSourceString(
              "com.example.OtherContext",
              """
              package com.example;

              public record OtherContext(String node) {}
              """);
      Compilation compilation =
          compile(
              CONTEXT,
              otherContext,
              errorSource(
                  "SplitError",
                  """
                  @GenerateErrorEnvelope
                  public sealed interface SplitError {
                    record A(ErrorEnvelope<FooErrorContext> envelope) implements SplitError {}

                    record B(ErrorEnvelope<OtherContext> envelope) implements SplitError {}
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("variants of 'SplitError' disagree on the context type");
      assertThat(compilation)
          .hadErrorContaining(
              "Use the same context record in every variant's ErrorEnvelope" + " component");
    }

    @Test
    @DisplayName(
        "a raw envelope component is rejected: the context is read from the type" + " argument")
    void rawEnvelopeComponentRejected() {
      Compilation compilation =
          compile(
              CONTEXT,
              errorSource(
                  "RawEnvelopeError",
                  """
                  @SuppressWarnings("rawtypes")
                  @GenerateErrorEnvelope
                  public sealed interface RawEnvelopeError {
                    record Untyped(ErrorEnvelope envelope) implements RawEnvelopeError {}
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "the ErrorEnvelope component on 'Untyped' does not declare a usable context type"
                  + " argument");
      assertThat(compilation).hadErrorContaining("never from a class-literal attribute");
    }

    @Test
    @DisplayName("a non-record context type is rejected: records-as-schema")
    void nonRecordContextRejected() {
      Compilation compilation =
          compile(
              errorSource(
                  "StringContextError",
                  """
                  @GenerateErrorEnvelope
                  public sealed interface StringContextError {
                    record Oops(ErrorEnvelope<String> envelope) implements StringContextError {}
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("context type 'java.lang.String' is not a record");
      assertThat(compilation)
          .hadErrorContaining("Declare the context as a record with nullable components");
    }
  }
}
