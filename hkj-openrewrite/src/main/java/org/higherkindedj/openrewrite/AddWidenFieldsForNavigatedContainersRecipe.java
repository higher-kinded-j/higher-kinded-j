// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.TrailingComma;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

/**
 * Adds to {@code @GenerateFocus}'s {@code widenFields} each field that a 0.4.x navigator stepped
 * into without widening: a {@code Map}, or a third-party collection, of {@code @GenerateFocus}
 * records.
 *
 * <p>In 0.4.x, a record that wrote {@code generateNavigators = true} got a navigator over the
 * elements of such a field, and the field's method widened to reach them. From 0.5.0 a navigator
 * never changes what a field's method focuses on, so the method keeps the container whole, with no
 * navigator, until the record widens the field. Naming the field in {@code widenFields} gives back
 * the path and the navigator 0.4.x generated.
 *
 * <h2>Which fields</h2>
 *
 * <p>The recipe reads a record as 0.4.x did. It reads only a record that writes {@code
 * generateNavigators = true} and does not write {@code widenCollections = true}, since the default
 * gave no navigators in 0.4.x and {@code widenCollections} still widens every such field. Of that
 * record's components, it takes each one that {@code includeFields} and {@code excludeFields} leave
 * a navigator, whose type is one of the containers the next paragraph names, with an element (a
 * map's value) naming a record that declares no type parameters and is annotated
 * {@code @GenerateFocus}. The navigator's package, the record's own or its literal {@code
 * targetPackage}, must be able to name that record and the type of each of its components, as 0.4.x
 * required. A container with a wildcard among its own type arguments, such as {@code Map<String, ?
 * extends Position>}, was refused in 0.4.x rather than stepped into, and a container nested in
 * another, such as a {@code Map} of {@code List}s, got no navigator, so both are left whole.
 *
 * <p>The containers are those 0.4.x's zero-or-more generators recognised by name: {@code
 * java.util.Map}, and collections from Eclipse Collections, Guava, Vavr, Apache Commons and
 * PCollections. A container a project's own generator recognises is left to the note 0.5.0 writes
 * at the field. An element record whose module did not run the processor got no navigator in 0.4.x,
 * but a source tree cannot show that, so such a field is widened like the rest.
 *
 * <h2>What is marked instead</h2>
 *
 * <p>An element record with a field named like a method every {@code TraversalPath} navigator
 * inherits, such as {@code head} or {@code last}, can have no navigator from 0.5.0, which refuses
 * one where the record writes {@code generateNavigators = true}. Widening such a field would make
 * the project fail to compile, and leaving it alone would let a 0.4.x call such as {@code
 * lines().head()} reach the path's own method. So the recipe marks the component, without rewriting
 * it, asking for the element's field to be renamed while the project is on 0.4.x.
 *
 * <p>A record whose settings or field lists it cannot read, because a value is not a literal, is
 * left alone.
 *
 * <p>The recipe never removes {@code generateNavigators = true}, which since 0.5.0 asks for
 * stricter checks: removing it could let a 0.4.x navigation call such as {@code name().last()}
 * compile against the path's own {@code last()}.
 */
public class AddWidenFieldsForNavigatedContainersRecipe extends Recipe {

  private static final String GENERATE_FOCUS = "org.higherkindedj.optics.annotations.GenerateFocus";

  /**
   * Each container a 0.4.x navigator stepped into, with the index of the type argument it reads.
   */
  private static final Map<String, Integer> CONTAINERS =
      Map.ofEntries(
          Map.entry("java.util.Map", 1),
          Map.entry("org.eclipse.collections.api.list.ImmutableList", 0),
          Map.entry("org.eclipse.collections.api.list.MutableList", 0),
          Map.entry("org.eclipse.collections.api.set.ImmutableSet", 0),
          Map.entry("org.eclipse.collections.api.set.MutableSet", 0),
          Map.entry("org.eclipse.collections.api.bag.ImmutableBag", 0),
          Map.entry("org.eclipse.collections.api.bag.MutableBag", 0),
          Map.entry("org.eclipse.collections.api.set.sorted.ImmutableSortedSet", 0),
          Map.entry("org.eclipse.collections.api.set.sorted.MutableSortedSet", 0),
          Map.entry("com.google.common.collect.ImmutableList", 0),
          Map.entry("com.google.common.collect.ImmutableSet", 0),
          Map.entry("io.vavr.collection.List", 0),
          Map.entry("io.vavr.collection.Set", 0),
          Map.entry("org.apache.commons.collections4.bag.HashBag", 0),
          Map.entry("org.apache.commons.collections4.list.UnmodifiableList", 0),
          Map.entry("org.pcollections.PVector", 0),
          Map.entry("org.pcollections.PStack", 0),
          Map.entry("org.pcollections.PSet", 0),
          Map.entry("org.pcollections.PSortedSet", 0),
          Map.entry("org.pcollections.PBag", 0),
          Map.entry("org.pcollections.PMap", 1),
          Map.entry("org.pcollections.PSortedMap", 1));

