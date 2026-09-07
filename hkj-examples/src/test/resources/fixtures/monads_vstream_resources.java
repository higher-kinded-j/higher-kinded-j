// Fixture for hkj-book/src/monads/vstream_resources.java
//
// The page streams a file, then a database cursor, and shows what runs when each finishes. The
// resources the snippets say they assume ("Assuming: Connection openConnection() ...") are
// declared here.
//
// `Path` means two different types on this page - java.nio.file.Path for the file, the effect
// `Path` for the VStreamPath factory - so neither is imported here; each snippet names its own.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStream.Seed;
import org.higherkindedj.hkt.vtask.VTask;

final class Connection {

  void close() {}
}

final class Cursor {

  void close() {}
}

class Fixture {

  static final VStream<String> lines = VStream.of("alpha", "beta", "gamma");

  static final VStream<Integer> stream = VStream.of(1, 2, 3);

  static Connection openConnection() {
    return new Connection();
  }

  static Cursor openCursor(Connection connection) {
    return new Cursor();
  }

  static VStream<String> streamFromCursor(Cursor cursor) {
    return VStream.of("row-1", "row-2");
  }

  static BufferedReader openReader() {
    return new BufferedReader(new StringReader("alpha\nbeta"));
  }

  static VStream<String> streamLines(BufferedReader reader) {
    return VStream.of("alpha", "beta");
  }
}
