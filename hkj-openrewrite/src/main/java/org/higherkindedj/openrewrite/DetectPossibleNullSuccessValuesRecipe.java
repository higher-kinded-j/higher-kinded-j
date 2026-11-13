// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe.CAPTURING_FACTORIES;
import static org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe.DEFERRED_FACTORIES;
import static org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe.EAGER_MAPPERS;
import static org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe.SUCCESS_TYPES;
import static org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe.valueArguments;
import static org.higherkindedj.openrewrite.NullLiterals.bare;
import static org.higherkindedj.openrewrite.NullLiterals.isNullLiteral;
import static org.higherkindedj.openrewrite.NullLiterals.markReturned;
import static org.higherkindedj.openrewrite.SwapTryFoldToFoldFailureFirstRecipe.isSuccessFirstFold;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/**
 * Recipe that marks a value that may reach a success as null, which 0.5.0 refuses, where no {@code
 * null} is written for {@link DetectNullSuccessValuesRecipe} to see.
 *
 * <h2>Detection Pattern</h2>
 *
 * <ul>
 *   <li>A {@code @Nullable} parameter of a wrapper: a method whose body only returns a success
 *       factory given that parameter as its value, as {@code static <T> Either<E, T>
 *       right(@Nullable T value) { return Either.right(value); }} does. The factories are those
 *       {@link DetectNullSuccessValuesRecipe} reads. A method that checks, changes or branches on
 *       the parameter first is not a wrapper.
 *   <li>A {@code null} passed to a wrapper declared in the run, from any file, whether its
 *       parameter is {@code @Nullable} or not. The wrapper hands it straight to the factory, which
 *       throws, so this mark is certain.
 *   <li>A call to a method that may return null, returned by a lambda given to {@code Try.of}, a
 *       deferred factory such as {@code VTask.of}, or the {@code map} of a {@code Try}, a lazy
 *       type, an {@code Either} or a {@code Path}; and a method reference to one, given to the
 *       same. A null there gives a {@code Failure}, fails when the computation runs, or throws.
 *   <li>A null check of a success step's value, in a chain of types whose success always holds a
 *       value that starts at one of those factories or {@code map}s: {@code value == null}, {@code
 *       Objects.isNull(value)} or the like in the function given to {@code map}, {@code flatMap},
 *       {@code via}, {@code peek} or {@code ifRight}, or as the success function of {@code fold} or
 *       {@code foldFailureFirst}. Also a method reference there to a method declared in the run
 *       whose parameter is {@code @Nullable}. 0.5.0 never hands such a step a null, so a value that
 *       used to arrive as null now fails the chain first.
 * </ul>
 *
 * <p>Each mark sits on the value that may be null: the parameter, the call, the method reference or
 * the null check.
 *
 * <h2>Which annotations count</h2>
 *
 * <p>An annotation named {@code Nullable} or {@code CheckForNull} counts, whatever its package. A
 * declaration annotation on a method's result, such as Spring Framework 6's {@code
 * org.springframework.lang.Nullable} or JSR-305's {@code CheckForNull}, is read from any method, a
 * library's included. A type-use annotation, such as JSpecify's, and any annotation on a parameter,
 * are read only from a method declared in the sources the run covers: OpenRewrite's type model
 * carries neither, so the recipe reads them from the declaration. So a call into a library
 * annotated with JSpecify, such as the Spring Data of Spring Boot 4, is not marked. A null check
 * after it, in the same chain, is.
 *
 * <h2>Why this is detection-only</h2>
 *
 * <p>Each fix is a choice: carry the absence as a {@code Maybe}, fail with an error that says why,
 * or pass {@code Unit.INSTANCE}. Most of these marks are heuristics too: a {@code @Nullable} method
 * may never return null where it is called, and a null check may be one no value ever reached. So
 * they say "may", and they are kept out of {@link DetectNullSuccessValuesRecipe}, whose marks are
 * certain. The recipe matches 0.5.0 code as well as 0.4.x code, so a project already on 0.5.0 can
 * run it on its own.
 */