  /**
   * The methods taking no arguments that a 0.5.0 {@code TraversalPath} navigator inherits, which no
   * field of the record it reaches may be named like.
   */
  static final Set<String> TRAVERSAL_PATH_METHODS =
      Set.of(
          "asFold",
          "cons",
          "each",
          "head",
          "headOption",
          "headTail",
          "init",
          "initLast",
          "last",
          "pathString",
          "segments",
          "snoc",
          "some",
          "tail",
          "toPath",
          "toTraversal");

  /** Creates a new instance of this recipe. */
  public AddWidenFieldsForNavigatedContainersRecipe() {}

  @Override
  public String getDisplayName() {
    return "Add widenFields where a 0.4.x navigator stepped into a container";
  }

  @Override
  public String getDescription() {
    return "In 0.4.x, a `@GenerateFocus` record that wrote `generateNavigators = true` got a"
        + " navigator over a `Map`, or a third-party collection, of `@GenerateFocus` records,"
        + " without `widenCollections`. From 0.5.0 a navigator never changes what a field's"
        + " method focuses on, so such a field needs `widenFields` to keep its navigator. Adds to"
        + " `widenFields` each such field that `includeFields` and `excludeFields` leave a"
        + " navigator, appending where the attribute is written. Marks, without rewriting, a"
        + " field whose elements have a field named like a path method, such as `last`, since"
        + " 0.5.0 can give it no navigator: rename that field while the project is on 0.4.x."
        + " Skips a record that sets `widenCollections = true` or leaves navigators to the"
        + " default. Keeps `generateNavigators = true`, which since 0.5.0 asks for stricter"
        + " checks.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "migration", "optics");
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return Preconditions.check(
        new UsesType<>(GENERATE_FOCUS, false),
        new JavaIsoVisitor<>() {
          @Override
          public J.ClassDeclaration visitClassDeclaration(
              J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
            if (visited.getKind() != J.ClassDeclaration.Kind.Type.Record
                || visited.getPrimaryConstructor() == null) {
              return visited;
            }
            return annotationsOf(visited)
                .filter(annotation -> TypeUtils.isOfClassType(annotation.getType(), GENERATE_FOCUS))
                .findFirst()
                .map(annotation -> migrated(visited, annotation))
                .orElse(visited);
          }
        });
  }

  /**
   * The record with each container its 0.4.x navigator stepped into added to {@code widenFields},
   * and each that 0.5.0 can give no navigator marked.
   */
  private static J.ClassDeclaration migrated(J.ClassDeclaration record, J.Annotation annotation) {
    Map<String, J.Assignment> written = attributes(annotation);
    if (!isLiteral(written.get("generateNavigators"), true)
        || (written.containsKey("widenCollections")
            && !isLiteral(written.get("widenCollections"), false))) {
      return record;
    }
    Optional<FieldLists> lists = FieldLists.read(written);
    Optional<String> companionPackage = companionPackage(record, written.get("targetPackage"));
    if (lists.isEmpty() || companionPackage.isEmpty()) {
      return record;
    }
    Map<Boolean, List<Stepped>> byClash =
        record.getPrimaryConstructor().stream()
            .flatMap(
                statement ->
                    statement instanceof J.VariableDeclarations component
                        ? Stepped.into(component, lists.get(), companionPackage.get()).stream()
                        : Stream.empty())
            .collect(Collectors.partitioningBy(stepped -> stepped.clashes().isEmpty()));
    Map<J, String> marks =
        byClash.get(false).stream().collect(Collectors.toMap(Stepped::component, Stepped::mark));
    List<String> toWiden = byClash.get(true).stream().map(Stepped::name).toList();
    J.ClassDeclaration marked =
        record.withPrimaryConstructor(
            ListUtils.map(
                record.getPrimaryConstructor(),
                statement ->
                    marks.containsKey(statement)
                        ? SearchResult.found(statement, marks.get(statement))
                        : statement));
    return toWiden.isEmpty()
        ? marked
        : replace(marked, annotation, withWidened(annotation, written, toWiden));
  }

