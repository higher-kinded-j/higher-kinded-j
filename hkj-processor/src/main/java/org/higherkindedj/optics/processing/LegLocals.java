// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.CodeBlock;
import java.util.HashSet;
import java.util.Set;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The locals the copies a {@code fields()} ladder's legs read are bound to, ahead of the ladder:
 * {@code var hkj$tags = hkj$copyOf(wire.tags());}, then {@code .field("tags",
 * hkj$allPresent(hkj$tags))}.
 *
 * <p>A copy helper is typed by what it is handed. Nested in a long chain of {@code .field(...)}
 * calls, a nullness checker infers that call again for every call the chain holds, which grows past
 * practical in a ladder of copied containers; bound ahead, each is inferred once. The local needs
 * no type written, since the copy's type is the read's own, and the leg stays in the ladder, which
 * types it as before.
 *
 * <p>The copies are made in leg order, ahead of the ladder's reads of the other legs, so the wire's
 * getters are read in another order than the components'. Only a getter that does more than answer,
 * or throws, can tell. A copy that throws, as one of a map whose keys {@code equals} merges does,
 * throws before the legs ahead of it are read; a leg that fails is still a {@code FieldError} that
 * accumulates. A local's name is the component's under the {@code hkj$} prefix, kept clear of every
 * name the method already has in scope.
 */
final class LegLocals {

  private final Set<String> taken;
  private final CodeBlock.Builder statements = CodeBlock.builder();

  /**
   * Locals for a method in which {@code inScope} are already named: its parameters, and the
   * components whose names its lambdas and locals take.
   *
   * @param inScope the names a local must not take
   */
  LegLocals(Set<String> inScope) {
    this.taken = new HashSet<>(inScope);
  }

  /**
   * Binds {@code value}, the copy the leg of {@code component} reads, to a fresh local.
   *
   * @param component the component the leg parses
   * @param value the copy of the component's read
   * @return the local's name, for the leg to read
   */
  CodeBlock bind(String component, CodeBlock value) {
    String name = ProcessorUtils.freeName("hkj$" + component, taken);
    statements.addStatement("var $L = $L", name, value);
    return CodeBlock.of("$L", name);
  }

  /** The names in scope and bound so far, which a chunked ladder's own locals keep clear of. */
  Set<String> names() {
    return Set.copyOf(taken);
  }

  /** The bindings, in the order the legs were bound, to precede the ladder. */
  CodeBlock statements() {
    return statements.build();
  }
}
