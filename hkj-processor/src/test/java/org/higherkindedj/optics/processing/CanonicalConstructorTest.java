// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.laws.LensLaws;
import org.higherkindedj.optics.laws.MappingLaws;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A record whose primitive component meets a same-arity overload, {@code Money(long cents, String
 * currency)} beside {@code Money(Number major, String currency)}, rebuilt by every generated
 * surface that passes the component boxed: {@code parse} (top level, flattened and chunked), the
 * validated {@code patch} (single and chunked ladders), the fallible merge (single and chunked),
 * {@code assemble()}, and the lens, setter and Focus path each optics annotation generates,
 * including {@code @ImportOptics}' record and {@code @ViaConstructor} routes. The first phase of
 * overload resolution allows no unboxing, so a boxed {@code Long} would bind to the {@code Number}
 * overload and read 1234 cents as 1234 pounds. Every surface has to call the canonical constructor.
 * A record with a second primitive component, {@code Qty(long cents, int count)}, makes the call
 * ambiguous rather than wrong, so its surfaces are pinned too: they have to compile at all.
 *
 * <p>Every fixture compiles in one javac run, under {@code -Xlint:all -Werror}, which also holds
 * that the unboxing cast is written nowhere javac would call it redundant. Each case calls one
 * static method of the compiled {@code Probes} class, which answers the cents the rebuilt value
 * holds.
 */
@DisplayName("Generated code calls a record's canonical constructor, not a same-arity overload")
class CanonicalConstructorTest {

  private static final String PKG = "com.example.canonical";

  private static RuntimeCompilationHelper.CompiledResult compiled;

  /** {@code , int f1, ..., int fN}. */
  private static String ints(int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(i -> ", int f" + i)
        .collect(Collectors.joining());
  }

  /** {@code , f1, ..., fN}. */
  private static String names(int count) {
    return IntStream.rangeClosed(1, count).mapToObj(i -> ", f" + i).collect(Collectors.joining());
  }

  /** {@code , 1, ..., N}. */
  private static String values(int count) {
    return IntStream.rangeClosed(1, count).mapToObj(i -> ", " + i).collect(Collectors.joining());
  }

  private static JavaFileObject source(String qualifiedName, String text) {
    return JavaFileObjects.forSourceString(qualifiedName, text);
  }

