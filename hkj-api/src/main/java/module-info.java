/**
 * Provides the core API types for higher-kinded-j, including HKT encoding, optics, and functional
 * interfaces.
 */
@org.jspecify.annotations.NullMarked
@SuppressWarnings("module") // hkj-core, named by the internal export, is built after this module
module org.higherkindedj.api {
  requires static org.jspecify;
  requires transitive org.higherkindedj.annotations;

  exports org.higherkindedj.hkt;
  exports org.higherkindedj.hkt.function;
  exports org.higherkindedj.optics;
  exports org.higherkindedj.optics.indexed;

  // Shared with hkj-core so both modules build a map an optic writes by one rule; not API.
  exports org.higherkindedj.optics.internal to
      org.higherkindedj.core;
}
