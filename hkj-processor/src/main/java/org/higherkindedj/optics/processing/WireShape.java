// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * The wire side of a {@code @GenerateMapping} pair, abstracted over how its components are
 * enumerated, read and constructed. A record wire ({@link RecordShape}) reads components positionally
 * through their accessors and constructs via the canonical constructor; a bean-shaped wire ({@link
 * BeanShape}, issue #628) reads through getters and constructs through setters or a builder. The
 * domain side is always a record (parse assembles it with {@code Validated.fields().apply(D::new)}),
 * so only the wire is abstracted.
 *
 * <p>Classification ({@link MappingProcessor#classify}) and code generation ({@link
 * MappingProcessor#writeImpl}) speak to the wire only through this interface: component enumeration
 * and name lookup, the per-component read expression ({@link WireComponent#readFrom}), and the
 * build-method body ({@link #buildStatements}).
 */
sealed interface WireShape permits WireShape.RecordShape {

  /** The wire type element. */
  TypeElement element();

  /** The wire's components in declaration order. */
  List<WireComponent> components();

  /** The number of components. */
  default int componentCount() {
    return components().size();
  }

  /** The component names in declaration order (for diagnostics). */
  default List<String> componentNames() {
    return components().stream().map(WireComponent::name).toList();
  }

  /** The component with the given (decapitalised) name, if any. */
  default Optional<WireComponent> componentNamed(String name) {
    return components().stream().filter(c -> c.name().equals(name)).findFirst();
  }

  /**
   * The complete, terminated statements of the {@code build} method body: they construct the wire
   * value from each component's build expression (supplied by {@code valueFor}) and {@code return}
   * it. A record emits a single {@code return new W(...)}; a bean emits setter or builder statements.
   *
   * @param wireType the wire type to construct
   * @param valueFor maps each component to the expression build fills it with
   * @return terminated statement(s) suitable for {@code MethodSpec.Builder.addCode}
   */
  CodeBlock buildStatements(TypeName wireType, Function<WireComponent, CodeBlock> valueFor);

  /**
   * One wire component: its (decapitalised) name, its type, and the accessor that reads it. For a
   * record the accessor is the component name; for a bean it is the getter (for example {@code
   * getName}).
   */
  record WireComponent(String name, TypeMirror type, String accessor) {

    /** The read expression for this component from the given receiver variable. */
    CodeBlock readFrom(String receiver) {
      return CodeBlock.of("$L.$L()", receiver, accessor);
    }
  }

  /** A record wire: positional accessors and canonical-constructor construction (today's path). */
  record RecordShape(TypeElement element, List<WireComponent> components) implements WireShape {

    @Override
    public CodeBlock buildStatements(
        TypeName wireType, Function<WireComponent, CodeBlock> valueFor) {
      CodeBlock.Builder args = CodeBlock.builder();
      boolean first = true;
      for (WireComponent component : components) {
        if (!first) {
          args.add(", ");
        }
        first = false;
        args.add(valueFor.apply(component));
      }
      return CodeBlock.builder().addStatement("return new $T($L)", wireType, args.build()).build();
    }
  }
}
