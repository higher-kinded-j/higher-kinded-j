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
 * enumerated, read and constructed. A record wire ({@link RecordShape}) reads components
 * positionally through their accessors and constructs via the canonical constructor; a bean-shaped
 * wire ({@link BeanShape}, issue #628) reads through getters and constructs through setters or a
 * builder. The domain side is always a record (parse assembles it with {@code
 * Validated.fields().apply(D::new)}), so only the wire is abstracted.
 *
 * <p>Classification ({@link MappingProcessor#classify}) and code generation ({@link
 * MappingProcessor#writeImpl}) speak to the wire only through this interface: component enumeration
 * and name lookup, the per-component read expression ({@link WireComponent#readFrom}), and the
 * build-method body ({@link #buildStatements}).
 */
sealed interface WireShape permits WireShape.RecordShape, WireShape.BeanShape {

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
   * it. A record emits a single {@code return new W(...)}; a bean emits setter or builder
   * statements.
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

  /**
   * A bean-shaped wire (issue #628): components are read through getters and written through the
   * {@link ConstructionStrategy}. Reads are null-hostile at parse time (an unset bean property is
   * null), which the mapping processor guards; only the construction differs from a record.
   */
  record BeanShape(
      TypeElement element, List<BeanProperty> properties, ConstructionStrategy strategy)
      implements WireShape {

    @Override
    public List<WireComponent> components() {
      return properties.stream().map(BeanProperty::asWireComponent).toList();
    }

    @Override
    public CodeBlock buildStatements(
        TypeName wireType, Function<WireComponent, CodeBlock> valueFor) {
      return strategy.buildStatements(wireType, properties, valueFor);
    }
  }

  /**
   * One bean property: its (decapitalised) name, type, the getter that reads it and the {@link
   * WriteSite} that writes it (a setter, a builder setter, or a JAXB collection getter).
   */
  record BeanProperty(String name, TypeMirror type, String getter, WriteSite write) {

    WireComponent asWireComponent() {
      return new WireComponent(name, type, getter);
    }
  }

  /** How a single bean property is written into a target (a bean instance or a builder). */
  sealed interface WriteSite permits WriteSite.Setter, WriteSite.CollectionAdd {

    /**
     * A statement writing {@code value} into {@code receiver} (the bean {@code wire} or builder).
     */
    CodeBlock write(String receiver, CodeBlock value);

    /** {@code receiver.setX(value)} — a setter or, in a builder frame, a builder setter. */
    record Setter(String method) implements WriteSite {
      @Override
      public CodeBlock write(String receiver, CodeBlock value) {
        return CodeBlock.of("$L.$L($L)", receiver, method, value);
      }
    }

    /** {@code receiver.getX().addAll(value)} — the JAXB collection-getter convention. */
    record CollectionAdd(String getter) implements WriteSite {
      @Override
      public CodeBlock write(String receiver, CodeBlock value) {
        return CodeBlock.of("$L.$L().addAll($L)", receiver, getter, value);
      }
    }
  }

  /** How a bean value is constructed from its per-property build expressions. */
  sealed interface ConstructionStrategy
      permits ConstructionStrategy.NoArgsSetters, ConstructionStrategy.Builder {

    CodeBlock buildStatements(
        TypeName wireType,
        List<BeanProperty> properties,
        Function<WireComponent, CodeBlock> valueFor);

    /**
     * Public no-args constructor plus per-property writes: {@code var w = new W(); w.setX(...);
     * return w;}. A property may write through a setter or, for a getter-only {@code List}, the
     * JAXB collection-getter convention ({@code w.getItems().addAll(...)}), which assumes the
     * getter returns a mutable, live list.
     */
    record NoArgsSetters() implements ConstructionStrategy {

      @Override
      public CodeBlock buildStatements(
          TypeName wireType,
          List<BeanProperty> properties,
          Function<WireComponent, CodeBlock> valueFor) {
        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("$T wire = new $T()", wireType, wireType);
        for (BeanProperty property : properties) {
          body.addStatement(
              "$L", property.write().write("wire", valueFor.apply(property.asWireComponent())));
        }
        body.addStatement("return wire");
        return body.build();
      }
    }

    /**
     * A builder: {@code var b = W.builder(); b.name(...); return b.build();}. {@code factory} is
     * the static builder factory ({@code builder} or {@code newBuilder}) and {@code buildMethod}
     * the builder's terminal method; each property writes through its builder setter.
     */
    record Builder(String factory, String buildMethod) implements ConstructionStrategy {

      @Override
      public CodeBlock buildStatements(
          TypeName wireType,
          List<BeanProperty> properties,
          Function<WireComponent, CodeBlock> valueFor) {
        CodeBlock.Builder body = CodeBlock.builder();
        body.addStatement("var b = $T.$L()", wireType, factory);
        for (BeanProperty property : properties) {
          body.addStatement(
              "$L", property.write().write("b", valueFor.apply(property.asWireComponent())));
        }
        body.addStatement("return b.$L()", buildMethod);
        return body.build();
      }
    }
  }
}
