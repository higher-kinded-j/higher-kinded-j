// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.higherkindedj.openrewrite.NullLiterals.isNullLiteral;
import static org.higherkindedj.openrewrite.NullLiterals.returnsNull;

import java.util.List;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/**
 * Recipe that marks the code a null success breaks from 0.5.0, where a {@code Right} or a {@code
 * Success} always holds a value, and a lazy computation always gives one.
 *
 * <h2>Detection Pattern</h2>
 *
 * <ul>
 *   <li>A {@code null} literal passed as the value to {@code Either.right}, {@code Try.success},
 *       {@code EitherT.right}, a lazy factory such as {@code VTask.succeed}, {@code State.pure},
 *       {@code Lazy.now} or {@code Context.succeed}, its {@code KindHelper} or context form, an
 *       {@code iterate} seed, the {@code StateTuple} or {@code Writer} constructor, or a {@code
 *       Path} factory such as {@code Path.right}, {@code Path.ioPure} and {@code Path.id}. These
 *       throw {@code NullPointerException}.
 *   <li>A {@code null} element given to {@code VStream.of}, {@code Path.vstreamOf} or {@code
 *       StreamPath.of}, since a stream holds no null element.
 *   <li>A lambda given to {@code Try.of}, {@code Try.attempt}, {@code Path.tryOf}, {@code
 *       TryKindHelper.tryOf} or the {@code map} of a {@code Try} or {@code TryPath}, that returns a
 *       {@code null} literal. It gives a {@code Failure} rather than a {@code Success}.
 *   <li>A lambda given to a deferred factory, such as {@code VTask.of}, {@code IO.delay}, {@code
 *       Lazy.defer} or {@code Path.io}, or to the {@code map} of a lazy type or a lazy {@code
 *       Path}, that returns a {@code null} literal. The computation fails when it runs.
 *   <li>A lambda given to the {@code map} of an {@code Either}, {@code EitherPath}, {@code Writer},
 *       {@code WriterPath} or {@code IdPath} that returns a {@code null} literal. It throws at the
 *       call. The {@code map} of a {@code Maybe}, an {@code Optional} or a list is left alone,
 *       since there a null result means empty or stays an element.
 *   <li>A {@code CompletableFuture<Void>} given to {@code Path.future} or {@code
 *       CompletableFuturePath.fromFuture}, which completes with null and so fails the path.
 *   <li>An {@code Either}, {@code Try} or {@code EitherT}, a lazy type such as {@code VTask},
 *       {@code IO}, {@code VStream}, {@code State}, {@code Lazy} or {@code Context}, or a {@code
 *       Path} or context over one, whose value type is {@code Void}, which has no value to hold.
 * </ul>
 *
 * <p>Where the value's type is {@code Void}, or unknown, the mark suggests {@code Unit}; where it
 * is another type, it suggests a {@code Maybe} or an error that says why the value is missing.
 *
 * <h2>Why this is detection-only</h2>
 *
 * <p>The replacement is {@code Unit}, but changing {@code Void} to {@code Unit} changes a method's
 * signature for every caller and implementer, and a lambda can return {@code null} from anywhere in
 * its body. So this recipe marks each site for a human to change, rather than risking a rewrite
 * that compiles and then fails when it runs.
 */
public class DetectNullSuccessValuesRecipe extends Recipe {

  /** Creates a new instance of this recipe. */
  public DetectNullSuccessValuesRecipe() {}

