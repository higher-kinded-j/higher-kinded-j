// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

/**
 * Rewrites each call to the deprecated {@code parCollect} to {@code toList()} on the stream it
 * collects, which gives the same list.
 *
 * <p>The three forms are deprecated in 0.6.0 for removal in 0.7.0, since a stream is read one pull
 * at a time and {@code parCollect} runs nothing in parallel. The rewrites are:
 *
 * <ul>
 *   <li>{@code VStreamPar.parCollect(stream, batchSize)} to {@code stream.toList()}
 *   <li>{@code path.parCollect(batchSize)}, on a {@code VStreamPath}, to {@code path.toList()}
 *   <li>{@code PathOps.parCollectVStream(path, batchSize)} to {@code path.toList()}
 * </ul>
 *
 * <p>Where the rewrite could change what the code does, or stop it compiling, the call is marked
 * instead, with what to do:
 *
 * <ul>
 *   <li>a batch size that is computed, since dropping it drops the computation, or that is not a
 *       positive literal, since {@code toList()} does not throw as {@code parCollect} did;
 *   <li>a stream argument whose type the call infers, such as {@code VStream.of(1, 2)} where a
 *       {@code VTask<List<Number>>} is wanted: as the receiver of {@code toList()} it would be
 *       inferred on its own, so write its type arguments, as in {@code VStream.<Number>of(1, 2)};
 *   <li>a stream argument that is {@code null}, a lambda or a method reference, which cannot be a
 *       receiver;
 *   <li>a method reference to one of the three, which has no {@code toList()} form to take its
 *       place: call {@code toList()} in a lambda.
 * </ul>
 *
 * <p>A stream that binds less tightly than a method call, such as {@code first ? a : b}, is put in
 * parentheses. Run the recipe before the project moves to 0.7.0, which removes the methods it
 * matches. {@code toList()} compiles against 0.4.x and later.
 */
public class ReplaceParCollectWithToListRecipe extends Recipe {

  /** Creates a new instance of this recipe. */
  public ReplaceParCollectWithToListRecipe() {}

  private static final MethodMatcher VSTREAM_PAR =
      new MethodMatcher(
          "org.higherkindedj.hkt.vstream.VStreamPar parCollect(org.higherkindedj.hkt.vstream.VStream,"
              + " int)");
  private static final MethodMatcher VSTREAM_PATH =
      new MethodMatcher("org.higherkindedj.hkt.effect.VStreamPath parCollect(int)", true);
  private static final MethodMatcher PATH_OPS =
      new MethodMatcher(
          "org.higherkindedj.hkt.effect.PathOps parCollectVStream("
              + "org.higherkindedj.hkt.effect.VStreamPath, int)");

  private static final String COMPUTED_BATCH_SIZE =
      "Not rewritten: the batch size is computed, and toList() takes none; keep what computing it"
          + " does, then call toList() on the stream";
  private static final String NOT_POSITIVE =
      "Not rewritten: parCollect throws IllegalArgumentException for a batch size that is not"
          + " positive, and toList() does not; call toList() on the stream if the exception is not"
          + " wanted";
  private static final String INFERRED_STREAM =
      "Not rewritten: the stream's type is inferred from this call; write its type arguments, as"
          + " in VStream.<Number>of(1, 2), then call toList() on it";
  private static final String NO_RECEIVER =
      "Not rewritten: a null, a lambda or a method reference cannot be the receiver of toList()";
  private static final String METHOD_REFERENCE =
      "Not rewritten: a method reference to a deprecated parCollect; call toList() on the stream"
          + " in a lambda instead";

  @Override
  public String getDisplayName() {
    return "parCollect -> toList()";
  }

  @Override
  public String getDescription() {
    return "Rewrites calls to the deprecated `VStreamPar.parCollect(stream, batchSize)`, "
        + "`VStreamPath.parCollect(batchSize)` and `PathOps.parCollectVStream(path, batchSize)` to "
        + "`toList()` on the stream, which gives the same list. A call the rewrite could break, "
        + "such as one whose batch size is computed, is marked instead.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "deprecation", "migration");
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

        boolean staticForm = VSTREAM_PAR.matches(mi) || PATH_OPS.matches(mi);
        Expression stream;
        Expression batchSize;
        if (staticForm) {
          stream = mi.getArguments().get(0);
          batchSize = mi.getArguments().get(1);
        } else if (VSTREAM_PATH.matches(mi) && mi.getSelect() != null) {
          stream = mi.getSelect();
          batchSize = mi.getArguments().get(0);
        } else {
          return mi;
        }

        Optional<String> reason = notRewritten(stream, batchSize, staticForm);
        if (reason.isPresent()) {
          return SearchResult.found(mi, reason.get());
        }

        if (VSTREAM_PAR.matches(mi)) {
          maybeRemoveImport("org.higherkindedj.hkt.vstream.VStreamPar");
          maybeRemoveImport("org.higherkindedj.hkt.vstream.VStreamPar.parCollect");
        } else if (PATH_OPS.matches(mi)) {
          maybeRemoveImport("org.higherkindedj.hkt.effect.PathOps");
          maybeRemoveImport("org.higherkindedj.hkt.effect.PathOps.parCollectVStream");
        }

        JavaType.Method toListType = toList(mi.getMethodType(), stream.getType());
        return mi.withSelect(receiver(stream))
            .withTypeParameters(null)
            .withName(mi.getName().withSimpleName("toList").withType(toListType))
            .withArguments(List.of(new J.Empty(Tree.randomId(), Space.EMPTY, Markers.EMPTY)))
            .withMethodType(toListType);
      }

