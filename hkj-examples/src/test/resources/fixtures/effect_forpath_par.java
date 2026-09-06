// Fixture for hkj-book/src/effect/forpath_par.md
//
// The VTaskPath section fetches a user and the application config independently, which is the
// whole point of `par()`.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.id.Id;

class Fixture {

  static final String userId = "u-1";

  static String fetchUserData(String userId) {
    return "user " + userId;
  }

  static String fetchConfigData() {
    return "config";
  }

  static String buildResponse(String user, String config) {
    return user + " / " + config;
  }
}