  private static final List<MethodMatcher> SUCCESS_FACTORIES =
      List.of(
          new MethodMatcher("org.higherkindedj.hkt.either.Either right(..)"),
          new MethodMatcher("org.higherkindedj.hkt.trymonad.Try success(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path right(*)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path success(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path vresultRight(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.VResultPath pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.trymonad.TryKindHelper success(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.context.ErrorContext success(..)"),
          new MethodMatcher("org.higherkindedj.hkt.either_t.EitherT right(..)"),
          new MethodMatcher("org.higherkindedj.hkt.vtask.VTask succeed(..)"),
          new MethodMatcher("org.higherkindedj.hkt.vstream.VStream succeed(..)"),
          new MethodMatcher("org.higherkindedj.hkt.vstream.VStream repeat(..)"),
          new MethodMatcher("org.higherkindedj.hkt.state.State pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.state.StateTuple of(..)"),
          new MethodMatcher("org.higherkindedj.hkt.reader.Reader constant(..)"),
          new MethodMatcher("org.higherkindedj.hkt.writer.Writer value(..)"),
          new MethodMatcher("org.higherkindedj.hkt.writer.Writer of(..)"),
          new MethodMatcher("org.higherkindedj.hkt.lazy.Lazy now(..)"),
          new MethodMatcher("org.higherkindedj.hkt.trampoline.Trampoline done(..)"),
          new MethodMatcher("org.higherkindedj.hkt.free.Free pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.free_ap.FreeAp pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path ioPure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path vtaskPure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path vstreamPure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path readerPure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path statePure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path lazyNow(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path trampolineDone(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path futureCompleted(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path streamPure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path id(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.LazyPath now(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.TrampolinePath done(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.TrampolinePath pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.ReaderPath pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.WithStatePath pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.StreamPath pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.CompletableFuturePath completed(..)"),
          new MethodMatcher("org.higherkindedj.hkt.vtask.VTaskKindHelper succeed(..)"),
          new MethodMatcher("org.higherkindedj.hkt.lazy.LazyKindHelper now(..)"),
          new MethodMatcher("org.higherkindedj.hkt.state.StateKindHelper pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.reader.ReaderKindHelper constant(..)"),
          new MethodMatcher("org.higherkindedj.hkt.writer.WriterKindHelper value(..)"),
          new MethodMatcher("org.higherkindedj.hkt.trampoline.TrampolineKindHelper done(..)"),
          new MethodMatcher("org.higherkindedj.hkt.context.Context succeed(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.context.VTaskContext pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.context.VStreamContext pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.context.ConfigContext pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.context.MutableContext pure(..)"));

  /** The factories that take the value first, before a monoid, a functor or a step function. */
  private static final List<MethodMatcher> VALUE_FIRST_FACTORIES =
      List.of(
          new MethodMatcher("org.higherkindedj.hkt.vstream.VStream iterate(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path vstreamIterate(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path streamIterate(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.StreamPath iterate(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path writerPure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path freePure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path freeApPure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.WriterPath pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.FreePath pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.FreeApPath pure(..)"));

  /** The factories that take their elements as arguments, any of which may be the null. */
  private static final List<MethodMatcher> ELEMENT_FACTORIES =
      List.of(
          new MethodMatcher("org.higherkindedj.hkt.vstream.VStream of(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path vstreamOf(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.StreamPath of(..)"));

  private static final List<MethodMatcher> CAPTURING_FACTORIES =
      List.of(
          new MethodMatcher("org.higherkindedj.hkt.trymonad.Try of(..)"),
          new MethodMatcher("org.higherkindedj.hkt.trymonad.Try attempt(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path tryOf(..)"),
          new MethodMatcher("org.higherkindedj.hkt.trymonad.TryKindHelper tryOf(..)"),
          new MethodMatcher("org.higherkindedj.hkt.trymonad.Try map(..)", true),
          new MethodMatcher("org.higherkindedj.hkt.effect.TryPath map(..)"));

  /** The constructors that take the value first, before the state. */
  private static final List<MethodMatcher> VALUE_FIRST_CONSTRUCTORS =
      List.of(new MethodMatcher("org.higherkindedj.hkt.state.StateTuple <constructor>(..)"));

  /** The constructors that take the value last, after the log. */
  private static final List<MethodMatcher> VALUE_LAST_CONSTRUCTORS =
      List.of(new MethodMatcher("org.higherkindedj.hkt.writer.Writer <constructor>(..)"));

  /** The factories that wrap a JDK future, which completes with null when it is typed Void. */
  private static final List<MethodMatcher> FUTURE_FACTORIES =
      List.of(
          new MethodMatcher("org.higherkindedj.hkt.effect.Path future(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.CompletableFuturePath fromFuture(..)"));

