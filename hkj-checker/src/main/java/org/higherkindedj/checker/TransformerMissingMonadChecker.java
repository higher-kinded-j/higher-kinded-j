// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.Map;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Detects a transformer monad constructed with no arguments (the missing-{@code Monad<F>} mistake,
 * {@code transformers/common_errors.md} section 1).
 *
 * <p>Every transformer monad's only public constructor requires the outer {@code Monad<F>} (and
 * {@code WriterTMonad} additionally a {@code Monoid<W>}). A zero-argument {@code new EitherTMonad
 * <>()} therefore cannot resolve; javac emits a raw "constructor cannot be applied to given types"
 * error and this checker adds the actionable companion alongside it.
 *
 * <p>Detection is structural (a {@code NewClassTree} with an empty argument list whose constructed
 * type is one of the known transformer monads) and so is unaffected by the constructor-resolution
 * failure. Where the constructed type resolves, the fully-qualified name is verified against the
 * HKJ package to exclude same-named user types; where it does not resolve, the well-known simple
 * name is sufficient because a zero-arg transformer-monad construction is unambiguously the
 * mistake. This preserves the no-false-positives policy.
 */
public final class TransformerMissingMonadChecker implements CheckVisitor {

  /** Transformer monad simple name to fully-qualified name. */
  private static final Map<String, String> TRANSFORMER_MONADS =
      Map.of(
          "EitherTMonad", "org.higherkindedj.hkt.either_t.EitherTMonad",
          "OptionalTMonad", "org.higherkindedj.hkt.optional_t.OptionalTMonad",
          "MaybeTMonad", "org.higherkindedj.hkt.maybe_t.MaybeTMonad",
          "ReaderTMonad", "org.higherkindedj.hkt.reader_t.ReaderTMonad",
          "StateTMonad", "org.higherkindedj.hkt.state_t.StateTMonad",
          "WriterTMonad", "org.higherkindedj.hkt.writer_t.WriterTMonad");

  private final Trees trees;
  private final Diagnostic.Kind severity;

  /**
   * Creates a checker reporting at {@link Diagnostic.Kind#ERROR}.
   *
   * @param trees the {@link Trees} utility for AST and type resolution
   */
  public TransformerMissingMonadChecker(Trees trees) {
    this(trees, Diagnostic.Kind.ERROR);
  }

  /**
   * Creates a checker reporting at the given severity.
   *
   * @param trees the Trees utility from the javac task; must not be null
   * @param severity the severity at which the companion diagnostic is reported
   */
  public TransformerMissingMonadChecker(Trees trees, Diagnostic.Kind severity) {
    this.trees = trees;
    this.severity = severity;
  }

  @Override
  public void onNewClass(NewClassTree node, TreePath path) {
    if (node.getArguments().isEmpty()) {
      String simpleName = constructedSimpleName(node.getIdentifier());
      if (simpleName != null
          && TRANSFORMER_MONADS.containsKey(simpleName)
          && isHkjType(node, path)) {
        trees.printMessage(
            severity,
            DiagnosticMessages.transformerMissingMonad(simpleName),
            node,
            path.getCompilationUnit());
      }
    }
  }

  /** Simple name of the constructed type, unwrapping generics and qualified names. */
  private String constructedSimpleName(Tree identifier) {
    Tree t = identifier;
    if (t instanceof ParameterizedTypeTree p) {
      t = p.getType();
    }
    if (t instanceof IdentifierTree id) {
      return id.getName().toString();
    }
    if (t instanceof MemberSelectTree ms) {
      return ms.getIdentifier().toString();
    }
    return null;
  }

  /**
   * Confirms the constructed type is the HKJ transformer monad. If the type resolved, its
   * fully-qualified name must match; if it did not resolve (e.g. attribution stopped at the failed
   * constructor), the simple-name match already made is accepted, since no-arg construction of a
   * type named like a transformer monad is unambiguously the documented mistake.
   */
  private boolean isHkjType(NewClassTree node, TreePath path) {
    TypeMirror t = LambdaReturns.typeOf(trees, path, node.getIdentifier());
    if (!(t instanceof DeclaredType dt) || !(dt.asElement() instanceof TypeElement te)) {
      return true; // unresolved: fall back to the simple-name match
    }
    return TRANSFORMER_MONADS.containsValue(te.getQualifiedName().toString());
  }
}
