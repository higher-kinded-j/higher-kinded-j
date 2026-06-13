// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 * Detects a raw {@code Kind} or {@code Kind2} used as a type — in a variable, field or parameter
 * declaration, a cast, or a method return type, whether bare ({@code Kind}) or nested ({@code
 * List<Kind>}).
 *
 * <p>Raw {@code Kind} is the one compile-silent route to a wrong-witness {@code narrow()}: {@code
 * Kind raw = OPTIONAL.widen(opt); EITHER.narrow(raw);} compiles cleanly and throws {@code
 * KindUnwrapException} (or {@code ClassCastException}) at runtime, because the lost witness type
 * argument lets a value tagged with one witness be narrowed through another. The HKJ {@code narrow}
 * helpers are typed precisely enough that the only way to feed them a mismatched witness is to drop
 * to a raw {@code Kind} first. This checker restores a compile-time signal there.
 *
 * <h2>Rule</h2>
 *
 * <p>At a variable, parameter or field declaration, a cast, or a method return type, the written
 * type tree is walked and every reference to {@code org.higherkindedj.hkt.Kind} or {@code Kind2}
 * that resolves to a <em>raw</em> type — the element declares type parameters but the use supplied
 * none — is flagged. This catches both a bare {@code Kind} and a nested one such as {@code
 * List<Kind>}, {@code Kind[]} or {@code ? extends Kind}. A properly parameterised {@code Kind<W,
 * A>} (wildcards like {@code Kind<F, ?>} included) is never flagged, and an unresolved type is
 * skipped, so the no-false-positives policy holds.
 */
public final class RawKindChecker implements CheckVisitor {

  private static final Set<String> RAW_KIND_TYPES =
      Set.of("org.higherkindedj.hkt.Kind", "org.higherkindedj.hkt.Kind2");

  private final Trees trees;
  private final Diagnostic.Kind severity;

  /**
   * Creates a checker reporting at the given severity.
   *
   * @param trees the {@link Trees} utility for AST and type resolution
   * @param severity the severity at which the diagnostic is reported
   */
  public RawKindChecker(Trees trees, Diagnostic.Kind severity) {
    this.trees = trees;
    this.severity = severity;
  }

  @Override
  public void onVariable(VariableTree node, TreePath path) {
    inspect(node.getType(), path);
  }

  @Override
  public void onTypeCast(TypeCastTree node, TreePath path) {
    inspect(node.getType(), path);
  }

  @Override
  public void onMethod(MethodTree node, TreePath path) {
    inspect(node.getReturnType(), path);
  }

  /**
   * Walks a written type tree and flags every raw {@code Kind}/{@code Kind2} within it — the type
   * itself or one nested inside a parameterised type, array or wildcard.
   */
  private void inspect(Tree typeTree, TreePath path) {
    if (typeTree == null) {
      return;
    }
    new TreePathScanner<Void, Void>() {
      @Override
      public Void visitParameterizedType(ParameterizedTypeTree node, Void unused) {
        // The head (e.g. Kind in Kind<W, A>) is parameterised and so never raw; only the type
        // arguments can hide a nested raw Kind, so recurse into those alone.
        for (Tree arg : node.getTypeArguments()) {
          scan(arg, unused);
        }
        return null;
      }

      @Override
      public Void visitIdentifier(IdentifierTree node, Void unused) {
        flagIfRawKind(node, getCurrentPath());
        return null;
      }

      @Override
      public Void visitMemberSelect(MemberSelectTree node, Void unused) {
        // A qualified type reference (e.g. fully-qualified Kind); its qualifier is a package or
        // outer type, never a raw Kind we care about, so do not recurse into it.
        flagIfRawKind(node, getCurrentPath());
        return null;
      }
    }.scan(new TreePath(path, typeTree), null);
  }

  private void flagIfRawKind(Tree typeTree, TreePath path) {
    TypeMirror tm = typeOf(path, typeTree);
    if (tm == null || tm.getKind() != TypeKind.DECLARED) {
      return; // unresolved or not a declared type: skip (no false positives)
    }
    DeclaredType dt = (DeclaredType) tm;
    if (!(dt.asElement() instanceof TypeElement te)) {
      return;
    }
    if (!RAW_KIND_TYPES.contains(te.getQualifiedName().toString())) {
      return;
    }
    // Raw iff the element declares type parameters but this use supplied none.
    if (dt.getTypeArguments().isEmpty() && !te.getTypeParameters().isEmpty()) {
      trees.printMessage(
          severity,
          DiagnosticMessages.rawKind(te.getSimpleName().toString()),
          typeTree,
          path.getCompilationUnit());
    }
  }

  private TypeMirror typeOf(TreePath path, Tree t) {
    try {
      return trees.getTypeMirror(new TreePath(path, t));
    } catch (RuntimeException e) {
      return null;
    }
  }
}
