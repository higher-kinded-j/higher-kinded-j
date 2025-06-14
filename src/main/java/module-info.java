module org.higherkindedj.core {
  // Export the core API packages
  exports org.higherkindedj.hkt;
  exports org.higherkindedj.hkt.function;
  exports org.higherkindedj.hkt.exception;
  exports org.higherkindedj.hkt.tuple;
  exports org.higherkindedj.hkt.expression;

  // Export packages for each HKT simulation
  exports org.higherkindedj.hkt.list;
  exports org.higherkindedj.hkt.optional;
  exports org.higherkindedj.hkt.maybe;
  exports org.higherkindedj.hkt.either;
  exports org.higherkindedj.hkt.future;
  exports org.higherkindedj.hkt.trymonad;
  exports org.higherkindedj.hkt.io;
  exports org.higherkindedj.hkt.lazy;
  exports org.higherkindedj.hkt.reader;
  exports org.higherkindedj.hkt.state;
  exports org.higherkindedj.hkt.validated;
  exports org.higherkindedj.hkt.writer;
  exports org.higherkindedj.hkt.id;
  exports org.higherkindedj.hkt.unit;

  // Export packages for monad transformers
  exports org.higherkindedj.hkt.either_t;
  exports org.higherkindedj.hkt.maybe_t;
  exports org.higherkindedj.hkt.optional_t;
  exports org.higherkindedj.hkt.reader_t;
  exports org.higherkindedj.hkt.state_t;

  requires org.jspecify;
}
