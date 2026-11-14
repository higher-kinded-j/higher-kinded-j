// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.CodeBlock;
import java.util.HashSet;
import java.util.Set;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The locals a {@code fields()} ladder's copying legs are bound to, ahead of the ladder: {@code var
 * hkj$tags = hkj$allPresent(hkj$copyOf(wire.tags()));}, then {@code .field("tags", hkj$tags)}.
 *
 * <p>A leg that copies a container calls a copy helper typed by what it is handed, inside a null
 * guard. Nested in a long chain of {@code .field(...)} calls, a nullness checker infers that call
 * again for every call the chain holds, which grows past practical in a ladder of copied
 * containers; bound ahead, each is inferred once. The guard declares the {@code Validated} it
 * returns, so the local needs no type written. Any other leg stays in the ladder, where the
 * ladder's parameter types it, as a {@code Validated.validNel(...)} needs. The bound values are
 * read in leg order, ahead of the legs left in the ladder; every leg is evaluated whichever fails,
 * so only the order of the wire's reads moves, and a getter a mapping reads has no effect to
 * reorder. A local's name is the component's under the {@code hkj$} prefix, which no lambda
 * parameter of the ladder takes.
 */
final class LegLocals {

  private final Set<String> taken = new HashSet<>();
  private final CodeBlock.Builder statements = CodeBlock.builder();

  /**
   * Binds {@code value}, the leg of {@code component}, to a fresh local.
   *
   * @param component the component the leg parses
   * @param value the leg's {@code Validated} value
   * @return the local's name, to hand the ladder
   */
  CodeBlock bind(String component, CodeBlock value) {
    String name = ProcessorUtils.freeName("hkj$" + component, taken);
    statements.addStatement("var $L = $L", name, value);
    return CodeBlock.of("$L", name);
  }

  /** The names bound so far, which a chunked ladder's own locals keep clear of. */
  Set<String> names() {
    return Set.copyOf(taken);
  }

  /** The bindings, in the order the legs were bound, to precede the ladder. */
  CodeBlock statements() {
    return statements.build();
  }
}