      @Override
      public J.MemberReference visitMemberReference(
          J.MemberReference memberRef, ExecutionContext ctx) {
        J.MemberReference ref = super.visitMemberReference(memberRef, ctx);
        JavaType.Method referenced = ref.getMethodType();
        boolean deprecated =
            VSTREAM_PAR.matches(referenced)
                || VSTREAM_PATH.matches(referenced)
                || PATH_OPS.matches(referenced);
        return deprecated ? SearchResult.found(ref, METHOD_REFERENCE) : ref;
      }

      /** Why the call is left for its author, if the rewrite could change or break it. */
      private Optional<String> notRewritten(
          Expression stream, Expression batchSize, boolean staticForm) {
        if (!hasNoEffect(batchSize)) {
          return Optional.of(COMPUTED_BATCH_SIZE);
        }
        boolean positive =
            !(batchSize instanceof J.Literal literal)
                || literal.getValue() instanceof Integer size && size > 0;
        if (batchSize instanceof J.Unary || !positive) {
          return Optional.of(NOT_POSITIVE);
        }
        if (staticForm) {
          Expression bare = unwrapped(stream);
          if (bare instanceof J.Literal
              || bare instanceof J.Lambda
              || bare instanceof J.MemberReference) {
            return Optional.of(NO_RECEIVER);
          }
          if (isInferred(bare)) {
            return Optional.of(INFERRED_STREAM);
          }
        }
        return Optional.empty();
      }

      /** A batch size whose evaluation has no effect, so dropping it changes nothing. */
      private boolean hasNoEffect(Expression batchSize) {
        return switch (batchSize) {
          case J.Literal _, J.Identifier _ -> true;
          case J.FieldAccess field -> hasNoEffect(field.getTarget());
          case J.Unary unary -> unary.getExpression() instanceof J.Literal;
          default -> false;
        };
      }

      /**
       * A stream argument whose type the call may infer from what it is assigned to: a generic
       * method called without type arguments, a diamond, or a conditional with such a branch.
       */
      private boolean isInferred(Expression stream) {
        return switch (stream) {
          case J.MethodInvocation call ->
              call.getTypeParameters() == null && isGeneric(call.getMethodType());
          case J.NewClass created ->
              created.getClazz() instanceof J.ParameterizedType parameterized
                  && parameterized.getTypeParameters() != null
                  && parameterized.getTypeParameters().stream()
                      .anyMatch(argument -> argument instanceof J.Empty);
          case J.Ternary ternary ->
              isInferred(unwrapped(ternary.getTruePart()))
                  || isInferred(unwrapped(ternary.getFalsePart()));
          default -> false;
        };
      }

      /**
       * Whether the method called declares type parameters. An invocation's own type is the one the
       * call resolved, which carries none, so the declaring type's methods are read instead.
       */
      private boolean isGeneric(JavaType.Method called) {
        if (called == null) {
          return true;
        }
        return called.getDeclaringType().getMethods().stream()
            .filter(declared -> declared.getName().equals(called.getName()))
            .filter(
                declared ->
                    declared.getParameterTypes().size() == called.getParameterTypes().size())
            .anyMatch(declared -> !declared.getDeclaredFormalTypeNames().isEmpty());
      }

      private Expression unwrapped(Expression expression) {
        Expression current = expression;
        while (current instanceof J.Parentheses<?> parentheses
            && parentheses.getTree() instanceof Expression inner) {
          current = inner;
        }
        return current;
      }

      /**
       * The stream, as the receiver of {@code toList()}: with its comments but not the space before
       * it in the old argument list, and in parentheses unless it binds tighter than a method call.
       */
      private Expression receiver(Expression stream) {
        Expression bare = stream.withPrefix(stream.getPrefix().withWhitespace(""));
        if (bare instanceof J.Identifier
            || bare instanceof J.FieldAccess
            || bare instanceof J.MethodInvocation
            || bare instanceof J.NewClass
            || bare instanceof J.ArrayAccess
            || bare instanceof J.Parentheses<?>) {
          return bare;
        }
        return new J.Parentheses<>(
            Tree.randomId(), Space.EMPTY, Markers.EMPTY, JRightPadded.build(bare));
      }

      /**
       * {@code toList()}'s type on the stream: the same result as the call it replaces, declared on
       * the stream's type, with no parameters, not static and not deprecated.
       */
      private JavaType.Method toList(JavaType.Method original, JavaType streamType) {
        if (original == null) {
          return null;
        }
        JavaType.FullyQualified declaring = TypeUtils.asFullyQualified(streamType);
        Set<Flag> flags = EnumSet.noneOf(Flag.class);
        flags.addAll(original.getFlags());
        flags.remove(Flag.Static);
        JavaType.Method renamed =
            original
                .withName("toList")
                .withParameterTypes(List.of())
                .withParameterNames(List.of())
                .withFlags(flags)
                .withAnnotations(List.of());
        return declaring == null ? renamed : renamed.withDeclaringType(declaring);
      }
    };
  }
}