  @BeforeAll
  static void compileFixtures() {
    // Fifteen int components past cents and currency: seventeen in all, one past a single
    // fields() ladder.
    String wide = ints(15);
    JavaFileObject records =
        source(
            PKG + ".Records",
            """
            package com.example.canonical;

            import org.higherkindedj.optics.annotations.GenerateAssembly;
            import org.higherkindedj.optics.annotations.GenerateFocus;
            import org.higherkindedj.optics.annotations.GenerateLenses;
            import org.higherkindedj.optics.annotations.GenerateSetters;

            public final class Records {
              private Records() {}

              @GenerateLenses
              @GenerateSetters
              @GenerateFocus
              @GenerateAssembly
              public record Money(long cents, String currency) {
                public Money(Number major, String currency) {
                  this(Math.round(major.doubleValue() * 100), currency);
                }

                // Fewer arguments than the call passes, so it is never one of its candidates.
                public Money(long cents) {
                  this(cents, "GBP");
                }
              }

              public record MoneyDto(long cents, String currency) {}

              // A second primitive component: the Number overload needs unboxing there too, so
              // the call is ambiguous rather than wrong.
              @GenerateLenses
              public record Qty(long cents, int count) {
                public Qty(Number major, int count) {
                  this(Math.round(major.doubleValue() * 100), count);
                }
              }

              public record QtyDto(long cents, int count) {}

              // Nothing competes with the canonical constructor here, so nothing is unboxed.
              @GenerateLenses
              public record Plain(long cents, String currency) {}

              public record Ledger(long cents, String currency, String memo) {
                public Ledger(Number major, String currency, String memo) {
                  this(Math.round(major.doubleValue() * 100), currency, memo);
                }
              }

              public record LedgerDto(long cents, String currency) {}

              public record Wide(long cents, String currency%1$s) {
                public Wide(Number major, String currency%1$s) {
                  this(Math.round(major.doubleValue() * 100), currency%2$s);
                }
              }

              public record WideDto(long cents, String currency%1$s) {}

              public record WideLedger(long cents, String currency%1$s, String memo) {
                public WideLedger(Number major, String currency%1$s, String memo) {
                  this(Math.round(major.doubleValue() * 100), currency%2$s, memo);
                }
              }

              public record WideLedgerDto(long cents, String currency%1$s) {}

              public record Order(String id, Money price) {}

              public record OrderDto(String id, long cents, String currency) {}

              public record CentsSource(long cents) {}

              public record CurrencySource(String currency) {}

              public record WideSource(long cents%1$s) {}
            }
            """
                .formatted(wide, names(15)));
    JavaFileObject specs =
        source(
            PKG + ".Specs",
            """
            package com.example.canonical;

            import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
            import org.higherkindedj.hkt.validated.FieldError;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.annotations.Flatten;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.GenerateMerge;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            public final class Specs {
              private Specs() {}

              /** A leaf that makes a projection's patch fallible; a merge declares its own. */
              public interface CurrencyLeaf {
                default ValidatedPrism<String, String> currency() {
                  return ValidatedPrism.of(Validated::validNel, currency -> currency);
                }
              }

              @GenerateMapping
              public interface MoneyMapping extends MappingSpec<Records.Money, Records.MoneyDto> {}

              @GenerateMapping
              public interface QtyMapping extends MappingSpec<Records.Qty, Records.QtyDto> {}

              @GenerateMapping
              public interface LedgerMapping
                  extends MappingSpec<Records.Ledger, Records.LedgerDto>, CurrencyLeaf {}

              @GenerateMapping
              public interface WideMapping extends MappingSpec<Records.Wide, Records.WideDto> {}

              @GenerateMapping
              public interface WideLedgerMapping
                  extends MappingSpec<Records.WideLedger, Records.WideLedgerDto>, CurrencyLeaf {}

              @GenerateMapping
              public interface OrderMapping extends MappingSpec<Records.Order, Records.OrderDto> {
                @Flatten
                Records.Money price();
              }

              @GenerateMerge
              public interface MoneyMerge {
                Validated<NonEmptyList<FieldError>, Records.Money> merge(
                    Records.CentsSource cents, Records.CurrencySource currency);

                default ValidatedPrism<String, String> currency() {
                  return ValidatedPrism.of(Validated::validNel, currency -> currency);
                }
              }

              @GenerateMerge
              public interface WideMerge {
                Validated<NonEmptyList<FieldError>, Records.Wide> merge(
                    Records.WideSource wide, Records.CurrencySource currency);

                default ValidatedPrism<String, String> currency() {
                  return ValidatedPrism.of(Validated::validNel, currency -> currency);
                }
              }
            }
            """);
    JavaFileObject externalMoney =
        source(
            PKG + ".ext.Money",
            """
            package com.example.canonical.ext;

            public record Money(long cents, String currency) {
              public Money(Number major, String currency) {
                this(Math.round(major.doubleValue() * 100), currency);
              }
            }
            """);
    JavaFileObject base =
        source(
            PKG + ".ext.Base",
            """
            package com.example.canonical.ext;

            public abstract class Base {
              private final long balance;

              protected Base(long balance) {
                this.balance = balance;
              }

              public long balance() {
                return balance;
              }
            }
            """);
    // The getter the lens reads through is inherited, and returns a primitive.
    JavaFileObject account =
        source(
            PKG + ".ext.Account",
            """
            package com.example.canonical.ext;

            public final class Account extends Base {
              private final String owner;

              public Account(long balance, String owner) {
                super(balance);
                this.owner = owner;
              }

              public Account(Number major, String owner) {
                this(Math.round(major.doubleValue() * 100), owner);
              }

              public Account(long balance) {
                this(balance, "");
              }

              public String owner() {
                return owner;
              }

              public Account balance(long balance) {
                return new Account(balance, owner);
              }
            }
            """);
    // The getter returns the boxed type, so the constructor meant takes it boxed.
    JavaFileObject boxed =
        source(
            PKG + ".ext.Boxed",
            """
            package com.example.canonical.ext;

            public final class Boxed {
              private final Long balance;
              private final String owner;

              public Boxed(Long balance, String owner) {
                this.balance = balance;
                this.owner = owner;
              }

              public Boxed(Number major, String owner) {
                this(Math.round(major.doubleValue() * 100), owner);
              }

              public Long balance() {
                return balance;
              }

              public String owner() {
                return owner;
              }
            }
            """);
    // A primitive getter over a boxed parameter: unboxing would hand the call to the widening
    // double constructor, which the boxed focus never reached, so the focus stays as it is.
    JavaFileObject widening =
        source(
            PKG + ".ext.Widening",
            """
            package com.example.canonical.ext;

            public final class Widening {
              private final Long cents;
              private final String owner;

              public Widening(Long cents, String owner) {
                this.cents = cents;
                this.owner = owner;
              }

              public Widening(double cents, String owner) {
                this(Long.valueOf(Math.round(cents * 100)), owner);
              }

              public long cents() {
                return cents;
              }

              public String owner() {
                return owner;
              }
            }
            """);
    // A primitive getter where every constructor takes a reference type in its place: the boxed
    // focus already picks the more specific of them.
    JavaFileObject boxedParameter =
        source(
            PKG + ".ext.BoxedParameter",
            """
            package com.example.canonical.ext;

            public final class BoxedParameter {
              private final Long cents;
              private final String owner;

              public BoxedParameter(Long cents, String owner) {
                this.cents = cents;
                this.owner = owner;
              }

              public BoxedParameter(Number major, String owner) {
                this(Long.valueOf(Math.round(major.doubleValue() * 100)), owner);
              }

              public long cents() {
                return cents;
              }

              public String owner() {
                return owner;
              }
            }
            """);
    // Candidates the unboxed focus cannot reach: an int one, which no argument narrows to, and
    // one no other argument fits. Neither can take the call, so neither holds the unboxing back.
    JavaFileObject narrow =
        source(
            PKG + ".ext.Narrow",
            """
            package com.example.canonical.ext;

            public final class Narrow {
              private final long cents;
              private final String owner;

              public Narrow(long cents, String owner) {
                this.cents = cents;
                this.owner = owner;
              }

              public Narrow(Number major, String owner) {
                this(Math.round(major.doubleValue() * 100), owner);
              }

              public Narrow(int cents, String owner) {
                this((long) cents, owner);
              }

              public Narrow(double cents, Integer scale) {
                this(Math.round(cents), String.valueOf(scale));
              }

              public long cents() {
                return cents;
              }

              public String owner() {
                return owner;
              }
            }
            """);
    // A parameterOrder that names no getter for the focus: the call passes it nowhere.
    JavaFileObject unpassed =
        source(
            PKG + ".ext.Unpassed",
            """
            package com.example.canonical.ext;

            public final class Unpassed {
              private final String owner;
              private final long tag;

              public Unpassed(String owner) {
                this(owner, 0L);
              }

              public Unpassed(long tag) {
                this("", tag);
              }

              public Unpassed(String owner, long tag) {
                this.owner = owner;
                this.tag = tag;
              }

              public String owner() {
                return owner;
              }

              public long tag() {
                return tag;
              }
            }
            """);
    // The three strategies that write through a method call rather than a constructor: an
    // overload taking a wrapper's supertype would take the boxed focus first.
    JavaFileObject coin =
        source(
            PKG + ".ext.Coin",
            """
            package com.example.canonical.ext;

            public final class Coin {
              private final long cents;

              public Coin(long cents) {
                this.cents = cents;
              }

              public long cents() {
                return cents;
              }

              public Coin withCents(long cents) {
                return new Coin(cents);
              }

              public Coin withCents(Number major) {
                return new Coin(Math.round(major.doubleValue() * 100));
              }
            }
            """);
    JavaFileObject legacy =
        source(
            PKG + ".ext.Legacy",
            """
            package com.example.canonical.ext;

            public final class Legacy {
              private long cents;

              public Legacy() {}

              public Legacy(Legacy other) {
                this.cents = other.cents;
              }

              public long cents() {
                return cents;
              }

              public void setCents(long cents) {
                this.cents = cents;
              }

              public void setCents(Number major) {
                this.cents = Math.round(major.doubleValue() * 100);
              }
            }
            """);
    JavaFileObject built =
        source(
            PKG + ".ext.Built",
            """
            package com.example.canonical.ext;

            public final class Built {
              private final long cents;

              private Built(long cents) {
                this.cents = cents;
              }

              public long cents() {
                return cents;
              }

              public static Built of(long cents) {
                return new Built(cents);
              }

              public Builder toBuilder() {
                return new Builder(cents);
              }

              public static final class Builder {
                private long cents;

                Builder(long cents) {
                  this.cents = cents;
                }

                // A readable builder: not a candidate for a call passing one argument.
                public long cents() {
                  return cents;
                }

                public Builder cents(long cents) {
                  this.cents = cents;
                  return this;
                }

                public Builder cents(Number major) {
                  this.cents = Math.round(major.doubleValue() * 100);
                  return this;
                }

                public Built build() {
                  return new Built(cents);
                }
              }
            }
            """);
    JavaFileObject imports =
        source(
            PKG + ".app.package-info",
            """
            @ImportOptics({com.example.canonical.ext.Money.class})
            package com.example.canonical.app;

            import org.higherkindedj.optics.annotations.ImportOptics;
            """);
    JavaFileObject accountSpec =
        source(
            PKG + ".app.AccountSpec",
            """
            package com.example.canonical.app;

            import com.example.canonical.ext.Account;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.OpticsSpec;
            import org.higherkindedj.optics.annotations.ViaConstructor;

            @ImportOptics
            public interface AccountSpec extends OpticsSpec<Account> {
              @ViaConstructor(parameterOrder = {"balance", "owner"})
              Lens<Account, Long> balance();
            }
            """);
    JavaFileObject boxedSpec =
        source(
            PKG + ".app.BoxedSpec",
            """
            package com.example.canonical.app;

            import com.example.canonical.ext.Boxed;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.OpticsSpec;
            import org.higherkindedj.optics.annotations.ViaConstructor;

            @ImportOptics
            public interface BoxedSpec extends OpticsSpec<Boxed> {
              @ViaConstructor(parameterOrder = {"balance", "owner"})
              Lens<Boxed, Long> balance();
            }
            """);
    JavaFileObject otherSpecs =
        source(
            PKG + ".app.OtherSpecs",
            """
            package com.example.canonical.app;

            import com.example.canonical.ext.BoxedParameter;
            import com.example.canonical.ext.Built;
            import com.example.canonical.ext.Coin;
            import com.example.canonical.ext.Legacy;
            import com.example.canonical.ext.Narrow;
            import com.example.canonical.ext.Unpassed;
            import com.example.canonical.ext.Widening;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.OpticsSpec;
            import org.higherkindedj.optics.annotations.ViaBuilder;
            import org.higherkindedj.optics.annotations.ViaConstructor;
            import org.higherkindedj.optics.annotations.ViaCopyAndSet;
            import org.higherkindedj.optics.annotations.Wither;

            public final class OtherSpecs {
              private OtherSpecs() {}

              @ImportOptics
              public interface WideningSpec extends OpticsSpec<Widening> {
                @ViaConstructor(parameterOrder = {"cents", "owner"})
                Lens<Widening, Long> cents();
              }

              @ImportOptics
              public interface BoxedParameterSpec extends OpticsSpec<BoxedParameter> {
                @ViaConstructor(parameterOrder = {"cents", "owner"})
                Lens<BoxedParameter, Long> cents();
              }

              @ImportOptics
              public interface NarrowSpec extends OpticsSpec<Narrow> {
                @ViaConstructor(parameterOrder = {"cents", "owner"})
                Lens<Narrow, Long> cents();
              }

              @ImportOptics
              public interface CoinSpec extends OpticsSpec<Coin> {
                @Wither("withCents")
                Lens<Coin, Long> cents();
              }

              @ImportOptics
              public interface LegacySpec extends OpticsSpec<Legacy> {
                @ViaCopyAndSet(setter = "setCents")
                Lens<Legacy, Long> cents();
              }

              @ImportOptics
              public interface BuiltSpec extends OpticsSpec<Built> {
                @ViaBuilder(setter = "cents")
                Lens<Built, Long> cents();
              }
            }
            """);
    JavaFileObject probes =
        source(
            PKG + ".Probes",
            """
            package com.example.canonical;

            import com.example.canonical.ext.Account;
            import com.example.canonical.ext.Boxed;
            import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
            import org.higherkindedj.hkt.validated.FieldError;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.Iso;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            public final class Probes {
              private Probes() {}

              static final Records.Money ONE = new Records.Money(1L, "GBP");

              static final Records.MoneyDto DTO = new Records.MoneyDto(1234L, "GBP");

              private static <T> T valid(Validated<NonEmptyList<FieldError>, T> assembled) {
                return assembled.get();
              }

              public static long parse() {
                return valid(SpecsMoneyMappingImpl.INSTANCE.parse(DTO)).cents();
              }

              public static long reverseGet() {
                return SpecsMoneyMappingImpl.INSTANCE.asIso().reverseGet(DTO).cents();
              }

              public static long secondPrimitiveParse() {
                return valid(SpecsQtyMappingImpl.INSTANCE.parse(new Records.QtyDto(1234L, 2)))
                    .cents();
              }

              public static long secondPrimitiveLens() {
                Records.Qty qty = QtyLenses.cents().set(1234L, new Records.Qty(1L, 2));
                return QtyLenses.count().set(3, qty).cents();
              }

              public static long patch() {
                return valid(
                        SpecsLedgerMappingImpl.INSTANCE.patch(
                            new Records.Ledger(1L, "GBP", "memo"),
                            new Records.LedgerDto(1234L, "GBP")))
                    .cents();
              }

              public static long chunkedParse() {
                return valid(
                        SpecsWideMappingImpl.INSTANCE.parse(
                            new Records.WideDto(1234L, "GBP"%1$s)))
                    .cents();
              }

              public static long chunkedPatch() {
                return valid(
                        SpecsWideLedgerMappingImpl.INSTANCE.patch(
                            new Records.WideLedger(1L, "GBP"%1$s, "memo"),
                            new Records.WideLedgerDto(1234L, "GBP"%1$s)))
                    .cents();
              }

              public static long flattened() {
                return valid(
                        SpecsOrderMappingImpl.INSTANCE.parse(
                            new Records.OrderDto("order", 1234L, "GBP")))
                    .price()
                    .cents();
              }

              public static long merge() {
                return valid(
                        SpecsMoneyMergeImpl.INSTANCE.merge(
                            new Records.CentsSource(1234L), new Records.CurrencySource("GBP")))
                    .cents();
              }

              public static long chunkedMerge() {
                return valid(
                        SpecsWideMergeImpl.INSTANCE.merge(
                            new Records.WideSource(1234L%1$s), new Records.CurrencySource("GBP")))
                    .cents();
              }

              public static long assembled() {
                return valid(
                        RecordsMoneyAssembly.fields()
                            .cents(Validated.validNel(1234L))
                            .currency(Validated.validNel("GBP"))
                            .assemble())
                    .cents();
              }

              public static long lensSet() {
                return MoneyLenses.cents().set(1234L, ONE).cents();
              }

              public static long with() {
                return MoneyLenses.withCents(ONE, 1234L).cents();
              }

              public static long setter() {
                return MoneySetters.cents().set(1234L, ONE).cents();
              }

              public static long focus() {
                return MoneyFocus.cents().set(1234L, ONE).cents();
              }

              public static long imported() {
                return com.example.canonical.app.MoneyLenses.cents()
                    .set(1234L, new com.example.canonical.ext.Money(1L, "GBP"))
                    .cents();
              }

              public static long viaConstructor() {
                return com.example.canonical.app.Account.balance()
                    .set(1234L, new Account(1L, "owner"))
                    .balance();
              }

              public static long viaConstructorBoxed() {
                return com.example.canonical.app.Boxed.balance()
                    .set(1234L, new Boxed(1L, "owner"))
                    .balance();
              }

              public static long viaConstructorWidening() {
                return com.example.canonical.app.Widening.cents()
                    .set(1234L, new com.example.canonical.ext.Widening(1L, "owner"))
                    .cents();
              }

              public static long viaConstructorUnreachableCandidates() {
                return com.example.canonical.app.Narrow.cents()
                    .set(1234L, new com.example.canonical.ext.Narrow(1L, "owner"))
                    .cents();
              }

              public static long viaConstructorBoxedParameter() {
                return com.example.canonical.app.BoxedParameter.cents()
                    .set(1234L, new com.example.canonical.ext.BoxedParameter(1L, "owner"))
                    .cents();
              }

              public static long wither() {
                return com.example.canonical.app.Coin.cents()
                    .set(1234L, new com.example.canonical.ext.Coin(1L))
                    .cents();
              }

              public static long copyAndSet() {
                return com.example.canonical.app.Legacy.cents()
                    .set(1234L, new com.example.canonical.ext.Legacy())
                    .cents();
              }

              public static long builder() {
                return com.example.canonical.app.Built.cents()
                    .set(1234L, com.example.canonical.ext.Built.of(1L))
                    .cents();
              }

              public static Iso<Records.Money, Records.MoneyDto> iso() {
                return SpecsMoneyMappingImpl.INSTANCE.asIso();
              }

              public static ValidatedPrism<Records.MoneyDto, Records.Money> prism() {
                return SpecsMoneyMappingImpl.INSTANCE.asValidatedPrism();
              }

              public static Lens<Records.Money, Long> lens() {
                return MoneyLenses.cents();
              }

              public static Records.Money one() {
                return ONE;
              }

              public static Records.MoneyDto dto() {
                return DTO;
              }
            }
            """
                .formatted(values(15)));
    Compilation compilation =
        javac()
            .withProcessors(
                new MappingProcessor(),
                new MergeProcessor(),
                new AssemblyProcessor(),
                new LensProcessor(),
                new SetterProcessor(),
                new FocusProcessor(),
                new ImportOpticsProcessor(),
                new CompanionAnnotationProcessor())
            .withOptions("-Xlint:all", "-Werror")
            .compile(
                records,
                specs,
                externalMoney,
                base,
                account,
                boxed,
                widening,
                boxedParameter,
                narrow,
                unpassed,
                coin,
                legacy,
                built,
                imports,
                accountSpec,
                boxedSpec,
                otherSpecs,
                probes);
    assertThat(compilation).succeeded();
    compiled = new RuntimeCompilationHelper.CompiledResult(compilation);
  }