  /**
   * The deferred factories and the {@code map} of each lazy type and lazy {@code Path}, each taking
   * the function that gives the value as its first argument.
   */
  private static final List<MethodMatcher> DEFERRED_FACTORIES =
      List.of(
          new MethodMatcher("org.higherkindedj.hkt.vtask.VTask of(..)"),
          new MethodMatcher("org.higherkindedj.hkt.vtask.VTask delay(..)"),
          new MethodMatcher("org.higherkindedj.hkt.vtask.VTask blocking(..)"),
          new MethodMatcher("org.higherkindedj.hkt.io.IO delay(..)"),
          new MethodMatcher("org.higherkindedj.hkt.lazy.Lazy defer(..)"),
          new MethodMatcher("org.higherkindedj.hkt.reader.Reader of(..)"),
          new MethodMatcher("org.higherkindedj.hkt.state.State inspect(..)"),
          new MethodMatcher("org.higherkindedj.hkt.vstream.VStream generate(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path io(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path vtask(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path lazyDefer(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path futureAsync(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path vstreamGenerate(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.LazyPath defer(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.CompletableFuturePath supplyAsync(..)"),
          new MethodMatcher("org.higherkindedj.hkt.vtask.VTask map(..)", true),
          new MethodMatcher("org.higherkindedj.hkt.io.IO map(..)", true),
          new MethodMatcher("org.higherkindedj.hkt.vstream.VStream map(..)", true),
          new MethodMatcher("org.higherkindedj.hkt.state.State map(..)", true),
          new MethodMatcher("org.higherkindedj.hkt.reader.Reader map(..)", true),
          new MethodMatcher("org.higherkindedj.hkt.lazy.Lazy map(..)"),
          new MethodMatcher("org.higherkindedj.hkt.trampoline.Trampoline map(..)", true),
          new MethodMatcher("org.higherkindedj.hkt.free.Free map(..)", true),
          new MethodMatcher("org.higherkindedj.hkt.free_ap.FreeAp map(..)", true),
          new MethodMatcher("org.higherkindedj.hkt.context.Context map(..)", true),
          new MethodMatcher("org.higherkindedj.hkt.effect.IOPath map(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.VTaskPath map(..)", true),
          new MethodMatcher("org.higherkindedj.hkt.effect.VStreamPath map(..)", true),
          new MethodMatcher("org.higherkindedj.hkt.effect.LazyPath map(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.ReaderPath map(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.WithStatePath map(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.TrampolinePath map(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.FreePath map(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.FreeApPath map(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.CompletableFuturePath map(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.StreamPath map(..)"));

  /**
   * The {@code map} of each strict type that throws at the call when its function returns null. The
   * {@code map} of a {@code Maybe}, an {@code Optional} or a list is not here: there a null result
   * means empty, or stays an element.
   */
  private static final List<MethodMatcher> EAGER_MAPPERS =
      List.of(
          new MethodMatcher("org.higherkindedj.hkt.either.Either map(..)", true),
          new MethodMatcher("org.higherkindedj.hkt.effect.EitherPath map(..)"),
          new MethodMatcher("org.higherkindedj.hkt.writer.Writer map(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.WriterPath map(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.IdPath map(..)"));