  /** A component a 0.4.x navigator stepped into, with the record it reached. */
  private record Stepped(J.VariableDeclarations component, String name, JavaType.Class element) {

    /** The component, where the lists leave it a navigator it is not yet widened for. */
    static Optional<Stepped> into(
        J.VariableDeclarations component, FieldLists lists, String companionPackage) {
      J.VariableDeclarations.NamedVariable variable = component.getVariables().getFirst();
      String name = variable.getSimpleName();
      return lists.navigates(name) && !lists.alreadyWidened().contains(name)
          ? steppedInto(variable.getType(), companionPackage)
              .map(element -> new Stepped(component, name, element))
          : Optional.empty();
    }

    /** The names of the record's components a {@code TraversalPath} navigator inherits. */
    List<String> clashes() {
      return components(element).stream()
          .map(JavaType.Variable::getName)
          .filter(TRAVERSAL_PATH_METHODS::contains)
          .sorted()
          .toList();
    }

    /**
     * The mark for a component whose elements 0.5.0 can give no navigator. It asks for the rename
     * while the project is on 0.4.x, whose navigator has no method of the old name, so that each
     * call through it fails to compile; on 0.5.0 such a call compiles against the path's own
     * method.
     */
    String mark() {
      List<String> clashes = clashes();
      String example =
          clashes.stream().filter(clash -> !clash.equals("toPath")).findFirst().orElse("toPath");
      List<String> fields =
          clashes.stream().map(clash -> element.getClassName() + "." + clash).toList();
      String named =
          fields.size() == 1
              ? fields.getFirst()
              : String.join(", ", fields.subList(0, fields.size() - 1))
                  + " and "
                  + fields.getLast();
      return "0.5.0 gives '"
          + name
          + "' no navigator while "
          + named
          + (fields.size() == 1 ? " is named like a path method" : " are named like path methods")
          + ", and "
          + name
          + "()."
          + example
          + "() would call the path's own "
          + example
          + "(): rename "
          + (fields.size() == 1 ? "it" : "them")
          + " while on 0.4.x, where the compiler finds each call through the old name, then add '"
          + name
          + "' to widenFields";
    }
  }

  /** The annotation with the names added to its {@code widenFields}, written or not. */
  private static J.Annotation withWidened(
      J.Annotation annotation, Map<String, J.Assignment> written, List<String> names) {
    J.Assignment existing = written.get("widenFields");
    if (existing == null) {
      return annotation
          .getPadding()
          .withArguments(
              append(
                  annotation.getPadding().getArguments(),
                  List.of(widenFields(written.get("generateNavigators"), names))));
    }
    return annotation.withArguments(
        ListUtils.map(
            annotation.getArguments(),
            argument ->
                argument == existing
                    ? existing.withAssignment(withNamesAdded(existing.getAssignment(), names))
                    : argument));
  }

  /** The field lists a record's annotation writes, each read as names. */
  private record FieldLists(
      List<String> include, List<String> exclude, List<String> alreadyWidened) {

    /** The lists, or none where an entry of one is not a string literal. */
    static Optional<FieldLists> read(Map<String, J.Assignment> written) {
      Optional<List<String>> include = names(written.get("includeFields"));
      Optional<List<String>> exclude = names(written.get("excludeFields"));
      Optional<List<String>> alreadyWidened = names(written.get("widenFields"));
      return include.isPresent() && exclude.isPresent() && alreadyWidened.isPresent()
          ? Optional.of(new FieldLists(include.get(), exclude.get(), alreadyWidened.get()))
          : Optional.empty();
    }

    /** Whether the lists leave a field a navigator: a non-empty includeFields names each one. */
    boolean navigates(String field) {
      return include.isEmpty() ? !exclude.contains(field) : include.contains(field);
    }
  }

