// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.auto.service.AutoService;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.Source;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.processing.AbstractProcessor;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing annotation processor patterns.
 *
 * <p>These rules ensure processors follow consistent patterns:
 *
 * <ul>
 *   <li>Processor classes must extend AbstractProcessor
 *   <li>Processor classes must be annotated with @AutoService
 *   <li>Processor naming conventions are followed
 *   <li>SPI interfaces are properly structured
 * </ul>
 */
@DisplayName("Processor Architecture Rules")
class ProcessorArchitectureRules {

  private static final String BASE_PACKAGE = "org.higherkindedj";

  /** The lookup arm that carries a generator whose widening cannot be written. */
  private static final String REFUSED_LOOKUP =
      "org.higherkindedj.optics.processing.WideningAnalysis$SpiLookup$Refused";

  /**
   * The methods that may read a refused generator, both to ask what it would have done rather than
   * to widen with it: the analysis's walk reads its cardinality, to tell a container it would have
   * stepped into from one it leaves alone, and the navigator's turned-away check reads its focus
   * argument, to say what the navigator would have reached.
   */
  private static final Set<String> REFUSED_GENERATOR_READERS =
      Set.of("collectSpi", "widensUndenotableSpiContainer");

  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);
  }

  /**
   * Processor classes must extend AbstractProcessor.
   *
   * <p>All annotation processors should extend the standard AbstractProcessor class.
   */
  @Test
  @DisplayName("Processor classes should extend AbstractProcessor")
  void processor_classes_should_extend_abstract_processor() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Processor")
        .and()
        .resideInAPackage("..processing..")
        .and()
        .areNotInterfaces()
        .should()
        .beAssignableTo(AbstractProcessor.class)
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Processor classes should follow naming convention.
   *
   * <p>Processors should be named {Feature}Processor (e.g., LensProcessor, PrismProcessor).
   */
  @Test
  @DisplayName("Classes extending AbstractProcessor should end with 'Processor'")
  void abstract_processor_subclasses_should_end_with_processor() {
    classes()
        .that()
        .areAssignableTo(AbstractProcessor.class)
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveSimpleName("AbstractProcessor")
        .should()
        .haveSimpleNameEndingWith("Processor")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Processors should be in the processing package.
   *
   * <p>All annotation processors should reside in the optics.processing package.
   */
  @Test
  @DisplayName("Processors should reside in processing package")
  void processors_should_be_in_processing_package() {
    classes()
        .that()
        .areAssignableTo(AbstractProcessor.class)
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveSimpleName("AbstractProcessor")
        .should()
        .resideInAPackage("..optics.processing..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * SPI interfaces should be in the spi sub-package.
   *
   * <p>Service Provider Interfaces should be isolated in their own package.
   */
  @Test
  @DisplayName("SPI interfaces should be in spi package")
  void spi_interfaces_should_be_in_spi_package() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Generator")
        .and()
        .areInterfaces()
        .should()
        .resideInAPackage("..spi..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Processors should not have mutable instance fields.
   *
   * <p>Annotation processors should be stateless to ensure thread safety.
   */
  @Test
  @DisplayName("Processors should not have mutable instance fields")
  void processors_should_not_have_mutable_instance_fields() {
    classes()
        .that()
        .areAssignableTo(AbstractProcessor.class)
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveSimpleName("AbstractProcessor")
        .should(haveOnlyFinalOrStaticFields())
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Processors should be public.
   *
   * <p>Annotation processors need to be public to be discovered by the service loader.
   */
  @Test
  @DisplayName("Processors should be public classes")
  void processors_should_be_public() {
    classes()
        .that()
        .areAssignableTo(AbstractProcessor.class)
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveSimpleName("AbstractProcessor")
        .should()
        .bePublic()
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Processors must be annotated with @AutoService.
   *
   * <p>{@code @AutoService} writes the {@code META-INF/services} entry javac's processor path
   * reads, and the tests that hold {@code module-info} and Gradle's incremental registration to
   * that file start from it, so a processor without it is registered nowhere and generates nothing.
   */
  @Test
  @DisplayName("Processors should be annotated with @AutoService")
  void processors_should_be_annotated_with_auto_service() {
    classes()
        .that()
        .areAssignableTo(AbstractProcessor.class)
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should()
        .beAnnotatedWith(AutoService.class)
        .check(classes);
  }

  /**
   * Processors should not depend on runtime HKT implementations.
   *
   * <p>Processors should only depend on the API module, not specific implementations.
   */
  @Test
  @DisplayName("Processors should not depend on HKT runtime implementations")
  void processors_should_not_depend_on_hkt_implementations() {
    noClasses()
        .that()
        .areAssignableTo(AbstractProcessor.class)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..hkt.maybe..", "..hkt.either..", "..hkt.trymonad..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Generator interfaces should define required methods.
   *
   * <p>SPI generators should have supports() and generate*() methods.
   */
  @Test
  @DisplayName("Generator interfaces should have supports method")
  void generator_interfaces_should_have_supports_method() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Generator")
        .and()
        .areInterfaces()
        .should(haveMethodNamed("supports"))
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * A refused generator widens nothing.
   *
   * <p>A generator widens by handing the path an optic instance whose type arguments javac infers
   * from the field type, which a raw or wildcard-carrying container gives it no way to do (#718).
   * The SPI lookup classifies such a container as refused instead of handing out the bare
   * generator, so no site widens it by accident; what is left to guard is a site reaching into the
   * refused arm for the generator anyway. The two that may read it ask what the generator would
   * have done, never what it would emit.
   */
  @Test
  @DisplayName("A refused generator should widen nothing")
  void a_refused_generator_should_widen_nothing() {
    classes()
        .that()
        .resideInAPackage("..processing..")
        .should(readRefusedGeneratorsOnlyFrom(REFUSED_GENERATOR_READERS))
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * The refused-generator rule sees the readers it exempts.
   *
   * <p>That rule is keyed on a nested type's name and an accessor's, so renaming either would let
   * it pass with nothing to check. The two readers it allows are known, and it must find exactly
   * them.
   */
  @Test
  @DisplayName("The refused-generator rule should see its readers")
  void the_refused_generator_rule_should_see_its_readers() {
    Set<String> readers =
        StreamSupport.stream(classes.spliterator(), false)
            .flatMap(ProcessorArchitectureRules::callsAndReferencesFrom)
            .filter(access -> access.getTarget().getOwner().getName().equals(REFUSED_LOOKUP))
            .filter(access -> access.getTarget().getName().equals("generator"))
            .map(access -> access.getOrigin().getName())
            .collect(Collectors.toSet());

    assertThat(readers).isEqualTo(REFUSED_GENERATOR_READERS);
  }

  /**
   * Every generator choice must come from the registry.
   *
   * <p>{@code GeneratorRegistry} is the one place that reads {@code supports()} and {@code
   * priority()}, which is what makes priority mean the same thing on every route
   * ({@code @GenerateFocus}, {@code @GenerateTraversals}, {@code @ImportOptics}). A site looping
   * over generators itself would reintroduce first-match resolution, where a {@code
   * PRIORITY_OVERRIDE} provider wins or loses by where its {@code META-INF/services} entry lands
   * (#774).
   */
  @Test
  @DisplayName("Generator selection should go through the registry")
  void generator_selection_should_go_through_the_registry() {
    classes()
        .that()
        .resideInAPackage("..processing..")
        .should(chooseSpiGeneratorsOnlyFromTheRegistry())
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * The registry answers only the route lookups.
   *
   * <p>The Focus route's lookup classifies what it finds (#718), so a site reading a generator
   * straight from {@code GeneratorRegistry.generatorFor} would hold a bare one for a raw or
   * wildcard-carrying container and could widen it into source that cannot compile. That lookup and
   * the two non-widening route sites are the only permitted readers.
   */
  @Test
  @DisplayName("Registry reads should stay behind the route lookups")
  void registry_reads_should_stay_behind_the_route_lookups() {
    classes()
        .that()
        .resideInAPackage("..processing..")
        .should(readTheRegistryOnlyFrom(REGISTRY_READERS))
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Only the guarded lookup asks which file an element was read from.
   *
   * <p>{@code Elements.getFileObjectOf} is a default method that throws unless the compiler
   * overrides it, as javac does, so a processor calling it directly aborts the compilation wherever
   * it is left unsupported. {@code ProcessorUtils.compiledFromSource} is the one caller: it answers
   * for an element the compiler cannot place as for one read from a class file, and every other
   * question about an element's file goes through it. Collecting the callers rather than forbidding
   * the rest also fails the rule if it stops seeing the one caller it allows.
   */
  @Test
  @DisplayName("Only the guarded lookup should ask which file an element was read from")
  void only_the_guarded_lookup_should_ask_which_file_an_element_was_read_from() {
    Set<String> askers =
        StreamSupport.stream(classes.spliterator(), false)
            .flatMap(ProcessorArchitectureRules::callsAndReferencesFrom)
            .filter(access -> access.getTarget().getOwner().isAssignableTo(Elements.class))
            .filter(access -> access.getTarget().getName().equals("getFileObjectOf"))
            .map(
                access ->
                    access.getOriginOwner().getSimpleName() + "." + access.getOrigin().getName())
            .collect(Collectors.toSet());

    assertThat(askers)
        .as(
            "call ProcessorUtils.compiledFromSource instead, which answers for an element the"
                + " compiler cannot place as for one read from a class file")
        .isEqualTo(Set.of("ProcessorUtils.compiledFromSource"));
  }

  /**
   * Only the shared check asks whether generated code can name a type.
   *
   * <p>A processor that asks {@code ProcessorUtils.firstUnreachableIn} itself writes its own
   * refusal, and the refusals drift apart: one names the type where a class enclosing it is what
   * hides it, another offers a move no package can make. {@code Reachability} asks for every
   * processor, with one message and one fix line naming the class to change. Collecting the callers
   * rather than forbidding the rest also fails the rule if it stops seeing the ones it allows.
   */
  @Test
  @DisplayName(
      "Only the shared reachability check should ask whether generated code can name a type")
  void only_the_shared_reachability_check_should_ask_whether_generated_code_can_name_a_type() {
    Set<String> askers =
        StreamSupport.stream(classes.spliterator(), false)
            .flatMap(ProcessorArchitectureRules::callsAndReferencesFrom)
            .filter(
                access -> access.getTarget().getOwner().getSimpleName().equals("ProcessorUtils"))
            .filter(access -> access.getTarget().getName().equals("firstUnreachableIn"))
            .map(access -> access.getOriginOwner().getSimpleName())
            .filter(owner -> !owner.equals("ProcessorUtils"))
            .collect(Collectors.toSet());

    assertThat(askers)
        .as(
            "refuse through Reachability.check, or ask Reachability.firstHidden, so every processor"
                + " gives the same message and fix line")
        .isEqualTo(Set.of("Reachability"));
  }

  /**
   * Only the methods that need a type's string form render a type through it.
   *
   * <p>A {@code TypeMirror}'s string form keeps type-use annotations and qualifies them, so a
   * component declared {@code @Nullable String} reads as {@code
   * java.lang.@org.jspecify.annotations.Nullable String}, and a fix line built from it offers that
   * spelling as the leaf to write. {@code ProcessorUtils.simpleTypeName} and {@code
   * qualifiedTypeName} render a type for a message without it. The methods allowed here use the
   * string form as a lookup key, as the name of a type javac could not resolve, or as the fallback
   * for the kinds a structural walk does not render. Collecting the renderers rather than
   * forbidding the rest also fails the rule if it stops seeing the ones it allows.
   */
  @Test
  @DisplayName("Only the methods that need a type's string form should render a type through it")
  void only_the_methods_that_need_a_types_string_form_should_render_a_type_through_it() {
    Set<String> renderers =
        StreamSupport.stream(classes.spliterator(), false)
            .filter(JavaClass.Predicates.resideInAPackage("..processing.."))
            .flatMap(ProcessorArchitectureRules::typeRenderingsIn)
            .collect(Collectors.toSet());

    assertThat(renderers)
        .as(
            "render a type for a message through ProcessorUtils.simpleTypeName, or"
                + " qualifiedTypeName where the message names it in full, as a line the reader"
                + " pastes must, so no type-use annotation enters the text")
        .isEqualTo(STRING_FORM_READERS);
  }

  /**
   * The rendering rule sees every way a message has concatenated a type.
   *
   * <p>javac compiles a concatenated object to {@code String.valueOf(Object)}, so the rule reads
   * the type of the value from the instruction that put it on the stack. Each shape a message has
   * used is kept here, and an element or its {@code Name} written into text is not flagged.
   */
  @Test
  @DisplayName("The rendering rule should see a type however a message concatenates it")
  void the_rendering_rule_should_see_a_type_however_a_message_concatenates_it() {
    Set<String> renderers =
        typeRenderingsIn(new ClassFileImporter().importClass(RendersTypes.class))
            .collect(Collectors.toSet());

    assertThat(renderers)
        .containsExactlyInAnyOrder(
            "RendersTypes.parameter",
            "RendersTypes.returned",
            "RendersTypes.arrayElement",
            "RendersTypes.field",
            "RendersTypes.cast",
            "RendersTypes.lambda",
            "RendersTypes.explicit");
  }

  /** Each shape by which a message has written a type into text, and two that write no type. */
  @SuppressWarnings("unused") // read as bytecode by the rule's own test
  private static final class RendersTypes {
    private final TypeMirror held;

    RendersTypes(TypeMirror held) {
      this.held = held;
    }

    String parameter(DeclaredType type) {
      return "a " + type;
    }

    String returned(Element element) {
      return "a " + element.asType();
    }

    String arrayElement(TypeMirror[] pair) {
      return "a " + pair[1];
    }

    String field() {
      return "a " + held;
    }

    String cast(Object type) {
      return "a " + (TypeMirror) type;
    }

    List<String> lambda(List<TypeMirror> types) {
      return types.stream().map(type -> "a " + type).toList();
    }

    String explicit(TypeMirror type) {
      return type.toString();
    }

    String described(Element element) {
      return "a " + element;
    }

    String named(Element element) {
      return "a " + element.getSimpleName();
    }
  }

  /**
   * Custom condition checking for final or static fields only.
   *
   * @return the arch condition
   */
  private static ArchCondition<JavaClass> haveOnlyFinalOrStaticFields() {
    return new ArchCondition<>("have only final or static fields") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getFields().stream()
            .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
            .filter(field -> !field.getModifiers().contains(JavaModifier.FINAL))
            .filter(field -> !field.getName().startsWith("$")) // Exclude synthetic
            .forEach(
                field ->
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                "Processor %s has mutable instance field '%s'",
                                javaClass.getName(), field.getName()))));
      }
    };
  }

  /**
   * Custom condition that no method outside {@code allowed} looks an SPI generator up directly.
   *
   * @param allowed the names of the methods that may call the lookup
   * @return the arch condition
   */
  private static ArchCondition<JavaClass> readRefusedGeneratorsOnlyFrom(Set<String> allowed) {
    return new ArchCondition<>("read a refused generator only from " + allowed) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        callsAndReferencesFrom(javaClass)
            .filter(access -> access.getTarget().getOwner().getName().equals(REFUSED_LOOKUP))
            .filter(access -> access.getTarget().getName().equals("generator"))
            .filter(access -> !allowed.contains(access.getOrigin().getName()))
            .forEach(
                access ->
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                "%s.%s reads the generator of a refused SPI lookup. Its widening"
                                    + " cannot be written, so match the admitted arm instead;"
                                    + " a refused generator is read only to say what it would"
                                    + " have done, never to widen with.",
                                javaClass.getSimpleName(), access.getOrigin().getName()))));
      }
    };
  }

  /**
   * The methods that may render a type through its string form: the structural renderer's own
   * fallback, which strips the annotations; a visited-set key and two erased-signature keys; a
   * witness javac could not resolve, named in generated code as written; and an annotation's type
   * name compared against the recognised nullness annotations.
   */
  private static final Set<String> STRING_FORM_READERS =
      Set.of(
          "ProcessorUtils.diagnosticName",
          "ProcessorUtils.supertypeOf",
          "PathProcessor.erasedSignature",
          "PathProcessor.bridgeSignature",
          "KindFieldAnalyser.witnessNameOf",
          "NullableAnnotations.hasNullable");

  /**
   * The methods of {@code javaClass} that render a type mirror through its string form: an explicit
   * {@code toString()}, or a concatenation, which javac compiles to {@code String.valueOf(Object)}
   * on the mirror. A lambda counts as the method it is written in.
   */
  private static Stream<String> typeRenderingsIn(JavaClass javaClass) {
    return ClassFile.of().parse(bytesOf(javaClass)).methods().stream()
        .filter(
            method ->
                method.code().map(MethodCode::of).filter(MethodCode::rendersAType).isPresent())
        .map(method -> javaClass.getSimpleName() + "." + writtenIn(method));
  }

  /** The method a compiled method was written in: a lambda's body is compiled into its own. */
  private static String writtenIn(MethodModel method) {
    return method.methodName().stringValue().replaceFirst("^lambda\\$(.+)\\$\\d+$", "$1");
  }

  private static byte[] bytesOf(JavaClass javaClass) {
    Source source = javaClass.getSource().orElseThrow();
    try (InputStream in = source.getUri().toURL().openStream()) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * One method's instructions, with its local variables placed against them.
   *
   * <p>The type of a value on the stack is read from the instruction that pushed it: a call's
   * return type, a field's, a cast's target, a local's declared type, or an array local's element
   * type where a constant index loads from it. A value computed any other way is not traced: a
   * generic call reads as its erased return type, and a conditional, an assignment or a computed
   * index is not read at all. Nor does the rule see an append, a format or a method reference, none
   * of which a message uses.
   */
  private record MethodCode(
      List<Instruction> instructions, List<LocalVariable> locals, Map<Label, Integer> positions) {

    static MethodCode of(CodeModel code) {
      List<Instruction> instructions = new ArrayList<>();
      List<LocalVariable> locals = new ArrayList<>();
      Map<Label, Integer> positions = new HashMap<>();
      for (CodeElement element : code) {
        switch (element) {
          case Instruction instruction -> instructions.add(instruction);
          case LocalVariable local -> locals.add(local);
          case LabelTarget target -> positions.put(target.label(), instructions.size());
          default -> {}
        }
      }
      return new MethodCode(List.copyOf(instructions), List.copyOf(locals), Map.copyOf(positions));
    }

    boolean rendersAType() {
      return IntStream.range(0, instructions.size()).anyMatch(this::rendersATypeAt);
    }

    private boolean rendersATypeAt(int at) {
      if (!(instructions.get(at) instanceof InvokeInstruction call)) {
        return false;
      }
      String name = call.name().stringValue();
      String descriptor = call.typeSymbol().descriptorString();
      if (name.equals("toString") && descriptor.equals("()Ljava/lang/String;")) {
        return isTypeMirror(call.owner().asSymbol());
      }
      return call.owner().asInternalName().equals("java/lang/String")
          && name.equals("valueOf")
          && descriptor.equals("(Ljava/lang/Object;)Ljava/lang/String;")
          && isTypeMirror(pushedAt(at - 1));
    }

    private ClassDesc pushedAt(int at) {
      return switch (instructions.get(at)) {
        case InvokeInstruction call -> call.typeSymbol().returnType();
        case FieldInstruction field -> field.typeSymbol();
        case TypeCheckInstruction check when check.opcode() == Opcode.CHECKCAST ->
            check.type().asSymbol();
        case LoadInstruction load when load.typeKind() == TypeKind.REFERENCE -> localAt(load, at);
        case ArrayLoadInstruction load when load.typeKind() == TypeKind.REFERENCE -> {
          ClassDesc array = pushedAt(at - 2);
          yield array.isArray() ? array.componentType() : ConstantDescs.CD_Object;
        }
        default -> ConstantDescs.CD_Object;
      };
    }

    private ClassDesc localAt(LoadInstruction load, int at) {
      return locals.stream()
          .filter(local -> local.slot() == load.slot())
          .filter(local -> positions.getOrDefault(local.startScope(), 0) <= at)
          .filter(local -> at < positions.getOrDefault(local.endScope(), instructions.size()))
          .map(LocalVariable::typeSymbol)
          .findFirst()
          .orElse(ConstantDescs.CD_Object);
    }

    private static boolean isTypeMirror(ClassDesc type) {
      if (!type.isClassOrInterface() || !type.packageName().equals("javax.lang.model.type")) {
        return false;
      }
      try {
        return TypeMirror.class.isAssignableFrom(
            Class.forName(type.packageName() + "." + type.displayName()));
      } catch (ClassNotFoundException e) {
        return false;
      }
    }
  }

  /** The interface whose choosing methods only the registry may consult. */
  private static final String TRAVERSABLE_GENERATOR =
      "org.higherkindedj.optics.processing.spi.TraversableGenerator";

  /** The single home for generator selection. */
  private static final String GENERATOR_REGISTRY =
      "org.higherkindedj.optics.processing.GeneratorRegistry";

  /** The methods that may read a choice from the registry: the delegate and the two route sites. */
  private static final Set<String> REGISTRY_READERS =
      Set.of("spiLookup", "generateTraversalsFile", "createTraversal");

  /** Method calls and method references from {@code javaClass}, which decide targets alike. */
  private static Stream<JavaAccess<?>> callsAndReferencesFrom(JavaClass javaClass) {
    return Stream.concat(
        javaClass.getMethodCallsFromSelf().stream(),
        javaClass.getMethodReferencesFromSelf().stream());
  }

  private static ArchCondition<JavaClass> chooseSpiGeneratorsOnlyFromTheRegistry() {
    return new ArchCondition<>(
        "consult TraversableGenerator.supports/priority only from GeneratorRegistry") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        if (javaClass.getName().equals(GENERATOR_REGISTRY)) {
          return;
        }
        // Method references count (the deleted pre-sort was TraversableGenerator::priority), and
        // the owner is matched by assignability: a call through a concrete generator's own type
        // resolves to the subtype in bytecode.
        callsAndReferencesFrom(javaClass)
            .filter(access -> access.getTarget().getOwner().isAssignableTo(TRAVERSABLE_GENERATOR))
            .filter(access -> Set.of("supports", "priority").contains(access.getTarget().getName()))
            .forEach(
                access ->
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                "%s.%s picks a generator itself. Read the choice from"
                                    + " GeneratorRegistry.generatorFor, so that priority() keeps"
                                    + " meaning the same thing on every route.",
                                javaClass.getSimpleName(), access.getOrigin().getName()))));
      }
    };
  }

  private static ArchCondition<JavaClass> readTheRegistryOnlyFrom(Set<String> allowed) {
    return new ArchCondition<>("read GeneratorRegistry.generatorFor only from " + allowed) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        callsAndReferencesFrom(javaClass)
            .filter(access -> access.getTarget().getOwner().isAssignableTo(GENERATOR_REGISTRY))
            .filter(access -> access.getTarget().getName().equals("generatorFor"))
            .filter(access -> !allowed.contains(access.getOrigin().getName()))
            .forEach(
                access ->
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                "%s.%s reads a generator straight from the registry. Go through"
                                    + " the route's one lookup, so that a raw or"
                                    + " wildcard-carrying container is turned away rather than"
                                    + " widened into source that cannot compile.",
                                javaClass.getSimpleName(), access.getOrigin().getName()))));
      }
    };
  }

  /**
   * Custom condition that checks if a class has a method with the given name.
   *
   * @param methodName the name of the method to check for
   * @return the arch condition
   */
  private static ArchCondition<JavaClass> haveMethodNamed(String methodName) {
    return new ArchCondition<>("have method named '" + methodName + "'") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        boolean hasMethod =
            javaClass.getMethods().stream().anyMatch(method -> method.getName().equals(methodName));

        if (!hasMethod) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  String.format(
                      "Interface %s does not have required method '%s'",
                      javaClass.getName(), methodName)));
        }
      }
    };
  }
}
