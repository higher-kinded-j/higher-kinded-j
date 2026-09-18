// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The wire side of a {@code @GenerateMapping} pair, abstracted over how its components are
 * enumerated, read and constructed. A record wire ({@link RecordShape}) reads components
 * positionally through their accessors and constructs via the canonical constructor; a bean-shaped
 * wire ({@link BeanShape}) reads through getters and constructs through setters or a builder. The
 * domain side is always a record (parse assembles it with {@code
 * Validated.fields().apply(D::new)}), so only the wire is abstracted.
 *
 * <p>Classification ({@link MappingProcessor#classify}) and code generation ({@link
 * MappingProcessor#writeImpl}) speak to the wire only through this interface: component enumeration
 * and name lookup, the {@link Direction} the wire can be crossed in, the per-component read
 * expression ({@link WireComponent#readFrom}), and the build body ({@link #buildStatements}), which
 * each shape renders from one value per component that the processor supplies.
 */
sealed interface WireShape permits WireShape.RecordShape, WireShape.BeanShape {

  /** The wire type element. */
  TypeElement element();

  /**
   * The {@code build} body that constructs the wire from {@code valueFor} each component: a
   * record's canonical constructor, or a bean's writes framed by its {@link ConstructionStrategy}.
   * Asked only of a wire that is written.
   */
  CodeBlock buildStatements(TypeName wireType, Function<WireComponent, CodeBlock> valueFor);

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
   * The accessors the wire declares outside its components. A record's components are all read and
   * written, so only a bean ({@link BeanShape#unpaired}) has any.
   */
  default List<UnpairedAccessor> unpaired() {
    return List.of();
  }

  /**
   * The ways a mapping can cross this wire. A record is read through its accessors and constructed
   * through its canonical constructor, so it is always {@link Direction#BIDIRECTIONAL}; a bean
   * affords whatever its accessors allow ({@link BeanShape#direction}).
   */
  default Direction direction() {
    return Direction.BIDIRECTIONAL;
  }

  /**
   * The directions a wire affords. A bean with getters and no way to be written can be parsed but
   * never built, and one that can be written but offers no getters can be built but never parsed,
   * so each maps one way, and its Impl carries only that way's surface.
   */
  enum Direction {
    /** Read and written: {@code build} and {@code parse}. */
    BIDIRECTIONAL,
    /** Read only: {@code parse}, and no {@code build}. */
    PARSE_ONLY,
    /** Written only: {@code build}, and no {@code parse}. */
    BUILD_ONLY
  }

  /**
   * One wire component: its (decapitalised) name, its type, and the accessor that reads it. For a
   * record the accessor is the component name; for a bean it is the getter (for example {@code
   * getName}), absent on a bean that is only ever written.
   */
  record WireComponent(String name, TypeMirror type, Optional<String> accessor) {

    /**
     * The read expression for this component from the given receiver variable. Asked only of a wire
     * that is read, where every component has its accessor.
     */
    CodeBlock readFrom(String receiver) {
      return CodeBlock.of("$L.$L()", receiver, accessor.orElseThrow());
    }
  }

  /** A record wire: positional accessors and canonical-constructor construction. */
  record RecordShape(TypeElement element, List<WireComponent> components) implements WireShape {

    /** The record build body: {@code return new W(v0, v1, ...)} in component order. */
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
   * A bean-shaped wire: components are read through getters and written through the {@link
   * ConstructionStrategy}. Reads are null-hostile at parse time (an unset bean property is null),
   * which the mapping processor guards; only the construction differs from a record.
   *
   * <p>{@code direction} is the reading the analyser chose, and the rest of the shape agrees with
   * it: a parse-only bean has no {@code strategy} and no write sites, and a build-only one has no
   * getters. The analyser selects the two-way reading whenever any property allows it, so a bean is
   * one-directional only when nothing at all crosses the other way.
   *
   * <p>{@code unpaired} lists the accessors a two-way bean declares outside its properties: a
   * getter nothing writes, or a writer nothing reads. The mapping leaves them out, and the
   * processor refuses one wherever leaving it out would lose a value. A one-directional bean has
   * none, every accessor it declares being a property.
   */
  record BeanShape(
      TypeElement element,
      List<BeanProperty> properties,
      Optional<ConstructionStrategy> strategy,
      Direction direction,
      List<UnpairedAccessor> unpaired)
      implements WireShape {

    public BeanShape {
      properties = List.copyOf(properties);
      unpaired = List.copyOf(unpaired);
    }

    @Override
    public List<WireComponent> components() {
      return properties.stream().map(BeanProperty::asWireComponent).toList();
    }

    /**
     * The bean build body: the strategy's frame, and between it one write per property, each
     * carrying its value whatever that is, a {@code null} included. No write is skipped, so a
     * property written through a setter or a builder setter never keeps a default the bean or its
     * builder started with.
     */
    @Override
    public CodeBlock buildStatements(
        TypeName wireType, Function<WireComponent, CodeBlock> valueFor) {
      // Only a bean that is written reaches a build body, so it has its strategy and write sites.
      ConstructionStrategy frame = strategy.orElseThrow();
      CodeBlock.Builder body = CodeBlock.builder().add(frame.prologue(wireType));
      for (BeanProperty property : properties) {
        CodeBlock value = valueFor.apply(property.asWireComponent());
        body.addStatement("$L", property.write().orElseThrow().write(frame.receiver(), value));
      }
      return body.add(frame.epilogue()).build();
    }
  }

  /**
   * An accessor a two-way bean declares with no partner in the other direction: its property name,
   * the method as declared, the type it reads or writes, the role it plays, and the supertype it is
   * inherited from, when it is not declared on the bean (or builder) itself.
   */
  record UnpairedAccessor(
      String name, String method, TypeMirror type, Role role, Optional<TypeElement> inheritedFrom) {

    /** The roles an accessor plays on a bean, each as a diagnostic names it. */
    enum Role {
      /** A {@code getX} or {@code isX} getter on the bean. */
      GETTER("getter"),
      /** A {@code setX} setter on the bean. */
      SETTER("setter"),
      /** A one-argument method on the bean's builder. */
      BUILDER_SETTER("builder setter");

      private final String label;

      Role(String label) {
        this.label = label;
      }

      String label() {
        return label;
      }
    }

    /** Whether this accessor reads the bean, rather than writing it. */
    boolean reads() {
      return role == Role.GETTER;
    }

    /**
     * The accessor as a diagnostic spells it, with where it is declared when it is inherited:
     * {@code getName()}, {@code setName(String) (declared on 'Base')}.
     */
    String signature() {
      return spelt(method) + BeanPropertyAnalyser.declaredOn(inheritedFrom);
    }

    /**
     * This accessor as a diagnostic spells it, renamed to speak for {@code property}: the prefix it
     * was declared with ({@code get}, {@code is}, {@code set}, or none for a property-named builder
     * method) is kept.
     */
    String renamedFor(String property) {
      int prefix = method.length() - name.length();
      return spelt(
          prefix == 0
              ? property
              : method.substring(0, prefix) + BeanPropertyAnalyser.accessorSuffix(property));
    }

    private String spelt(String methodName) {
      return reads()
          ? methodName + "()"
          : methodName + "(" + ProcessorUtils.simpleTypeName(type) + ")";
    }
  }

  /**
   * One bean property: its (decapitalised) name, type, the getter that reads it and the {@link
   * WriteSite} that writes it (a setter, a builder setter, or a JAXB collection getter). A bean
   * read one way only has no getter, or no write site, on any of its properties.
   */
  record BeanProperty(
      String name, TypeMirror type, Optional<String> getter, Optional<WriteSite> write) {

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

    /**
     * {@code receiver.setX(value)} — a setter or, in a builder frame, a builder setter. It keeps
     * the method as declared, so what its parameter says about {@code null} can be read.
     */
    record Setter(ExecutableElement method) implements WriteSite {
      @Override
      public CodeBlock write(String receiver, CodeBlock value) {
        return CodeBlock.of("$L.$L($L)", receiver, method.getSimpleName(), value);
      }

      /** The one parameter the write hands its value to. */
      VariableElement parameter() {
        return method.getParameters().getFirst();
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

  /**
   * How a bean value is constructed: the target variable each property writes into ({@link
   * #receiver}), and the framing {@link #prologue} and {@link #epilogue} statements, between which
   * {@link BeanShape#buildStatements} writes each property.
   */
  sealed interface ConstructionStrategy
      permits ConstructionStrategy.NoArgsSetters, ConstructionStrategy.Builder {

    /** The variable each property write targets (the bean instance or the builder). */
    String receiver();

    /** The opening statement that creates the target. */
    CodeBlock prologue(TypeName wireType);

    /** The closing statement that returns the built wire. */
    CodeBlock epilogue();

    /**
     * Public no-args constructor plus per-property writes: {@code var w = new W(); w.setX(...);
     * return w;}. A property may write through a setter or, for a getter-only {@code List}, the
     * JAXB collection-getter convention ({@code w.getItems().addAll(...)}), which assumes the
     * getter returns a mutable, live list.
     */
    record NoArgsSetters() implements ConstructionStrategy {
      @Override
      public String receiver() {
        return "wire";
      }

      @Override
      public CodeBlock prologue(TypeName wireType) {
        return CodeBlock.builder().addStatement("$T wire = new $T()", wireType, wireType).build();
      }

      @Override
      public CodeBlock epilogue() {
        return CodeBlock.builder().addStatement("return wire").build();
      }
    }

    /**
     * A builder: {@code var b = W.builder(); b.name(...); return b.build();}. {@code factory} is
     * the static builder factory ({@code builder} or {@code newBuilder}) and {@code buildMethod}
     * the builder's terminal method; each property writes through its builder setter.
     */
    record Builder(String factory, String buildMethod) implements ConstructionStrategy {
      @Override
      public String receiver() {
        return "b";
      }

      @Override
      public CodeBlock prologue(TypeName wireType) {
        return CodeBlock.builder().addStatement("var b = $T.$L()", wireType, factory).build();
      }

      @Override
      public CodeBlock epilogue() {
        return CodeBlock.builder().addStatement("return b.$L()", buildMethod).build();
      }
    }
  }
}
