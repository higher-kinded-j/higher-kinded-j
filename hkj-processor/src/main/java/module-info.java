module org.higherkindedj.processor {
  exports org.higherkindedj.alias;

  requires java.compiler;
  requires static org.jspecify;

//  provides javax.annotation.processing.Processor with
//      org.higherkindedj.alias.HKTAliasProcessor;
}
