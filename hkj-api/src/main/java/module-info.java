@org.jspecify.annotations.NullMarked
module org.higherkindedj.api {
  requires static org.jspecify;
  requires transitive org.higherkindedj.annotations;
    requires org.higherkindedj.api;

    exports org.higherkindedj.hkt;
  exports org.higherkindedj.hkt.function;
  exports org.higherkindedj.optics;
}
