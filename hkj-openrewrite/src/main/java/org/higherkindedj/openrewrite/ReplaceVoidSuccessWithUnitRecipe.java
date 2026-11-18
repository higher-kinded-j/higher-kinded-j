// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.higherkindedj.openrewrite.DetectNullSuccessValuesRecipe.SUCCESS_TYPES;
import static org.higherkindedj.openrewrite.DetectPossibleNullSuccessValuesRecipe.isNullable;
import static org.higherkindedj.openrewrite.NullLiterals.inBrackets;
import static org.higherkindedj.openrewrite.NullLiterals.isNullLiteral;
import static org.higherkindedj.openrewrite.NullLiterals.isVoidType;
import static org.higherkindedj.openrewrite.NullLiterals.mapReturned;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

/**
 * Recipe that replaces {@code Void} with {@code Unit} as the value type of a Higher-Kinded-J
 * success type, and each {@code null} it can tie to that value with {@code Unit.INSTANCE}. From
 * 0.5.0 a {@code Right} or a {@code Success} always holds a value, and {@code Void}'s only value is
 * {@code null}, so a {@code null} where a {@code Void} value is expected can only mean "nothing to
 * return".
 *
 * <h2>What it rewrites</h2>
 *
 * <ul>
 *   <li>{@code Void} as the value type of a type that {@link DetectNullSuccessValuesRecipe} marks,
 *       such as {@code Either<E, Void>}, {@code EitherPath<E, Void>} or {@code VTask<Void>}, at any
 *       depth, as in {@code List<Either<E, Void>>}, and as a wildcard's bound there. A {@code Kind}
 *       over one of their witnesses, such as {@code Kind<StateTKind.Witness<S, F>, Void>}, and a
 *       {@code Void} type argument written on a call, as in {@code Either.<String, Void>right(..)},
 *       change too. A {@code @Nullable} written on the {@code Void} goes, since a {@code Unit} is
 *       never null.
 *   <li>A {@code null}, cast or not, passed where the call's declared result holds that parameter's
 *       type variable as its value, and the call's result holds {@code Void}: {@code
 *       Either.right(null)}, {@code StateTuple.of(state, (Void) null)}, {@code monad.of(null)}, and
 *       a project's own wrapper such as {@code static <T> Either<String, T> ok(T value)}. The type
 *       variable must be the method's own, or one of a success type's, since only then does the
 *       call, or a type this recipe rewrites, decide it. Where another overload fits the call too,
 *       as {@code ok(Void)} does beside {@code <T> ok(T)}, the {@code null} stays. So it does where
 *       another could take the {@code Unit}, as {@code ok(Unit)} could, since Java might then pick
 *       that one. Each {@code null} a conditional gives counts, here and below, as in {@code ready
 *       ? null : null}.
 *   <li>A {@code null} returned by a lambda whose function gives that value, in brackets or not, as
 *       its body or in one of its own {@code return}s: {@code VTask.of(() -> { ...; return null;
 *       })}, {@code either.map(x -> null)}, and a lambda that is itself a {@code VTask}, an {@code
 *       IO} or a {@code Reader} holding {@code Void}, or an interface that extends one.
 *   <li>A method that overrides one returning that value, such as {@code execute()} in an anonymous
 *       {@code VTask<Void>}, or one that overrides it through a class between them: its {@code
 *       Void} result becomes {@code Unit}, and each {@code null} it returns {@code Unit.INSTANCE}.
 * </ul>
 *
 * <h2>What it leaves</h2>
 *
 * <ul>
 *   <li>{@code Void} in any other type, such as {@code CompletableFuture<Void>}, {@code
 *       HttpResponse<Void>} or a {@code Callable<Void>} variable, and {@code Void} as an error
 *       type, which {@link DetectNullErrorValuesRecipe} marks.
 *   <li>A {@code CompletableFuture<Void>} given to {@code Path.future}, which {@link
 *       DetectNullSuccessValuesRecipe} marks.
 *   <li>A value or function typed {@code Void}, such as a variable, a {@code Supplier<Void>} or a
 *       method reference to a method returning {@code Void}, which stops compiling where it meets a
 *       rewritten type. {@link DetectNullSuccessValuesRecipe} marks one passed to a success
 *       factory, as in {@code Either.right(purge())}, since it can only be {@code null}.
 *   <li>A {@code null} it cannot tie to a {@code Void} value, such as one in a lambda whose result
 *       the compiler typed {@code Object}. {@link DetectNullSuccessValuesRecipe}, run after this
 *       recipe, marks it, since 0.5.0 refuses it when it runs.
 *   <li>A file where {@code Unit.INSTANCE} cannot be written: one with a variable named {@code
 *       org}, which hides the package in {@code org.higherkindedj.hkt.Unit.INSTANCE}, beside a type
 *       or a variable named {@code Unit}. {@link DetectNullSuccessValuesRecipe} marks its {@code
 *       Void} types and {@code null}s.
 * </ul>
 *
 * <p>The rewrite changes a signature for every caller and implementer. OpenRewrite rewrites every
 * file in the run, so those in the same build change with it; one in another build stops compiling.
 * On 0.4.x, {@code hkj-spring} writes a {@code Unit} success as a JSON string, where a {@code Void}
 * one wrote {@code null}, so run it in the change that moves the project to 0.5.0, not in an
 * earlier one.
 */
