/**
 * AssertJ assertion helpers for Higher-Kinded-J types.
 *
 * <p>Consumers can pull in every assertion in one line with Java 25's module-import syntax (JEP
 * 511, requires {@code --enable-preview}):
 *
 * <pre>{@code
 * import module org.higherkindedj.test;
 * import module org.higherkindedj.core;   // Either, Maybe, Try, IO, ...
 * }</pre>
 *
 * <p>On older Java versions, import the assertions explicitly:
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
 * }</pre>
 */
@org.jspecify.annotations.NullMarked
module org.higherkindedj.test {
  requires transitive org.higherkindedj.core;
  requires transitive org.assertj.core;

  exports org.higherkindedj.hkt.assertions;
  exports org.higherkindedj.hkt.laws;
  exports org.higherkindedj.optics.laws;
}
