@org.jspecify.annotations.NullMarked
module org.higherkindedj.core {
  // This module requires jspecify and makes it available to its users.
  requires transitive org.jspecify;
  requires transitive org.higherkindedj.api;

  exports org.higherkindedj.hkt.constant;
  exports org.higherkindedj.hkt.tuple;
  exports org.higherkindedj.hkt.either;
  exports org.higherkindedj.hkt.either_t;
  exports org.higherkindedj.hkt.exception;
  exports org.higherkindedj.hkt.expression;
  exports org.higherkindedj.hkt.free;
  exports org.higherkindedj.hkt.future;
  exports org.higherkindedj.hkt.func;
  exports org.higherkindedj.hkt.id;
  exports org.higherkindedj.hkt.io;
  exports org.higherkindedj.hkt.lazy;
  exports org.higherkindedj.hkt.list;
  exports org.higherkindedj.hkt.maybe;
  exports org.higherkindedj.hkt.maybe_t;
  exports org.higherkindedj.hkt.optional;
  exports org.higherkindedj.hkt.optional_t;
  exports org.higherkindedj.hkt.reader;
  exports org.higherkindedj.hkt.reader_t;
  exports org.higherkindedj.hkt.state;
  exports org.higherkindedj.hkt.state_t;
  exports org.higherkindedj.hkt.stream;
  exports org.higherkindedj.hkt.trampoline;
  exports org.higherkindedj.hkt.trymonad;
  exports org.higherkindedj.hkt.validated;
  exports org.higherkindedj.hkt.writer;
  exports org.higherkindedj.optics.util;
  exports org.higherkindedj.optics.at;
  exports org.higherkindedj.optics.ixed;
  exports org.higherkindedj.optics.prism;
  exports org.higherkindedj.optics.free;
  exports org.higherkindedj.optics.fluent;
}
