module org.higherkindedj.api {
  requires transitive org.jspecify;
  requires transitive org.higherkindedj.annotations;

  exports org.higherkindedj.hkt;
  exports org.higherkindedj.hkt.function;
  exports org.higherkindedj.optics;
}