public class DetectPossibleNullSuccessValuesRecipe
    extends ScanningRecipe<DetectPossibleNullSuccessValuesRecipe.Declarations> {

  /** Creates a new instance of this recipe. */
  public DetectPossibleNullSuccessValuesRecipe() {}

  /** The success steps whose function takes the success value as its first argument. */
  private static final Set<String> STEPS = Set.of("map", "flatMap", "via", "peek", "ifRight");

  /** The JDK's null checks that read as a comparison with null. */
  private static final List<MethodMatcher> NULL_CHECKS =
      List.of(
          new MethodMatcher("java.util.Objects isNull(..)"),
          new MethodMatcher("java.util.Objects nonNull(..)"));

  /** The simple names of the annotations that say a value may be null. */
  private static final Set<String> NULLABLE_ANNOTATIONS = Set.of("Nullable", "CheckForNull");

  static final String NULLABLE_WRAPPED =
      "May be null, which %s now refuses: drop @Nullable so callers pass a value, such as"
          + " Unit.INSTANCE or a Maybe, or return an error here when it is null";

  static final String NULL_WRAPPED =
      "Passes null on to %s, which now throws: pass Unit.INSTANCE for a step with nothing to"
          + " return";

  static final String NULL_TYPED_WRAPPED =
      "Passes null on to %s, which now throws: use a Maybe for a value that may be missing, or an"
          + " error that says why";

  static final String NULLABLE_SUPPLIER =
      "May return null, which now gives a Failure: carry a missing value as a Maybe, or fail with an"
          + " error that says why";

  static final String NULLABLE_DEFERRED =
      "May return null, which now fails when it runs: carry a missing value as a Maybe, or fail"
          + " with an error that says why";

  static final String NULLABLE_EAGER =
      "May return null, which now throws: carry a missing value as a Maybe";

  static final String NULL_CHECKED_STEP =
      "Never null here now, so a value that used to arrive as null may fail the chain before this"
          + " check: carry it as a Maybe from the call that returns it";

  static final String NULLABLE_STEP =
      "Accepts null, but is never handed one now, so a value that used to arrive as null may fail"
          + " the chain before this step: carry it as a Maybe from the call that returns it";

  @Override
  public String getDisplayName() {
    return "Detect success values that may be null";
  }

  @Override
  public String getDescription() {
    return "Marks a @Nullable parameter of a wrapper that returns a success factory given it, and"
        + " a null passed to such a wrapper; a call to a method that may return null, returned to"
        + " Try.of, a deferred factory or a map; and a null check, or a @Nullable parameter, in a"
        + " success step that follows one of those. From 0.5.0 a success always holds a value, so"
        + " such a value now fails or throws where 0.4.x passed the null along.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "migration", "null");
  }

  @Override
  public Declarations getInitialValue(ExecutionContext ctx) {
    return new Declarations();
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getScanner(Declarations declarations) {
    return new JavaIsoVisitor<>() {
      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
        if (md.getMethodType() != null && !md.isConstructor()) {
          declarations.add(
              md.getMethodType(), Declared.of(getCursor(), service(AnnotationService.class)));
        }
        return md;
      }
    };
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor(Declarations declarations) {
    return new JavaIsoVisitor<>() {

      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
        if (md.isConstructor()) {
          return md;
        }
        Declared declared = Declared.of(getCursor(), service(AnnotationService.class));
        return md.withParameters(
            ListUtils.map(
                md.getParameters(),
                (index, parameter) ->
                    declared.nullableParameters().contains(index)
                            && declared.wrapped().containsKey(index)
                        ? SearchResult.found(
                            parameter, NULLABLE_WRAPPED.formatted(declared.wrapped().get(index)))
                        : parameter));
      }

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
        if (mi.getMethodType() == null) {
          return mi;
        }
        Optional<String> handedOn = nullHandedOn(mi, declarations);
        if (handedOn.isPresent()) {
          return SearchResult.found(mi, handedOn.get());
        }
        J.MethodInvocation marked =
            nullResultMessage(mi)
                .map(
                    message ->
                        mi.withArguments(
                            ListUtils.mapFirst(
                                mi.getArguments(),
                                function -> markNullableResult(function, message, declarations))))
                .orElse(mi);
        OptionalInt success = successFunction(marked);
        if (success.isEmpty() || !startsAtALazyFactory(marked.getSelect())) {
          return marked;
        }
        return marked.withArguments(
            ListUtils.map(
                marked.getArguments(),
                (index, argument) ->
                    index == success.getAsInt()
                        ? markNullChecks(argument, declarations)
                        : argument));
      }
    };
  }

  /**
   * The mark for a call that passes {@code null} to a wrapper declared in the run, naming the
   * factory the wrapper hands it to. The advice reads the type the parameter takes at this call, as
   * {@code Void} does where a wrapper's {@code T} stands for it.
   */
  private static Optional<String> nullHandedOn(J.MethodInvocation call, Declarations declarations) {
    List<Expression> args = call.getArguments();
    List<JavaType> parameters = call.getMethodType().getParameterTypes();
    return declarations
        .find(call.getMethodType())
        .flatMap(
            declared ->
                declared.wrapped().entrySet().stream()
                    .filter(e -> e.getKey() < args.size() && isNullLiteral(args.get(e.getKey())))
                    .findFirst()
                    .map(
                        e ->
                            (wantsUnit(parameters.get(e.getKey()))
                                    ? NULL_WRAPPED
                                    : NULL_TYPED_WRAPPED)
                                .formatted(e.getValue())));
  }

  /**
   * Whether {@code Unit.INSTANCE} is the advice for a value of this type: {@code Void}, {@code
   * Unit}, or a type the parser left unresolved.
   */
  private static boolean wantsUnit(JavaType type) {
    return TypeUtils.isOfClassType(type, "java.lang.Void")
        || TypeUtils.isOfClassType(type, "org.higherkindedj.hkt.Unit")
        || type instanceof JavaType.GenericTypeVariable
        || type instanceof JavaType.Unknown;
  }

  /**
   * What a null result of the function given to a call does there: a capturing factory gives a
   * {@code Failure}, a deferred one fails when it runs, and an eager {@code map} throws.
   */
  private static Optional<String> nullResultMessage(J.MethodInvocation call) {
    if (CAPTURING_FACTORIES.stream().anyMatch(m -> m.matches(call))) {
      return Optional.of(NULLABLE_SUPPLIER);
    }
    if (DEFERRED_FACTORIES.stream().anyMatch(m -> m.matches(call))) {
      return Optional.of(NULLABLE_DEFERRED);
    }
    if (EAGER_MAPPERS.stream().anyMatch(m -> m.matches(call))) {
      return Optional.of(NULLABLE_EAGER);
    }
    return Optional.empty();
  }

  /**
   * The function, with a mark on each result it reads from a method that may return null: a method
   * reference to one, or a call to one that a lambda returns.
   */
  private static Expression markNullableResult(
      Expression function, String message, Declarations declarations) {
    return switch (function) {
      case J.MemberReference ref when declarations.mayReturnNull(ref.getMethodType()) ->
          SearchResult.found(ref, message);
      case J.Lambda lambda ->
          markReturned(
              lambda,
              result ->
                  bare(result) instanceof J.MethodInvocation call
                      && declarations.mayReturnNull(call.getMethodType()),
              message);
      default -> function;
    };
  }

  /**
   * The argument that takes the success value of a step on a type whose success always holds one:
   * the first, but the last for a {@code fold} or {@code foldFailureFirst}, which take the failure
   * first, unless the {@code fold} is 0.4.x's.
   */
  private static OptionalInt successFunction(J.MethodInvocation call) {
    if (call.getSelect() == null || !isSuccessType(call.getSelect().getType())) {
      return OptionalInt.empty();
    }
    String name = call.getSimpleName();
    if (STEPS.contains(name)) {
      return OptionalInt.of(0);
    }
    if (name.equals("foldFailureFirst") || name.equals("fold")) {
      return OptionalInt.of(isSuccessFirstFold(call) ? 0 : call.getArguments().size() - 1);
    }
    return OptionalInt.empty();
  }

  private static boolean isSuccessType(JavaType type) {
    return type != null
        && SUCCESS_TYPES.stream().anyMatch(success -> TypeUtils.isAssignableTo(success, type));
  }

  /**
   * Whether a receiver is a chain of types whose success always holds a value, that starts at, or
   * passes through, a capturing or deferred factory or the {@code map} of a {@code Try} or a lazy
   * type: where 0.4.x gave a success whose value was null, and 0.5.0 gives a failure.
   */
  private static boolean startsAtALazyFactory(Expression receiver) {
    Expression link = bare(receiver);
    while (link instanceof J.MethodInvocation call && carriesTheValue(call)) {
      if (CAPTURING_FACTORIES.stream().anyMatch(m -> m.matches(call))
          || DEFERRED_FACTORIES.stream().anyMatch(m -> m.matches(call))) {
        return true;
      }
      link = bare(call.getSelect());
    }
    return false;
  }

  /**
   * Whether a link of a chain carries its success value on: it gives a type whose success always
   * holds a value, and not the value its receiver held. Running a task for its result, as {@code
   * join()} does, ends the chain, even where that result is an {@code Either}, since the {@code
   * Either}'s own value came from elsewhere.
   */
  private static boolean carriesTheValue(J.MethodInvocation call) {
    if (!isSuccessType(call.getType())) {
      return false;
    }
    return call.getSelect() == null
        || !(call.getSelect().getType() instanceof JavaType.Parameterized receiver)
        || receiver.getTypeParameters().isEmpty()
        || !TypeUtils.isOfType(receiver.getTypeParameters().getLast(), call.getType());
  }

  /**
   * The step's function, with a mark on each null check of its value: in a lambda, each comparison
   * of its parameter with {@code null}; and a method reference to a method declared in the run
   * whose parameter is {@code @Nullable}.
   */
  private static Expression markNullChecks(Expression function, Declarations declarations) {
    return switch (function) {
      case J.MemberReference ref when declarations.acceptsNull(ref.getMethodType()) ->
          SearchResult.found(ref, NULLABLE_STEP);
      case J.Lambda lambda ->
          parameterName(lambda)
              .map(name -> lambda.withBody(markComparisonsWithNull(lambda.getBody(), name)))
              .orElse(lambda);
      default -> function;
    };
  }

  /** The name of a lambda's one parameter, unless it is unnamed or there are more. */
  private static Optional<String> parameterName(J.Lambda lambda) {
    List<J> parameters = lambda.getParameters().getParameters();
    Optional<String> name =
        parameters.size() != 1
            ? Optional.empty()
            : switch (parameters.getFirst()) {
              case J.VariableDeclarations declared ->
                  Optional.of(declared.getVariables().getFirst().getSimpleName());
              case J.Identifier identifier -> Optional.of(identifier.getSimpleName());
              default -> Optional.empty();
            };
    return name.filter(n -> !n.equals("_"));
  }

  /**
   * The body, with a mark on each {@code name == null}, {@code name != null}, {@code
   * Objects.isNull(name)} and {@code Objects.nonNull(name)}. A nested lambda cannot reuse the name,
   * but a class declared in the body can, so the search does not enter one.
   */
  private static J markComparisonsWithNull(J body, String name) {
    return new JavaIsoVisitor<Integer>() {
      @Override
      public J.Binary visitBinary(J.Binary binary, Integer p) {
        J.Binary b = super.visitBinary(binary, p);
        return nullCheckedName(b).filter(name::equals).isPresent()
            ? SearchResult.found(b, NULL_CHECKED_STEP)
            : b;
      }

      @Override
      public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Integer p) {
        J.MethodInvocation m = super.visitMethodInvocation(method, p);
        return nullCheckedName(m).filter(name::equals).isPresent()
            ? SearchResult.found(m, NULL_CHECKED_STEP)
            : m;
      }

      @Override
      public J.NewClass visitNewClass(J.NewClass newClass, Integer p) {
        return newClass.getBody() == null ? super.visitNewClass(newClass, p) : newClass;
      }

      @Override
      public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Integer p) {
        return classDecl;
      }
    }.visit(body, 0);
  }

  /** The variable a comparison with {@code null} reads, as {@code name == null} does. */
  private static Optional<String> nullCheckedName(J.Binary comparison) {
    if (comparison.getOperator() != J.Binary.Type.Equal
        && comparison.getOperator() != J.Binary.Type.NotEqual) {
      return Optional.empty();
    }
    if (isNullLiteral(comparison.getLeft())) {
      return variableName(comparison.getRight());
    }
    if (isNullLiteral(comparison.getRight())) {
      return variableName(comparison.getLeft());
    }
    return Optional.empty();
  }

  /** The variable a JDK null check reads, as {@code Objects.isNull(name)} does. */
  private static Optional<String> nullCheckedName(J.MethodInvocation call) {
    return NULL_CHECKS.stream().anyMatch(check -> check.matches(call))
        ? variableName(call.getArguments().getFirst())
        : Optional.empty();
  }

  /** The name of the variable an expression is, in brackets or cast. */
  private static Optional<String> variableName(Expression expression) {
    return bare(expression) instanceof J.Identifier identifier
        ? Optional.of(identifier.getSimpleName())
        : Optional.empty();
  }

  /** Whether an annotation, read from the source, says its value may be null. */
  private static boolean isNullable(J.Annotation annotation) {
    return NULLABLE_ANNOTATIONS.contains(annotation.getSimpleName());
  }

  /** Whether an annotation, read from the type model, says its value may be null. */
  private static boolean isNullable(JavaType.FullyQualified annotation) {
    String name = annotation.getFullyQualifiedName();
    return NULLABLE_ANNOTATIONS.contains(name.substring(name.lastIndexOf('.') + 1));
  }

  /**
   * What the run's sources declare about each method a call may reach. The scan fills it, so that a
   * call in one file reads a method declared in another.
   */
  public static final class Declarations {

    private final Map<MethodKey, Declared> methods = new HashMap<>();

    private Declarations() {}

    private void add(JavaType.Method method, Declared declared) {
      methods.merge(MethodKey.of(method), declared, Declared::both);
    }

    private Optional<Declared> find(JavaType.Method method) {
      return method == null
          ? Optional.empty()
          : Optional.ofNullable(methods.get(MethodKey.of(method)));
    }

    /**
     * Whether a method may return null: it carries a declaration annotation that says so, which the
     * type model keeps, or the run declares it with one on its result, which the type model drops.
     */
    private boolean mayReturnNull(JavaType.Method method) {
      return method != null
          && (method.getAnnotations().stream()
                  .anyMatch(DetectPossibleNullSuccessValuesRecipe::isNullable)
              || find(method).map(Declared::returnsNullable).orElse(false));
    }

    /** Whether the run declares a method of one {@code @Nullable} parameter. */
    private boolean acceptsNull(JavaType.Method method) {
      return method != null
          && method.getParameterTypes().size() == 1
          && find(method).map(d -> d.nullableParameters().contains(0)).orElse(false);
    }
  }

  /**
   * A method as a call names it: its declaring type, its name and its number of parameters. The
   * types of its parameters are left out, since a call to a generic method reads them with its own
   * type arguments.
   */
  private record MethodKey(String owner, String name, int arity) {
    static MethodKey of(JavaType.Method method) {
      return new MethodKey(
          method.getDeclaringType().getFullyQualifiedName(),
          method.getName(),
          method.getParameterTypes().size());
    }
  }

  /**
   * What one method's declaration says: whether it may return null, which of its parameters may be
   * null, and which a wrapper hands to a success factory, by index, with that factory's name.
   */
  private record Declared(
      boolean returnsNullable,
      Set<Integer> nullableParameters,
      SortedMap<Integer, String> wrapped) {

    Declared {
      nullableParameters = Set.copyOf(nullableParameters);
      wrapped = Collections.unmodifiableSortedMap(new TreeMap<>(wrapped));
    }

    /** What the method declaration at a cursor says. */
    static Declared of(Cursor declaration, AnnotationService annotations) {
      J.MethodDeclaration method = declaration.getValue();
      List<J> parameters = List.copyOf(method.getParameters());
      Set<Integer> nullable =
          IntStream.range(0, parameters.size())
              .filter(
                  i ->
                      parameters.get(i) instanceof J.VariableDeclarations parameter
                          && annotations
                              .getAllAnnotations(new Cursor(declaration, parameter))
                              .stream()
                              .anyMatch(DetectPossibleNullSuccessValuesRecipe::isNullable))
              .boxed()
              .collect(Collectors.toUnmodifiableSet());
      return new Declared(
          annotations.getAllAnnotations(declaration).stream()
              .anyMatch(DetectPossibleNullSuccessValuesRecipe::isNullable),
          nullable,
          wrapped(method, parameters));
    }

    /**
     * What two overloads with the same name and number of parameters both say, since a call is
     * matched to a declaration by those alone.
     */
    Declared both(Declared other) {
      SortedMap<Integer, String> wrappedByBoth = new TreeMap<>(wrapped);
      wrappedByBoth.keySet().retainAll(other.wrapped.keySet());
      return new Declared(
          returnsNullable && other.returnsNullable,
          nullableParameters.stream()
              .filter(other.nullableParameters::contains)
              .collect(Collectors.toUnmodifiableSet()),
          wrappedByBoth);
    }

    /**
     * Each parameter a wrapper hands to a success factory, with the factory's name: the method's
     * body is one {@code return} of a factory call, or a construction, whose value is the
     * parameter, cast or in brackets. A varargs parameter is left out, since a {@code null} among
     * its arguments is an element, not the array the factory is given.
     */
    private static SortedMap<Integer, String> wrapped(
        J.MethodDeclaration method, List<J> parameters) {
      SortedMap<Integer, String> wrapped = new TreeMap<>();
      if (method.getBody() == null
          || method.getBody().getStatements().size() != 1
          || !(method.getBody().getStatements().getFirst() instanceof J.Return ret)
          || ret.getExpression() == null) {
        return wrapped;
      }
      List<Expression> values;
      String factory;
      switch (bare(ret.getExpression())) {
        case J.MethodInvocation call when call.getMethodType() != null -> {
          values = valueArguments(call);
          factory =
              call.getMethodType().getDeclaringType().getClassName()
                  + "."
                  + call.getMethodType().getName();
        }
        case J.NewClass construction
            when TypeUtils.asFullyQualified(construction.getType()) != null -> {
          values = valueArguments(construction);
          factory = "new " + TypeUtils.asFullyQualified(construction.getType()).getClassName();
        }
        default -> {
          return wrapped;
        }
      }
      for (Expression value : values) {
        variableName(value)
            .flatMap(name -> parameterIndex(parameters, name))
            .ifPresent(index -> wrapped.put(index, factory));
      }
      return wrapped;
    }

    /** The index of the parameter with a name, unless it is varargs. */
    private static Optional<Integer> parameterIndex(List<J> parameters, String name) {
      return IntStream.range(0, parameters.size())
          .filter(
              i ->
                  parameters.get(i) instanceof J.VariableDeclarations parameter
                      && parameter.getVarargs() == null
                      && parameter.getVariables().getFirst().getSimpleName().equals(name))
          .boxed()
          .findFirst();
    }
  }
}