public class ReplaceVoidSuccessWithUnitRecipe extends Recipe {

  /** Creates a new instance of this recipe. */
  public ReplaceVoidSuccessWithUnitRecipe() {}

  private static final String UNIT = "org.higherkindedj.hkt.Unit";

  private static final String KIND = "org.higherkindedj.hkt.Kind";

  /** {@code Unit} and its supertypes, each a parameter type that could take a {@code Unit}. */
  private static final Set<String> HOLDS_UNIT =
      Set.of(
          UNIT,
          "java.lang.Object",
          "java.lang.Enum",
          "java.lang.Comparable",
          "java.io.Serializable",
          "java.lang.constant.Constable");

  /** What a witness's name adds to its type's, as {@code EitherKind$Witness} does to Either. */
  private static final String WITNESS_SUFFIX = "Kind$Witness";

  private static final Set<String> OBJECT_METHODS = Set.of("equals", "hashCode", "toString");

  private static final JavaType.FullyQualified UNIT_TYPE = JavaType.ShallowClass.build(UNIT);

  private static final JavaType.Variable UNIT_INSTANCE =
      new JavaType.Variable(
          null,
          Flag.flagsToBitMap(EnumSet.of(Flag.Public, Flag.Static, Flag.Final)),
          "INSTANCE",
          UNIT_TYPE,
          UNIT_TYPE,
          List.of());

  /** The cursor message that says another type named {@code Unit} is in use in the file. */
  private static final String UNIT_TAKEN = "unitTaken";

  @Override
  public String getDisplayName() {
    return "Replace a Void success with Unit";
  }

  @Override
  public String getDescription() {
    return "Replaces Void with Unit as the value type of an Either, Try, lazy type, Path, or Kind"
        + " over one, and each null it can tie to that value with Unit.INSTANCE: a null passed as"
        + " the value, a null a lambda returns as the value, and a null returned by a method that"
        + " overrides one returning the value. From 0.5.0 a success always holds a value. Void in"
        + " any other type, or as an error type, is left alone, and DetectNullSuccessValuesRecipe"
        + " marks the nulls it finds that this recipe leaves.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "migration", "null");
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      @Override
      public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
        Set<String> values = valueNames(cu);
        boolean taken = unitNameTaken(cu, values);
        if (taken && values.contains("org")) {
          // A variable named org hides the package in org.higherkindedj.hkt.Unit.INSTANCE too.
          return cu;
        }
        getCursor().putMessage(UNIT_TAKEN, taken);
        return super.visitCompilationUnit(cu, ctx);
      }