  /**
   * The record a 0.4.x navigator stepped into for a field of this type, if it did: the element of a
   * listed container, where that element is a non-generic {@code @GenerateFocus} record the
   * navigator's package can name.
   */
  private static Optional<JavaType.Class> steppedInto(JavaType type, String companionPackage) {
    if (!(type instanceof JavaType.Parameterized container)) {
      return Optional.empty(); // a raw container names no element
    }
    Integer index = CONTAINERS.get(container.getFullyQualifiedName());
    // A wildcard argument, even one bounding the key, left 0.4.x no way to write the container's
    // optic, so it refused the field rather than step in.
    if (index == null
        || index >= container.getTypeParameters().size()
        || container.getTypeParameters().stream()
            .anyMatch(AddWidenFieldsForNavigatedContainersRecipe::isWildcard)) {
      return Optional.empty();
    }
    // A generic record given arguments is a parameterised type, and a raw one declares its type
    // parameters: 0.4.x gave neither a navigator.
    return container.getTypeParameters().get(index) instanceof JavaType.Class record
            && record.getKind() == JavaType.FullyQualified.Kind.Record
            && record.getTypeParameters().isEmpty()
            && record.getAnnotations().stream()
                .anyMatch(annotation -> TypeUtils.isOfClassType(annotation, GENERATE_FOCUS))
            && reachable(record, companionPackage)
        ? Optional.of(record)
        : Optional.empty();
  }

  /**
   * Whether a navigator into the record, written into the package given, can name the record and
   * the type of each of its components. 0.4.x gave none where one of them was private, or
   * package-private in another package.
   */
  private static boolean reachable(JavaType.Class record, String companionPackage) {
    return visible(record, companionPackage)
        && components(record).stream()
            .allMatch(component -> allVisible(component.getType(), companionPackage));
  }

  /** Whether every type named in this one, its arguments and bounds included, is visible. */
  private static boolean allVisible(JavaType type, String companionPackage) {
    if (type instanceof JavaType.Array array) {
      return allVisible(array.getElemType(), companionPackage);
    }
    if (type instanceof JavaType.GenericTypeVariable variable) {
      return !isWildcard(variable)
          || variable.getBounds().stream().allMatch(bound -> allVisible(bound, companionPackage));
    }
    if (type instanceof JavaType.Parameterized parameterized) {
      return visible(parameterized, companionPackage)
          && parameterized.getTypeParameters().stream()
              .allMatch(argument -> allVisible(argument, companionPackage));
    }
    return !(type instanceof JavaType.FullyQualified declared)
        || visible(declared, companionPackage);
  }

  /** Whether a type and each type enclosing it are visible from the package given. */
  private static boolean visible(JavaType.FullyQualified type, String companionPackage) {
    for (JavaType.FullyQualified link = type; link != null; link = link.getOwningClass()) {
      if (link.hasFlags(Flag.Private)
          || !(link.hasFlags(Flag.Public) || packageOf(link).equals(companionPackage))) {
        return false;
      }
    }
    return true;
  }

  /** A record's components: its fields that are not static. */
  private static List<JavaType.Variable> components(JavaType.Class record) {
    return record.getMembers().stream().filter(field -> !field.hasFlags(Flag.Static)).toList();
  }

  /**
   * The package a record's Focus class is written into: its literal {@code targetPackage}, or the
   * record's own package. None where {@code targetPackage} is not a literal.
   */
  private static Optional<String> companionPackage(
      J.ClassDeclaration record, J.Assignment targetPackage) {
    if (targetPackage != null) {
      if (!(targetPackage.getAssignment() instanceof J.Literal literal
          && literal.getValue() instanceof String name)) {
        return Optional.empty();
      }
      if (!name.isEmpty()) {
        return Optional.of(name);
      }
    }
    return Optional.ofNullable(record.getType())
        .map(AddWidenFieldsForNavigatedContainersRecipe::packageOf);
  }

  /** The package a type is declared in, read from its outermost enclosing type. */
  private static String packageOf(JavaType.FullyQualified type) {
    JavaType.FullyQualified outermost = type;
    while (outermost.getOwningClass() != null) {
      outermost = outermost.getOwningClass();
    }
    return outermost.getPackageName();
  }

  /** Whether a type argument is a wildcard, bounded or not. */
  private static boolean isWildcard(JavaType argument) {
    return argument instanceof JavaType.GenericTypeVariable variable
        && "?".equals(variable.getName());
  }

  /** The attributes an annotation writes, by name. */
  private static Map<String, J.Assignment> attributes(J.Annotation annotation) {
    Map<String, J.Assignment> written = new LinkedHashMap<>();
    List<Expression> arguments = annotation.getArguments();
    if (arguments != null) {
      for (Expression argument : arguments) {
        if (argument instanceof J.Assignment assignment
            && assignment.getVariable() instanceof J.Identifier name) {
          written.put(name.getSimpleName(), assignment);
        }
      }
    }
    return written;
  }