  private static final Set<String> SUCCESS_TYPES =
      Set.of(
          "org.higherkindedj.hkt.either.Either",
          "org.higherkindedj.hkt.trymonad.Try",
          "org.higherkindedj.hkt.either_t.EitherT",
          "org.higherkindedj.hkt.effect.EitherPath",
          "org.higherkindedj.hkt.effect.TryPath",
          "org.higherkindedj.hkt.effect.VResultPath",
          "org.higherkindedj.hkt.effect.context.ErrorContext",
          "org.higherkindedj.hkt.vtask.VTask",
          "org.higherkindedj.hkt.vtask.Resource",
          "org.higherkindedj.hkt.effect.VTaskPath",
          "org.higherkindedj.hkt.io.IO",
          "org.higherkindedj.hkt.effect.IOPath",
          "org.higherkindedj.hkt.vstream.VStream",
          "org.higherkindedj.hkt.effect.VStreamPath",
          "org.higherkindedj.hkt.effect.StreamPath",
          "org.higherkindedj.hkt.lazy.Lazy",
          "org.higherkindedj.hkt.effect.LazyPath",
          "org.higherkindedj.hkt.state.State",
          "org.higherkindedj.hkt.state.StateTuple",
          "org.higherkindedj.hkt.state_t.StateT",
          "org.higherkindedj.hkt.effect.WithStatePath",
          "org.higherkindedj.hkt.reader.Reader",
          "org.higherkindedj.hkt.reader_t.ReaderT",
          "org.higherkindedj.hkt.effect.ReaderPath",
          "org.higherkindedj.hkt.writer.Writer",
          "org.higherkindedj.hkt.writer_t.WriterT",
          "org.higherkindedj.hkt.effect.WriterPath",
          "org.higherkindedj.hkt.trampoline.Trampoline",
          "org.higherkindedj.hkt.effect.TrampolinePath",
          "org.higherkindedj.hkt.free.Free",
          "org.higherkindedj.hkt.effect.FreePath",
          "org.higherkindedj.hkt.free_ap.FreeAp",
          "org.higherkindedj.hkt.effect.FreeApPath",
          "org.higherkindedj.hkt.effect.CompletableFuturePath",
          "org.higherkindedj.hkt.effect.IdPath",
          "org.higherkindedj.hkt.context.Context",
          "org.higherkindedj.hkt.effect.context.VTaskContext",
          "org.higherkindedj.hkt.effect.context.VStreamContext",
          "org.higherkindedj.hkt.effect.context.ConfigContext",
          "org.higherkindedj.hkt.effect.context.MutableContext",
          "org.higherkindedj.hkt.resilience.Saga");

  static final String NULL_VALUE =
      "A success always holds a value: pass Unit.INSTANCE for a step with nothing to return";

  static final String NULL_TYPED_VALUE =
      "A success always holds a value: use a Maybe for a value that may be missing, or an error"
          + " that says why";

  static final String NULL_SUPPLIER =
      "Returns null, which now gives a Failure: return Unit.INSTANCE for a step with nothing to"
          + " return";

  static final String NULL_TYPED_SUPPLIER =
      "Returns null, which now gives a Failure: return a value, or fail with an error that says"
          + " why";

  static final String NULL_DEFERRED =
      "Returns null, which now fails when it runs: return Unit.INSTANCE for a step with nothing to"
          + " return";

  static final String NULL_TYPED_DEFERRED =
      "Returns null, which now fails when it runs: return a value, or fail with an error that says"
          + " why";

  static final String NULL_EAGER =
      "Returns null, which now throws: return Unit.INSTANCE for a step with nothing to return";

  static final String NULL_TYPED_EAGER =
      "Returns null, which now throws: return a value, or use a Maybe for one that may be missing";

  static final String NULL_ELEMENT = "A stream holds no null element: leave it out, or use a Maybe";

  static final String VOID_FUTURE =
      "A future with nothing to return completes with null, which now fails the path: map it to"
          + " Unit with thenApply(_ -> Unit.INSTANCE) first";

  static final String VOID_TYPE = "Void has no value for a success to hold: use Unit";

  @Override
  public String getDisplayName() {
    return "Detect null success values";
  }