      @Override
      public J.ParameterizedType visitParameterizedType(
          J.ParameterizedType type, ExecutionContext ctx) {
        J.ParameterizedType pt = super.visitParameterizedType(type, ctx);
        List<Expression> args = pt.getTypeParameters();
        OptionalInt found = args == null ? OptionalInt.empty() : valueIndex(pt.getType(), args);
        if (found.isEmpty()) {
          return pt;
        }
        int value = found.getAsInt();
        List<Expression> rewritten =
            ListUtils.map(args, (i, a) -> i == value ? unitTypeIfVoid(a) : a);
        if (rewritten == args) {
          return pt;
        }
        return pt.withTypeParameters(rewritten)
            .withType(
                pt.getType() instanceof JavaType.Parameterized held
                    ? withUnitAt(held, value)
                    : pt.getType());
      }

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
        JavaType.Method invoked = mi.getMethodType();
        if (invoked == null || !holdsVoidValue(invoked.getReturnType())) {
          return mi;
        }
        boolean successOwner =
            SUCCESS_TYPES.contains(invoked.getDeclaringType().getFullyQualifiedName());
        List<JavaType.Method> overloads =
            invoked.getDeclaringType().getMethods().stream()
                .filter(m -> !m.isConstructor() && m.getName().equals(invoked.getName()))
                .toList();
        Optional<Declared> found =
            onlyFitting(overloads, invoked)
                .flatMap(
                    m ->
                        valueVariable(m.getReturnType())
                            .filter(v -> successOwner || m.getDeclaredFormalTypeNames().contains(v))
                            .map(v -> new Declared(m, v)));
        if (found.isEmpty()) {
          return mi;
        }
        Declared declared = found.get();
        List<Expression> args = valueArguments(mi.getArguments(), invoked, declared);
        if (anotherTakesUnit(overloads, declared, mi.getArguments(), args)) {
          return mi;
        }
        J.MethodInvocation withArgs = mi.withArguments(args);
        JContainer<Expression> witnesses = withArgs.getPadding().getTypeParameters();
        if (witnesses == null) {
          return withArgs;
        }
        int count = witnesses.getElements().size();
        return withArgs
            .getPadding()
            .withTypeParameters(
                JContainer.withElements(
                    witnesses,
                    ListUtils.map(
                        witnesses.getElements(),
                        (i, w) -> declared.isValueWitness(i, count) ? unitTypeIfVoid(w) : w)));
      }

      @Override
      public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
        J.NewClass nc = super.visitNewClass(newClass, ctx);
        JavaType.Method invoked = nc.getConstructorType();
        if (invoked == null
            || nc.getArguments().isEmpty()
            || !holdsVoidValue(invoked.getReturnType())
            || !SUCCESS_TYPES.contains(invoked.getDeclaringType().getFullyQualifiedName())) {
          return nc;
        }
        List<JavaType> classVariables = invoked.getDeclaringType().getTypeParameters();
        Optional<String> value =
            classVariables.isEmpty()
                ? Optional.empty()
                : typeVariableName(classVariables.getLast());
        List<JavaType.Method> constructors =
            invoked.getDeclaringType().getMethods().stream()
                .filter(JavaType.Method::isConstructor)
                .toList();
        return onlyFitting(constructors, invoked)
            .flatMap(m -> value.map(v -> new Declared(m, v)))
            .flatMap(
                d -> {
                  List<Expression> args = valueArguments(nc.getArguments(), invoked, d);
                  return anotherTakesUnit(constructors, d, nc.getArguments(), args)
                      ? Optional.empty()
                      : Optional.of(nc.withArguments(args));
                })
            .orElse(nc);
      }

      @Override
      public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext ctx) {
        J.Lambda l = super.visitLambda(lambda, ctx);
        // A lambda that is itself a success, such as a VTask: its function gives the value.
        return successSelf(l.getType())
                .filter(self -> resultVariable(self).orElse(-1) == lastIndex(self))
                .filter(ReplaceVoidSuccessWithUnitRecipe::holdsVoidValue)
                .isPresent()
            ? unitReturns(l)
            : l;
      }

      @Override
      public J.MethodDeclaration visitMethodDeclaration(
          J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
        JavaType.Method type = md.getMethodType();
        if (type == null
            || !(md.getReturnTypeExpression() instanceof Expression returned)
            || !isVoid(returned)
            || !overridesValue(type)) {
          return md;
        }
        J.MethodDeclaration typed =
            md.withReturnTypeExpression((TypeTree) unitType(returned))
                .withMethodType(type.withReturnType(UNIT_TYPE));
        J.Block body = typed.getBody();
        return body == null
            ? typed
            : typed.withBody(NullLiterals.mapOwnReturns(body, this::unitIfNull));
      }

      /**
       * The arguments with each {@code null} at the value parameter made {@code Unit.INSTANCE}, and
       * each lambda whose function gives the value made to return {@code Unit.INSTANCE} for {@code
       * null}.
       */
      private List<Expression> valueArguments(
          List<Expression> args, JavaType.Method invoked, Declared declared) {
        List<JavaType> invokedTypes = invoked.getParameterTypes();
        return ListUtils.map(
            args,
            (i, arg) -> {
              if (i >= invokedTypes.size()) {
                return arg;
              }
              if (isVoidType(invokedTypes.get(i)) && declared.takesValueAt(i)) {
                return unitIfNull(arg);
              }
              return declared.givesValueAt(i) ? inBrackets(arg, this::unitReturnsIfVoid) : arg;
            });
      }

      /** A lambda whose function gives {@code Void} made to return {@code Unit.INSTANCE}. */
      private Expression unitReturnsIfVoid(Expression arg) {
        return arg instanceof J.Lambda lambda
                && lambda.getType() instanceof JavaType.Parameterized function
                && resultVariable(function).stream().anyMatch(r -> holdsVoidAt(function, r))
            ? unitReturns(lambda)
            : arg;
      }

      /** The lambda with each {@code null} it returns, as its body or in a return, made a Unit. */
      private J.Lambda unitReturns(J.Lambda lambda) {
        return mapReturned(lambda, this::unitIfNull);
      }

      /**
       * {@code Unit.INSTANCE} in place of a {@code null}, cast or not, and of each {@code null} a
       * conditional gives, keeping the brackets around it.
       */
      private Expression unitIfNull(Expression value) {
        return inBrackets(
            value,
            inner ->
                switch (inner) {
                  case J.Ternary choice ->
                      choice
                          .withTruePart(unitIfNull(choice.getTruePart()))
                          .withFalsePart(unitIfNull(choice.getFalsePart()));
                  default -> isNullLiteral(inner) ? unitInstance(inner) : inner;
                });
      }

      /** {@code Unit} in place of a {@code Void} type, or of a wildcard's {@code Void} bound. */
      private Expression unitTypeIfVoid(Expression typeTree) {
        if (typeTree instanceof J.Wildcard wildcard
            && wildcard.getBoundedType() instanceof Expression bound
            && isVoid(bound)) {
          return wildcard.withBoundedType((NameTree) unitType(bound));
        }
        return isVoid(typeTree) ? unitType(typeTree) : typeTree;
      }

      /**
       * {@code Unit} in place of a {@code Void} type, keeping its id, so the space and comments
       * after it stay, and its prefix and any annotation on it but one that says it may be null.
       */
      private Expression unitType(Expression voidType) {
        if (!(voidType instanceof J.AnnotatedType annotated)) {
          Expression unit = unitName(voidType.getPrefix(), List.of());
          return unit.withId(voidType.getId());
        }
        List<J.Annotation> all = annotated.getAnnotations();
        List<J.Annotation> kept = all.stream().filter(a -> !isNullable(a)).toList();
        // In a method's return type the space before the type is the first annotation's.
        Space outer =
            annotated.getPrefix().isEmpty() ? all.getFirst().getPrefix() : annotated.getPrefix();
        if (kept.isEmpty() || unitTaken()) {
          // A type annotation on a qualified name goes before its simple name.
          Expression unit =
              unitName(outer, ListUtils.mapFirst(kept, a -> a.withPrefix(Space.EMPTY)));
          return unit.withId(annotated.getId());
        }
        List<J.Annotation> placed =
            kept.getFirst() == all.getFirst()
                ? kept
                : ListUtils.mapFirst(kept, a -> a.withPrefix(all.getFirst().getPrefix()));
        return annotated
            .withAnnotations(placed)
            .withTypeExpression(
                (TypeTree) unitName(annotated.getTypeExpression().getPrefix(), List.of()));
      }

      /** {@code Unit.INSTANCE} in place of a {@code null}, with its id and prefix. */
      private Expression unitInstance(Expression replaced) {
        return new J.FieldAccess(
            replaced.getId(),
            replaced.getPrefix(),
            Markers.EMPTY,
            unitName(Space.EMPTY, List.of()),
            JLeftPadded.build(
                new J.Identifier(
                    Tree.randomId(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    List.of(),
                    "INSTANCE",
                    UNIT_TYPE,
                    UNIT_INSTANCE)),
            UNIT_TYPE);
      }

      /**
       * {@code Unit} as a type name: imported, or written in full where another type named {@code
       * Unit} is in use in the file, with any type annotation before its simple name.
       */
      private Expression unitName(Space prefix, List<J.Annotation> annotations) {
        if (!unitTaken()) {
          maybeAddImport(UNIT);
          return new J.Identifier(
              Tree.randomId(), prefix, Markers.EMPTY, annotations, "Unit", UNIT_TYPE, null);
        }
        J.FieldAccess qualified = TypeTree.build(UNIT);
        J.FieldAccess named =
            annotations.isEmpty()
                ? qualified
                : qualified.withName(
                    qualified
                        .getName()
                        .withAnnotations(annotations)
                        .withPrefix(Space.SINGLE_SPACE));
        return named.withPrefix(prefix).withType(UNIT_TYPE);
      }

      /**
       * Whether another type, or a variable, named {@code Unit} is in use in the file being
       * visited.
       */
      private boolean unitTaken() {
        Boolean taken = getCursor().getNearestMessage(UNIT_TAKEN);
        if (taken != null) {
          return taken;
        }
        J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
        return cu != null && unitNameTaken(cu, valueNames(cu));
      }
    };
  }

  /**
   * The generic declaration a call resolves to, as {@link #onlyFitting} finds it, with the name of
   * the type variable its result holds as its value.
   */
  private record Declared(JavaType.Method method, String value) {

    /** Whether the parameter at an index is the type variable the result holds as its value. */
    boolean takesValueAt(int index) {
      List<JavaType> params = method.getParameterTypes();
      return index < params.size()
          && !(method.hasFlags(Flag.Varargs) && index == params.size() - 1)
          && typeVariableName(params.get(index)).filter(value::equals).isPresent();
    }

    /**
     * Whether the parameter at an index is a function whose result is the type variable the
     * declared result holds as its value, as {@code Callable<A>} is to {@code VTask<A> of(..)}.
     */
    boolean givesValueAt(int index) {
      List<JavaType> params = method.getParameterTypes();
      return index < params.size()
          && params.get(index) instanceof JavaType.Parameterized fn
          && resultVariable(fn).stream()
              .mapToObj(r -> typeVariableName(fn.getTypeParameters().get(r)))
              .anyMatch(name -> name.filter(value::equals).isPresent());
    }

    /**
     * Whether a call's type argument at an index, of {@code count} written, is the one for the
     * value, as {@code Void} is in {@code Either.<String, Void>right(..)}.
     */
    boolean isValueWitness(int index, int count) {
      List<String> formals = method.getDeclaredFormalTypeNames();
      return formals.size() == count && formals.indexOf(value) == index;
    }
  }

  /**
   * The one declaration among overloads whose parameters fit a call's; empty where none or several
   * do, since then the value's place is not known.
   */
  private static Optional<JavaType.Method> onlyFitting(
      List<JavaType.Method> overloads, JavaType.Method invoked) {
    List<JavaType.Method> fitting =
        overloads.stream().filter(m -> fits(m, invoked)).limit(2).toList();
    return fitting.size() == 1 ? Optional.of(fitting.getFirst()) : Optional.empty();
  }

  /**
   * Whether another overload of the same arity could take a {@code Unit} at a parameter the rewrite
   * gives one, as {@code ok(Unit)} could beside {@code <T> ok(T)}. Java might then pick it in place
   * of the declaration the call resolves to now.
   */
  private static boolean anotherTakesUnit(
      List<JavaType.Method> overloads,
      Declared declared,
      List<Expression> before,
      List<Expression> after) {
    int arity = declared.method().getParameterTypes().size();
    List<Integer> given =
        IntStream.range(0, Math.min(arity, before.size()))
            .filter(i -> before.get(i) != after.get(i) && declared.takesValueAt(i))
            .boxed()
            .toList();
    return overloads.stream()
        .filter(m -> m != declared.method() && m.getParameterTypes().size() == arity)
        .anyMatch(m -> given.stream().anyMatch(i -> couldHoldUnit(m.getParameterTypes().get(i))));
  }

  /**
   * Whether a parameter could take a {@code Unit}: {@code Unit} or one of its supertypes, or a type
   * variable that only such types bound, as {@code Enum<E>} bounds {@code E}.
   */
  private static boolean couldHoldUnit(JavaType parameter) {
    return switch (parameter) {
      case JavaType.GenericTypeVariable variable ->
          variable.getBounds().stream().allMatch(ReplaceVoidSuccessWithUnitRecipe::couldHoldUnit);
      case JavaType.FullyQualified named -> HOLDS_UNIT.contains(named.getFullyQualifiedName());
      default -> false;
    };
  }

  /**
   * Whether a declaration's parameters fit a call's, one by one: a type variable fits any type, and
   * any other type fits the same class or primitive.
   */
  private static boolean fits(JavaType.Method declaration, JavaType.Method invoked) {
    List<JavaType> declared = declaration.getParameterTypes();
    List<JavaType> called = invoked.getParameterTypes();
    return declared.size() == called.size()
        && IntStream.range(0, declared.size()).allMatch(i -> fits(declared.get(i), called.get(i)));
  }

  private static boolean fits(JavaType declared, JavaType called) {
    return switch (declared) {
      case JavaType.GenericTypeVariable _ -> true;
      case JavaType.Array array ->
          called instanceof JavaType.Array calledArray
              && fits(array.getElemType(), calledArray.getElemType());
      case JavaType.FullyQualified named ->
          TypeUtils.asFullyQualified(called) instanceof JavaType.FullyQualified calledNamed
              && named.getFullyQualifiedName().equals(calledNamed.getFullyQualifiedName());
      default -> TypeUtils.isOfType(declared, called);
    };
  }

  /**
   * The index of a type's value type argument: the last for a success type, and the second for a
   * {@code Kind} whose witness is a success type's; empty for any other type.
   */
  private static OptionalInt valueIndex(JavaType type, List<?> args) {
    JavaType.FullyQualified raw = TypeUtils.asFullyQualified(type);
    if (raw == null || args.isEmpty()) {
      return OptionalInt.empty();
    }
    if (SUCCESS_TYPES.contains(raw.getFullyQualifiedName())) {
      return OptionalInt.of(args.size() - 1);
    }
    return KIND.equals(raw.getFullyQualifiedName())
            && args.size() == 2
            && type instanceof JavaType.Parameterized kind
            && isSuccessWitness(kind.getTypeParameters().getFirst())
        ? OptionalInt.of(1)
        : OptionalInt.empty();
  }

  /**
   * The name of the type variable a declared result holds as its value; empty where it holds none
   * or is not a success type or a {@code Kind}.
   */
  private static Optional<String> valueVariable(JavaType declaredResult) {
    if (!(declaredResult instanceof JavaType.Parameterized result)) {
      return Optional.empty();
    }
    List<JavaType> args = result.getTypeParameters();
    String raw = result.getFullyQualifiedName();
    return SUCCESS_TYPES.contains(raw) || (KIND.equals(raw) && args.size() == 2)
        ? typeVariableName(args.getLast())
        : Optional.empty();
  }

  /** Whether a type holds {@code Void} as the value of a success type, or of a Kind over one. */
  private static boolean holdsVoidValue(JavaType type) {
    return type instanceof JavaType.Parameterized held
        && valueIndex(held, held.getTypeParameters()).stream().anyMatch(i -> holdsVoidAt(held, i));
  }

  private static boolean holdsVoidAt(JavaType.Parameterized type, int index) {
    return index < type.getTypeParameters().size()
        && isVoidType(type.getTypeParameters().get(index));
  }

  /** {@code Unit} in place of the type argument at an index. */
  private static JavaType.Parameterized withUnitAt(JavaType.Parameterized type, int index) {
    return type.withTypeParameters(
        ListUtils.map(type.getTypeParameters(), (i, arg) -> i == index ? unitOrBound(arg) : arg));
  }

  /**
   * {@code Unit} in place of a {@code Void} type argument, or of a wildcard's {@code Void} bound.
   */
  private static JavaType unitOrBound(JavaType arg) {
    return arg instanceof JavaType.GenericTypeVariable wildcard
        ? wildcard.withBounds(List.of(UNIT_TYPE))
        : UNIT_TYPE;
  }

  /**
   * Whether a type tree is {@code Void}: as {@code Void}, {@code java.lang.Void} or with an
   * annotation, as in {@code @Nullable Void}, but not as a wildcard's bound.
   */
  private static boolean isVoid(Expression typeTree) {
    return (typeTree instanceof J.Identifier
            || typeTree instanceof J.FieldAccess
            || typeTree instanceof J.AnnotatedType)
        && isVoidType(typeTree.getType());
  }

  /** Whether a type is the witness of a success type, such as {@code EitherKind.Witness<L>}. */
  private static boolean isSuccessWitness(JavaType type) {
    JavaType.FullyQualified witness = TypeUtils.asFullyQualified(type);
    if (witness == null) {
      return false;
    }
    String name = witness.getFullyQualifiedName();
    return name.endsWith(WITNESS_SUFFIX)
        && SUCCESS_TYPES.contains(name.substring(0, name.length() - WITNESS_SUFFIX.length()));
  }

  /**
   * The success type a lambda's type is, or extends, with its type arguments, as {@code
   * VTask<Void>} is for {@code interface Job extends VTask<Void>}.
   */
  private static Optional<JavaType.Parameterized> successSelf(JavaType type) {
    if (type instanceof JavaType.Parameterized self
        && SUCCESS_TYPES.contains(self.getFullyQualifiedName())) {
      return Optional.of(self);
    }
    JavaType.FullyQualified named = TypeUtils.asFullyQualified(type);
    return named == null
        ? Optional.empty()
        : ancestors(named, 0)
            .filter(t -> SUCCESS_TYPES.contains(t.getFullyQualifiedName()))
            .flatMap(t -> t instanceof JavaType.Parameterized p ? Stream.of(p) : Stream.empty())
            .findFirst();
  }

  private static int lastIndex(JavaType.Parameterized type) {
    return type.getTypeParameters().size() - 1;
  }

  /**
   * Of a functional interface, the index of the type parameter its one abstract method returns, as
   * 0 is for {@code Callable<V>} and 1 for {@code Function<T, R>}; empty where it returns another
   * type, or the interface has no one abstract method of its own.
   */
  private static OptionalInt resultVariable(JavaType.Parameterized function) {
    JavaType.FullyQualified raw = function.getType();
    List<JavaType.Method> abstracts =
        raw.getMethods().stream()
            .filter(m -> m.hasFlags(Flag.Abstract) && !m.hasFlags(Flag.Default))
            .filter(m -> !OBJECT_METHODS.contains(m.getName()))
            .toList();
    if (abstracts.size() != 1) {
      return OptionalInt.empty();
    }
    Optional<String> result = typeVariableName(abstracts.getFirst().getReturnType());
    List<JavaType> variables = raw.getTypeParameters();
    return IntStream.range(0, variables.size())
        .filter(i -> result.isPresent() && result.equals(typeVariableName(variables.get(i))))
        .findFirst();
  }

  /**
   * The name of a type variable, read through an upper-bounded wildcard, as {@code T} is in {@code
   * ? extends T}; empty for any other type.
   */
  private static Optional<String> typeVariableName(JavaType type) {
    if (!(type instanceof JavaType.GenericTypeVariable variable)) {
      return Optional.empty();
    }
    if (!"?".equals(variable.getName())) {
      return Optional.of(variable.getName());
    }
    return variable.getVariance() == JavaType.GenericTypeVariable.Variance.COVARIANT
            && variable.getBounds().size() == 1
            && variable.getBounds().getFirst() instanceof JavaType.GenericTypeVariable bound
            && !"?".equals(bound.getName())
        ? Optional.of(bound.getName())
        : Optional.empty();
  }

  /**
   * Whether a method overrides one whose result is the value of a success type, which its class
   * gives as {@code Void}, as {@code execute()} in an anonymous {@code VTask<Void>} does. The
   * override may pass through a class between them, as {@code BaseJob.execute()} does for {@code
   * class Flush extends BaseJob}, with {@code BaseJob implements VTask<Void>}.
   */
  private static boolean overridesValue(JavaType.Method method) {
    Optional<JavaType.Method> overridden =
        Stream.iterate(
                TypeUtils.findOverriddenMethod(method),
                Optional::isPresent,
                above -> above.flatMap(TypeUtils::findOverriddenMethod))
            .limit(16)
            .flatMap(Optional::stream)
            .filter(m -> SUCCESS_TYPES.contains(m.getDeclaringType().getFullyQualifiedName()))
            .findFirst();
    if (overridden.isEmpty()) {
      return false;
    }
    JavaType.FullyQualified owner = overridden.get().getDeclaringType();
    List<JavaType> variables = owner.getTypeParameters();
    String name = owner.getFullyQualifiedName();
    return SUCCESS_TYPES.contains(name)
        && !variables.isEmpty()
        && typeVariableName(overridden.get().getReturnType())
            .filter(result -> typeVariableName(variables.getLast()).orElse("").equals(result))
            .isPresent()
        && ancestors(method.getDeclaringType(), 0)
            .filter(t -> name.equals(t.getFullyQualifiedName()))
            .findFirst()
            .filter(ReplaceVoidSuccessWithUnitRecipe::holdsVoidValue)
            .isPresent();
  }

  /**
   * A class's supertypes at every depth, each as the class or interface that names it declares it.
   * Their arguments are not put through the ones the class gives, so {@code class Flush extends
   * Base<Void>}, with {@code Base<T> implements VTask<T>}, reaches {@code VTask<T>}: only a {@code
   * Void} written on the success type itself, which this recipe rewrites, counts.
   */
  private static Stream<JavaType.FullyQualified> ancestors(
      JavaType.FullyQualified type, int depth) {
    if (depth > 16) {
      return Stream.empty();
    }
    return Stream.concat(Stream.ofNullable(type.getSupertype()), type.getInterfaces().stream())
        .flatMap(parent -> Stream.concat(Stream.of(parent), ancestors(parent, depth + 1)));
  }

  /**
   * Whether a type other than Higher-Kinded-J's {@code Unit} is named {@code Unit} in a file, so
   * that importing {@code Unit} would hide it, or clash with it: one in use, one declared there, or
   * a type variable of that name. A variable or an enum constant named {@code Unit} counts too,
   * since in {@code Unit.INSTANCE} it hides the type.
   */
  private static boolean unitNameTaken(J.CompilationUnit cu, Set<String> valueNames) {
    if (valueNames.contains("Unit")) {
      return true;
    }
    boolean used =
        cu.getTypesInUse().getTypesInUse().stream()
            .map(TypeUtils::asFullyQualified)
            .anyMatch(
                t ->
                    t != null
                        && !UNIT.equals(t.getFullyQualifiedName())
                        && "Unit".equals(simpleName(t)));
    if (used) {
      return true;
    }
    return new JavaIsoVisitor<AtomicBoolean>() {
      @Override
      public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration c, AtomicBoolean found) {
        if ("Unit".equals(c.getSimpleName())) {
          found.set(true);
        }
        return super.visitClassDeclaration(c, found);
      }

      @Override
      public J.TypeParameter visitTypeParameter(J.TypeParameter t, AtomicBoolean found) {
        if (t.getName() instanceof J.Identifier name && "Unit".equals(name.getSimpleName())) {
          found.set(true);
        }
        return super.visitTypeParameter(t, found);
      }
    }.reduce(cu, new AtomicBoolean()).get();
  }

  /**
   * The names a file gives a variable, a parameter, a field or an enum constant, any of which hides
   * a type or a package of that name at the start of an expression such as {@code Unit.INSTANCE}.
   */
  private static Set<String> valueNames(J.CompilationUnit cu) {
    return new JavaIsoVisitor<Set<String>>() {
      @Override
      public J.VariableDeclarations.NamedVariable visitVariable(
          J.VariableDeclarations.NamedVariable v, Set<String> names) {
        names.add(v.getSimpleName());
        return super.visitVariable(v, names);
      }

      @Override
      public J.EnumValue visitEnumValue(J.EnumValue e, Set<String> names) {
        names.add(e.getName().getSimpleName());
        return super.visitEnumValue(e, names);
      }
    }.reduce(cu, new HashSet<>());
  }

  private static String simpleName(JavaType.FullyQualified type) {
    String name = type.getFullyQualifiedName();
    return name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1);
  }
}
