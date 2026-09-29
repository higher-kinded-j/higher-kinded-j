// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
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
  CodeBlock buildStatements(TypeName wireType, Function<WireComponent, WireValue> valueFor);

  /**
   * The value {@code build} hands one wire component, and, for a domain {@code Optional} bridged to
   * it, the condition under which the domain holds one: {@code value} is {@code null} when it does
   * not. A record takes the {@code null}, and so does a bean property, except where the property
   * {@linkplain BeanProperty#presence tracks its presence}: there the write is made only when the
   * value is present, so the property stays unset.
   *
   * @param value the value written
   * @param present the condition under which {@code value} is not {@code null}, for a bridged one
   */
  record WireValue(CodeBlock value, Optional<CodeBlock> present) {}

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
   * Whether the component named {@code name} is read and never written, on a wire that is otherwise
   * written both ways: a bean's read-only property ({@link BeanShape#readingAlso}). A record's
   * components are all written.
   */
  default boolean readOnly(String name) {
    return false;
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
   * One wire component: its (decapitalised) name, its type, the accessor that reads it, and, for a
   * field of a protobuf-java message, what the message says of it ({@code field}). For a record the
   * accessor is the component name; for a bean it is the getter (for example {@code getName}),
   * absent on a bean that is only ever written.
   */
  record WireComponent(
      String name, TypeMirror type, Optional<String> accessor, Optional<MessageField> field) {

    /** A component of a record, or of a bean that is not a protobuf-java message. */
    WireComponent(String name, TypeMirror type, Optional<String> accessor) {
      this(name, type, accessor, Optional.empty());
    }

    /**
     * The method that tells whether this component is set, when it tracks its presence: a message
     * field's {@code hasX()}.
     */
    Optional<String> presence() {
      return field.flatMap(MessageField::presence);
    }

    /**
     * Whether the read of this component can be {@code null}: a reference always can, and a
     * primitive can when the component tracks its presence, since {@link #readFrom} reads it as
     * {@code null} when it is unset.
     */
    boolean readsNull() {
      return !type.getKind().isPrimitive() || presence().isPresent();
    }

    /**
     * The read expression for this component from the given receiver variable. Asked only of a wire
     * that is read, where every component has its accessor. A component that tracks its presence
     * reads {@code null} when it is unset, as an unset bean property does, so the rules for a
     * {@code null} read apply to it: a domain {@code Optional} reads it as empty, and any other
     * component as missing. The read of a primitive one is then of its boxed type, while {@link
     * #type} stays the type its accessor declares.
     */
    CodeBlock readFrom(String receiver) {
      CodeBlock read = CodeBlock.of("$L.$L()", receiver, accessor.orElseThrow());
      return presence()
          .map(has -> CodeBlock.of("($L.$L() ? $L : null)", receiver, has, read))
          .orElse(read);
    }
  }

  /** A record wire: positional accessors and canonical-constructor construction. */
  record RecordShape(TypeElement element, List<WireComponent> components) implements WireShape {

    /**
     * The record build body: {@code return new W(v0, v1, ...)} in component order, each value
     * handed over whatever it is, a {@code null} included.
     */
    @Override
    public CodeBlock buildStatements(
        TypeName wireType, Function<WireComponent, WireValue> valueFor) {
      CodeBlock.Builder args = CodeBlock.builder();
      boolean first = true;
      for (WireComponent component : components) {
        if (!first) {
          args.add(", ");
        }
        first = false;
        args.add(valueFor.apply(component).value());
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
   * none, every accessor it declares being a property. An openapi-generator {@code JsonNullable}
   * companion is in neither list: the analyser leaves it out, since the plain property beside it
   * carries its value.
   *
   * <p>A two-way bean may also carry read-only properties, named in {@code readOnlyNames} ({@link
   * #readingAlso}): getters a {@code @ReadOnly} marker reads as properties, each with no write
   * site. {@code parse} reads them like any other, and {@code build} writes every other property
   * and leaves them as the bean starts them.
   *
   * <p>A protobuf-java message is a two-way builder bean whose properties are its fields, each
   * carrying what the message says of it ({@link MessageField}). The accessors it declares beside
   * them, such as {@code getXBytes()} or {@code getXCount()}, are none of its fields, and it
   * records none of them as unpaired.
   */
  record BeanShape(
      TypeElement element,
      List<BeanProperty> properties,
      Optional<ConstructionStrategy> strategy,
      Direction direction,
      List<UnpairedAccessor> unpaired,
      Set<String> readOnlyNames)
      implements WireShape {

    public BeanShape {
      properties = List.copyOf(properties);
      unpaired = List.copyOf(unpaired);
      readOnlyNames = Set.copyOf(readOnlyNames);
    }

    /** A bean as the analyser reads it, before any marker makes a property read-only. */
    BeanShape(
        TypeElement element,
        List<BeanProperty> properties,
        Optional<ConstructionStrategy> strategy,
        Direction direction,
        List<UnpairedAccessor> unpaired) {
      this(element, properties, strategy, direction, unpaired, Set.of());
    }

    @Override
    public List<WireComponent> components() {
      return properties.stream().map(BeanProperty::asWireComponent).toList();
    }

    /**
     * The bean build body: the strategy's frame, and between it one write per property, each
     * carrying its value whatever that is, a {@code null} included. So a property written through a
     * setter or a builder setter never keeps a default the bean or its builder started with. Only a
     * read-only property is skipped, since nothing writes it, and so is an absent value of a
     * property that {@linkplain BeanProperty#presence tracks its presence}: a protobuf-java builder
     * starts every field unset, and its setters refuse a {@code null}.
     */
    @Override
    public CodeBlock buildStatements(
        TypeName wireType, Function<WireComponent, WireValue> valueFor) {
      // Only a bean that is written reaches a build body, so it has its strategy and write sites.
      ConstructionStrategy frame = strategy.orElseThrow();
      CodeBlock.Builder body = CodeBlock.builder().add(frame.prologue(wireType));
      for (BeanProperty property : properties) {
        if (readOnlyNames.contains(property.name())) {
          continue;
        }
        WireValue value = valueFor.apply(property.asWireComponent());
        CodeBlock write = property.write().orElseThrow().write(frame.receiver(), value.value());
        Optional<CodeBlock> present = value.present().filter(_ -> property.presence().isPresent());
        if (present.isPresent()) {
          body.beginControlFlow("if ($L)", present.get())
              .addStatement("$L", write)
              .endControlFlow();
        } else {
          body.addStatement("$L", write);
        }
      }
      return body.add(frame.epilogue()).build();
    }

    /**
     * This bean with the getters named in {@code names} read as read-only properties: an unpaired
     * getter joins the properties, leaving {@link #unpaired}, and a getter-only {@code List} loses
     * the {@code getX().addAll(...)} write the JAXB convention gave it. The names are those a
     * {@code @ReadOnly} marker binds, each of which names one or the other, never a property with a
     * writer of its own. A bean left with nothing to write is read parse-only, as one declaring
     * nothing that writes it is.
     */
    BeanShape readingAlso(Set<String> names) {
      List<UnpairedAccessor> getters =
          unpaired.stream()
              .filter(accessor -> accessor.reads() && names.contains(accessor.name()))
              .toList();
      List<BeanProperty> read =
          Stream.concat(
                  properties.stream()
                      .map(
                          property ->
                              names.contains(property.name())
                                  ? new BeanProperty(
                                      property.name(),
                                      property.type(),
                                      property.getter(),
                                      Optional.empty(),
                                      property.field())
                                  : property),
                  getters.stream()
                      .map(
                          getter ->
                              new BeanProperty(
                                  getter.name(),
                                  getter.type(),
                                  Optional.of(getter.method()),
                                  Optional.empty())))
              .toList();
      List<UnpairedAccessor> left =
          unpaired.stream().filter(accessor -> !getters.contains(accessor)).toList();
      // A protobuf-java message with no fields has nothing to write, and is written all the same.
      return !read.isEmpty() && read.stream().allMatch(property -> property.write().isEmpty())
          ? new BeanShape(element, read, Optional.empty(), Direction.PARSE_ONLY, List.of())
          : new BeanShape(element, read, strategy, direction, left, names);
    }

    /** Whether the property named {@code name} is read-only: read, on a bean that is written. */
    @Override
    public boolean readOnly(String name) {
      return readOnlyNames.contains(name);
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
   * WriteSite} that writes it (a setter, a builder setter, a {@code @Singular} collection setter,
   * or a JAXB collection getter). A bean read one way only has no getter, or no write site, on any
   * of its properties. A field of a protobuf-java message also carries what the message says of it
   * ({@code field}).
   */
  record BeanProperty(
      String name,
      TypeMirror type,
      Optional<String> getter,
      Optional<WriteSite> write,
      Optional<MessageField> field) {

    /** A property of a bean that is not a protobuf-java message. */
    BeanProperty(String name, TypeMirror type, Optional<String> getter, Optional<WriteSite> write) {
      this(name, type, getter, write, Optional.empty());
    }

    /**
     * The method that tells whether this property is set, when it tracks its presence: a message
     * field's {@code hasX()}.
     */
    Optional<String> presence() {
      return field.flatMap(MessageField::presence);
    }

    WireComponent asWireComponent() {
      return new WireComponent(name, type, getter, field);
    }
  }

  /**
   * What a protobuf-java message declares of one of its fields besides the accessors that read and
   * write it.
   *
   * <p>{@code presence} is the {@code hasX()} method that tells whether the field is set. A message
   * field has one, and so does a scalar declared {@code optional}, every field of a proto2 message,
   * and every member of a oneof. A proto3 scalar declared without {@code optional} has none: unset,
   * it reads its default, which cannot be told apart from the default set. Nor has a repeated or
   * map field, which an empty collection leaves unset.
   *
   * <p>{@code oneof} is the oneof the field belongs to, as its case getter names it ({@code
   * getKindCase()} names {@code kind}): setting one member clears the others.
   *
   * @param presence the {@code hasX()} method, for a field that tracks its presence
   * @param oneof the oneof the field is a member of, if any
   */
  record MessageField(Optional<String> presence, Optional<String> oneof) {}

  /** How a single bean property is written into a target (a bean instance or a builder). */
  sealed interface WriteSite
      permits WriteSite.Setter, WriteSite.SingularCollection, WriteSite.CollectionAdd {

    /**
     * A statement writing {@code value} into {@code receiver} (the bean {@code wire} or builder).
     */
    CodeBlock write(String receiver, CodeBlock value);

    /**
     * {@code receiver.setX(value)} — a setter or, in a builder frame, a builder setter. It keeps
     * the method as declared, so what its parameter says about {@code null} can be read. The
     * element is valid only within the round that analysed the bean, and compares by identity, so a
     * {@code Setter} is never compared or kept across rounds. A protobuf-java message's repeated or
     * map field is written the same way, through its builder's {@code addAllX} or {@code putAllX},
     * which on the fresh builder {@code build} starts from sets the field whole.
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

    /**
     * {@code receiver.tags(value)} — the collection setter of a Lombok {@code @Singular} builder,
     * which takes any collection of the element type and adds it to what the builder holds. {@code
     * build} calls it once on a fresh builder, so the collection is written whole. The builder
     * builds an empty collection when nothing is added, and by default refuses a {@code null}, so
     * the property has no absent state: neither the {@code Optional} bridge nor a sparse update can
     * use it. As with a {@link Setter}, the element is valid only within the round that analysed
     * the bean.
     */
    record SingularCollection(ExecutableElement method) implements WriteSite {
      @Override
      public CodeBlock write(String receiver, CodeBlock value) {
        return CodeBlock.of("$L.$L($L)", receiver, method.getSimpleName(), value);
      }
    }

    /**
     * {@code receiver.getX().addAll(value)} — the JAXB collection-getter convention. A getter
     * declared nullable ({@code nullable}), as openapi-generator declares a {@code readOnly}
     * array's, may answer {@code null} where JAXB's creates the list, so its write fills the list
     * only when there is one: {@code if (receiver.getX() != null) receiver.getX().addAll(value)}.
     */
    record CollectionAdd(String getter, boolean nullable) implements WriteSite {
      @Override
      public CodeBlock write(String receiver, CodeBlock value) {
        CodeBlock fill = CodeBlock.of("$L.$L().addAll($L)", receiver, getter, value);
        return nullable ? CodeBlock.of("if ($L.$L() != null) $L", receiver, getter, fill) : fill;
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
     * the static builder factory ({@code builder} or {@code newBuilder}), {@code buildMethod} the
     * builder's terminal method, and {@code builderType} the builder as the factory returns it,
     * whose methods the Impl calls; each property writes through its builder setter.
     */
    record Builder(String factory, String buildMethod, DeclaredType builderType)
        implements ConstructionStrategy {
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