  private static Object probe(String name) throws ReflectiveOperationException {
    return compiled.invokeStatic(PKG + ".Probes", name);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "parse",
        "reverseGet",
        "secondPrimitiveParse",
        "secondPrimitiveLens",
        "patch",
        "chunkedParse",
        "chunkedPatch",
        "flattened",
        "merge",
        "chunkedMerge",
        "assembled",
        "lensSet",
        "with",
        "setter",
        "focus",
        "imported"
      })
  @DisplayName("the canonical constructor takes the 1234 cents, and the overload never reads them")
  void everySurfaceCallsTheCanonicalConstructor(String surface)
      throws ReflectiveOperationException {
    assertThat(probe(surface)).isEqualTo(1234L);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "viaConstructor",
        "viaConstructorBoxed",
        "viaConstructorWidening",
        "viaConstructorBoxedParameter",
        "viaConstructorUnreachableCandidates"
      })
  @DisplayName(
      "a @ViaConstructor call binds where the getters' own values do, unboxing the focus only"
          + " where that cannot move it to another constructor")
  void viaConstructorBindsWhereTheGettersDo(String source) throws ReflectiveOperationException {
    assertThat(probe(source)).isEqualTo(1234L);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"wither", "copyAndSet", "builder"})
  @DisplayName(
      "a strategy that writes through a method call unboxes the focus too, so an overload taking"
          + " a wrapper's supertype does not take it first")
  void methodStrategiesUnboxTheFocus(String strategy) throws ReflectiveOperationException {
    assertThat(probe(strategy)).isEqualTo(1234L);
  }

  @Test
  @DisplayName(
      "a @ViaConstructor parameterOrder that names no argument for the focus is refused, since"
          + " setting through it would change nothing")
  void focusPassedNowhereIsRefused() {
    // The call such an order describes, new Unpassed(source.owner()), passes the focus nowhere,
    // so set would hand back whatever that constructor gives the component and the lens would
    // not obey its own laws. It is refused at the spec method rather than generated.
    var spec =
        source(
            PKG + ".app.UnpassedSpec",
            """
            package com.example.canonical.app;

            import com.example.canonical.ext.Unpassed;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.OpticsSpec;
            import org.higherkindedj.optics.annotations.ViaConstructor;

            @ImportOptics
            public interface UnpassedSpec extends OpticsSpec<Unpassed> {
              @ViaConstructor(parameterOrder = {"owner"})
              Lens<Unpassed, Long> tag();
            }
            """);

    var compilation =
        javac()
            .withProcessors(new ImportOpticsProcessor())
            .compile(
                source(
                    PKG + ".ext.Unpassed",
                    """
                    package com.example.canonical.ext;

                    public final class Unpassed {
                      private final String owner;
                      private final long tag;

                      public Unpassed(String owner, long tag) {
                        this.owner = owner;
                        this.tag = tag;
                      }

                      public String owner() {
                        return owner;
                      }

                      public long tag() {
                        return tag;
                      }
                    }
                    """),
                spec);

    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@ViaConstructor: 'parameterOrder' names no argument for the lens's own 'tag'.");
  }

  @Test
  @DisplayName("a record that overloads no constructor is built exactly as it was, with no cast")
  void nothingIsUnboxedWithoutAnOverload() {
    assertThat(compiled.compilation())
        .generatedSourceFile(PKG + ".PlainLenses")
        .contentsAsUtf8String()
        .contains("new Records.Plain(newValue, source.currency())");
    assertThat(compiled.compilation())
        .generatedSourceFile(PKG + ".PlainLenses")
        .contentsAsUtf8String()
        .doesNotContain("(long) newValue");
  }

  @Test
  @DisplayName("parse agrees with asIso().reverseGet, so the lossless tier's laws hold")
  @SuppressWarnings("unchecked") // the probes hand back the generated optics as Object
  void losslessLawsHold() throws ReflectiveOperationException {
    MappingLaws.assertMappingLaws(
        (Iso<Object, Object>) probe("iso"),
        (ValidatedPrism<Object, Object>) probe("prism"),
        probe("one"),
        probe("dto"));
  }

  @Test
  @DisplayName("the generated lens obeys the lens laws on a primitive focus")
  @SuppressWarnings("unchecked") // the probe hands back the generated lens as Object
  void lensLawsHold() throws ReflectiveOperationException {
    LensLaws.assertLensLaws((Lens<Object, Object>) probe("lens"), probe("one"), 1234L, 5678L);
  }
}