  /** Whether an attribute is written as the boolean literal given. */
  private static boolean isLiteral(J.Assignment attribute, boolean value) {
    return attribute != null
        && attribute.getAssignment() instanceof J.Literal literal
        && Boolean.valueOf(value).equals(literal.getValue());
  }

  /**
   * The names a field list writes, none where it is not written, or no list at all where an entry
   * is not a string literal, since the names it holds cannot then be known.
   */
  private static Optional<List<String>> names(J.Assignment attribute) {
    if (attribute == null) {
      return Optional.of(List.of());
    }
    Expression value = attribute.getAssignment();
    List<Expression> entries =
        value instanceof J.NewArray array && array.getInitializer() != null
            ? array.getInitializer().stream().filter(entry -> !(entry instanceof J.Empty)).toList()
            : List.of(value);
    List<String> names = new ArrayList<>();
    for (Expression entry : entries) {
      if (!(entry instanceof J.Literal literal && literal.getValue() instanceof String name)) {
        return Optional.empty();
      }
      names.add(name);
    }
    return Optional.of(List.copyOf(names));
  }

  /**
   * A written field list with the names added. A single name, which {@link #names} has read as a
   * string literal, becomes an array that keeps the literal as it was written.
   */
  private static Expression withNamesAdded(Expression value, List<String> added) {
    List<Expression> literals = added.stream().map(name -> (Expression) literal(name)).toList();
    if (value instanceof J.NewArray array && array.getPadding().getInitializer() != null) {
      return array
          .getPadding()
          .withInitializer(append(array.getPadding().getInitializer(), literals));
    }
    List<Expression> elements = new ArrayList<>();
    elements.add(value.withPrefix(Space.EMPTY));
    elements.addAll(literals);
    return array(elements).withPrefix(value.getPrefix());
  }

  /**
   * A new {@code widenFields} attribute holding the names, spaced as {@code generateNavigators} is,
   * without any comment written in it.
   */
  private static J.Assignment widenFields(J.Assignment template, List<String> names) {
    Expression value =
        names.size() == 1
            ? literal(names.getFirst())
            : array(names.stream().map(name -> (Expression) literal(name)).toList());
    JLeftPadded<Expression> assignment = template.getPadding().getAssignment();
    return new J.Assignment(
        Tree.randomId(),
        Space.EMPTY,
        Markers.EMPTY,
        new J.Identifier(
            Tree.randomId(), Space.EMPTY, Markers.EMPTY, List.of(), "widenFields", null, null),
        assignment
            .withBefore(withoutComments(assignment.getBefore()))
            .withElement(value.withPrefix(withoutComments(assignment.getElement().getPrefix()))),
        null);
  }

  /**
   * A container with elements appended before whatever closes it, each laid out as its last element
   * is. An empty container, such as {@code {}}, takes them in place of its placeholder. A comment
   * after the last element stays with it, and a trailing comma moves to the new last element.
   */
  private static <T extends J> JContainer<T> append(JContainer<T> container, List<T> added) {
    List<JRightPadded<T>> elements = new ArrayList<>(container.getPadding().getElements());
    JRightPadded<T> last = elements.removeLast();
    Space before = last.getElement().getPrefix();
    Space layout = layoutOf(before);
    boolean empty = last.getElement() instanceof J.Empty;
    // After a trailing comma, what follows the last element is the comma's suffix.
    Optional<TrailingComma> comma = last.getMarkers().findFirst(TrailingComma.class);
    Space after = comma.map(TrailingComma::getSuffix).orElse(last.getAfter());
    Space kept = comma.isPresent() ? last.getAfter() : Space.EMPTY;
    Space first = empty ? (before.getComments().isEmpty() ? Space.EMPTY : before) : layout;
    Space closing = after;
    if (!empty && !after.getComments().isEmpty()) {
      closing = Space.build(after.getLastWhitespace(), List.of());
      if (comma.isEmpty() && after.getComments().stream().allMatch(Comment::isMultiline)) {
        // A block comment on the element's line stays before the comma the new elements need.
        kept = after.withComments(ListUtils.mapLast(after.getComments(), c -> c.withSuffix("")));
      } else {
        // A comment after the comma, or running to the end of the line, follows the comma.
        first =
            after.withComments(
                ListUtils.mapLast(after.getComments(), comment -> separated(comment, layout)));
      }
    }
    if (!empty) {
      elements.add(last.withAfter(kept).withMarkers(Markers.EMPTY));
    }
    Space finalClosing = closing;
    for (int i = 0; i < added.size(); i++) {
      boolean isLast = i == added.size() - 1;
      elements.add(
          new JRightPadded<>(
              added.get(i).withPrefix(i == 0 ? first : layout),
              isLast && comma.isEmpty() ? closing : Space.EMPTY,
              isLast
                  ? comma
                      .map(c -> last.getMarkers().setByType(c.withSuffix(finalClosing)))
                      .orElse(last.getMarkers())
                  : Markers.EMPTY));
    }
    return JContainer.build(container.getBefore(), elements, container.getMarkers());
  }