  @Override
  public String getDescription() {
    return "Marks a null passed to Either.right, Try.success, the lazy value factories and the Path"
        + " factories, a null stream element, a Try.of supplier or a deferred or map lambda that"
        + " returns null, a CompletableFuture<Void> wrapped in a Path, and an Either, Try, lazy"
        + " type or Path typed with Void. From 0.5.0 a Right or a Success always holds a value,"
        + " a lazy computation always gives one, and Unit replaces Void.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "migration", "null");
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
        List<Expression> args = mi.getArguments();
        if ((SUCCESS_FACTORIES.stream().anyMatch(m -> m.matches(mi))
                && isNullLiteral(args.getLast()))
            || (VALUE_FIRST_FACTORIES.stream().anyMatch(m -> m.matches(mi))
                && isNullLiteral(args.getFirst()))) {
          return SearchResult.found(
              mi, holdsVoid(mi.getMethodType()) ? NULL_VALUE : NULL_TYPED_VALUE);
        }
        if (ELEMENT_FACTORIES.stream().anyMatch(m -> m.matches(mi))
            && args.stream().anyMatch(NullLiterals::isNullLiteral)) {
          return SearchResult.found(mi, NULL_ELEMENT);
        }
        if (FUTURE_FACTORIES.stream().anyMatch(m -> m.matches(mi))
            && isVoidFuture(args.getFirst().getType())) {
          return SearchResult.found(mi, VOID_FUTURE);
        }
        if (args.isEmpty()
            || !(args.getFirst() instanceof J.Lambda lambda)
            || !returnsNull(lambda)) {
          return mi;
        }
        boolean wantsUnit = holdsVoid(mi.getMethodType());
        if (CAPTURING_FACTORIES.stream().anyMatch(m -> m.matches(mi))) {
          return SearchResult.found(mi, wantsUnit ? NULL_SUPPLIER : NULL_TYPED_SUPPLIER);
        }
        if (DEFERRED_FACTORIES.stream().anyMatch(m -> m.matches(mi))) {
          return SearchResult.found(mi, wantsUnit ? NULL_DEFERRED : NULL_TYPED_DEFERRED);
        }
        if (EAGER_MAPPERS.stream().anyMatch(m -> m.matches(mi))) {
          return SearchResult.found(mi, wantsUnit ? NULL_EAGER : NULL_TYPED_EAGER);
        }
        return mi;
      }

      @Override
      public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
        J.NewClass nc = super.visitNewClass(newClass, ctx);
        List<Expression> args = nc.getArguments();
        if ((VALUE_FIRST_CONSTRUCTORS.stream().anyMatch(m -> m.matches(nc))
                && isNullLiteral(args.getFirst()))
            || (VALUE_LAST_CONSTRUCTORS.stream().anyMatch(m -> m.matches(nc))
                && isNullLiteral(args.getLast()))) {
          return SearchResult.found(nc, holdsVoid(nc.getType()) ? NULL_VALUE : NULL_TYPED_VALUE);
        }
        return nc;
      }

      @Override
      public J.ParameterizedType visitParameterizedType(
          J.ParameterizedType type, ExecutionContext ctx) {
        J.ParameterizedType pt = super.visitParameterizedType(type, ctx);
        JavaType.FullyQualified raw = TypeUtils.asFullyQualified(pt.getType());
        List<Expression> params = pt.getTypeParameters();
        if (raw == null
            || params == null
            || params.isEmpty()
            || !TypeUtils.isOfClassType(params.getLast().getType(), "java.lang.Void")) {
          return pt;
        }
        if (SUCCESS_TYPES.contains(raw.getFullyQualifiedName())) {
          return SearchResult.found(pt, VOID_TYPE);
        }
        return pt;
      }
    };
  }

  /** Whether a type is a {@code CompletableFuture<Void>}, which completes with null. */
  private static boolean isVoidFuture(JavaType type) {
    return type instanceof JavaType.Parameterized future
        && TypeUtils.isOfClassType(future, "java.util.concurrent.CompletableFuture")
        && !future.getTypeParameters().isEmpty()
        && TypeUtils.isOfClassType(future.getTypeParameters().getLast(), "java.lang.Void");
  }

  /**
   * Whether a method's result holds {@code Void} or {@code Unit}, or a type the parser could not
   * resolve, so {@code Unit} is the advice that fits.
   */
  private static boolean holdsVoid(JavaType.Method method) {
    return method == null || holdsVoid(method.getReturnType());
  }

  /**
   * Whether a type's last type argument is {@code Void} or {@code Unit}, or a type the parser could
   * not resolve, so {@code Unit} is the advice that fits.
   */
  private static boolean holdsVoid(JavaType type) {
    if (!(type instanceof JavaType.Parameterized parameterized)
        || parameterized.getTypeParameters().isEmpty()) {
      return true;
    }
    JavaType value = parameterized.getTypeParameters().getLast();
    return TypeUtils.isOfClassType(value, "java.lang.Void")
        || TypeUtils.isOfClassType(value, "org.higherkindedj.hkt.Unit")
        || value instanceof JavaType.Unknown;
  }
}
