// Fixture for hkj-book/src/optics/focus_external_bridging.md
//
// The page's bridge is real code: `org.higherkindedj.example.optics.bridge` holds the domain, the
// external Address and the CompanyBridge the snippets read, and the gate compiles against the
// module's own main sources. The imports below are on-demand so that the snippet which shows the
// domain records can declare them itself.
//
// Its "Other Libraries, Same Shape" specs name a Lombok and an AutoValue type. Neither processor is
// on the gate's path, so each type is declared here with the methods that processor generates;
// the protobuf spec names the Mapping chapter's real, protoc-generated CustomerMessage.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;
import org.higherkindedj.example.optics.bridge.*;
import org.higherkindedj.example.optics.bridge.domain.*;
import org.higherkindedj.example.book.mapping.proto.CustomerMessage;
import org.higherkindedj.example.optics.bridge.external.*;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.annotations.OpticsSpec;
import org.higherkindedj.optics.annotations.ViaBuilder;
import org.higherkindedj.optics.laws.LensLaws;
import org.junit.jupiter.api.Test;

/** What Lombok generates for {@code @Getter @Builder(toBuilder = true)} on a name field. */
final class LombokPerson {
  private final String name;

  LombokPerson(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public LombokPersonBuilder toBuilder() {
    return new LombokPersonBuilder().name(name);
  }

  static final class LombokPersonBuilder {
    private String name;

    public LombokPersonBuilder name(String name) {
      this.name = name;
      return this;
    }

    public LombokPerson build() {
      return new LombokPerson(name);
    }
  }
}

/**
 * What AutoValue generates for an {@code @AutoValue} class with an {@code @AutoValue.Builder} and
 * an abstract {@code toBuilder()}, collapsed to one concrete class.
 */
final class AutoPerson {
  private final String name;

  AutoPerson(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  public Builder toBuilder() {
    return new Builder().setName(name);
  }

  static final class Builder {
    private String name;

    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    public AutoPerson build() {
      return new AutoPerson(name);
    }
  }
}

class Fixture {

  /**
   * A value the page names but does not build. Snippets are compiled, never run, and a snippet
   * that shows a model shadows the one above, so naming a constructor here would tie the fixture
   * to one shape of it.
   */
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Company acme = sample();
}