  /**
   * The space before an element appended after one with this prefix: a new line indented as that
   * element is, where it starts a line, and a single space otherwise.
   */
  private static Space layoutOf(Space prefix) {
    String nearest = prefix.getLastWhitespace();
    String line = nearest.contains("\n") ? nearest : prefix.getWhitespace();
    int lineBreak = line.lastIndexOf('\n');
    if (lineBreak < 0) {
      return Space.SINGLE_SPACE;
    }
    // A Windows line break is kept whole, so that the new line ends as the others do.
    int from = lineBreak > 0 && line.charAt(lineBreak - 1) == '\r' ? lineBreak - 1 : lineBreak;
    return Space.build(line.substring(from), List.of());
  }

  /**
   * A comment followed by the layout of the element after it. A comment running to the end of its
   * line keeps the line break it had where the layout has none.
   */
  private static Comment separated(Comment comment, Space layout) {
    return layout.getWhitespace().contains("\n") || comment.isMultiline()
        ? comment.withSuffix(layout.getWhitespace())
        : comment;
  }

  /** A space with its comments left out, keeping the whitespace nearest what follows it. */
  private static Space withoutComments(Space space) {
    return Space.build(space.getLastWhitespace(), List.of());
  }

  /**
   * A record's annotations, wherever it holds them: leading the declaration, or following a
   * modifier, as in {@code public @GenerateFocus(...) record}, where the modifier or the kind holds
   * them.
   */
  private static Stream<J.Annotation> annotationsOf(J.ClassDeclaration record) {
    return Stream.of(
            record.getLeadingAnnotations().stream(),
            record.getModifiers().stream().flatMap(modifier -> modifier.getAnnotations().stream()),
            record.getPadding().getKind().getAnnotations().stream())
        .flatMap(annotations -> annotations);
  }

  /** The record with one of its annotations, wherever the record holds it, replaced. */
  private static J.ClassDeclaration replace(
      J.ClassDeclaration record, J.Annotation original, J.Annotation updated) {
    UnaryOperator<List<J.Annotation>> swap =
        annotations -> ListUtils.map(annotations, a -> a == original ? updated : a);
    J.ClassDeclaration.Kind kind = record.getPadding().getKind();
    return record
        .withLeadingAnnotations(swap.apply(record.getLeadingAnnotations()))
        .withModifiers(
            ListUtils.map(
                record.getModifiers(),
                modifier -> modifier.withAnnotations(swap.apply(modifier.getAnnotations()))))
        .getPadding()
        .withKind(kind.withAnnotations(swap.apply(kind.getAnnotations())));
  }

  /** A string literal holding a field name. */
  private static J.Literal literal(String name) {
    return new J.Literal(
        Tree.randomId(),
        Space.EMPTY,
        Markers.EMPTY,
        name,
        "\"" + name + "\"",
        null,
        JavaType.Primitive.String);
  }

  /** An array initialiser of the elements given, such as {@code {"positions", "holdings"}}. */
  private static J.NewArray array(List<Expression> elements) {
    List<JRightPadded<Expression>> padded = new ArrayList<>();
    for (int i = 0; i < elements.size(); i++) {
      padded.add(
          new JRightPadded<>(
              elements.get(i).withPrefix(i == 0 ? Space.EMPTY : Space.SINGLE_SPACE),
              Space.EMPTY,
              Markers.EMPTY));
    }
    return new J.NewArray(
        Tree.randomId(),
        Space.EMPTY,
        Markers.EMPTY,
        null,
        List.of(),
        JContainer.build(Space.EMPTY, padded, Markers.EMPTY),
        new JavaType.Array(null, JavaType.Primitive.String, null));
  }
}
