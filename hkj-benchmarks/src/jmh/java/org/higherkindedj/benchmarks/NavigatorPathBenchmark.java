// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.benchmarks;

import java.util.concurrent.TimeUnit;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.focus.FocusPath;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Reading and writing through a navigator that is a path, against the {@code LensFocusPath} it
 * wraps.
 *
 * <p>The direct pairs call each through its own static type, as user code does. The {@code
 * callSite} pair calls through {@code FocusPath} at one site that sees four receivers, as library
 * code does when it is handed paths: four {@code LensFocusPath}s, or one with three navigator
 * classes.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class NavigatorPathBenchmark {

  @GenerateFocus
  public record Address(String street, String city) {}

  @GenerateFocus
  public record Company(Address hq, Address billing, Address shipping) {}

  private CompanyFocus.HqNavigator<Company> navigator;
  private FocusPath<Company, Address> lens;
  private FocusPath<Company, Address>[] lenses;
  private FocusPath<Company, Address>[] mixed;
  private Company company;
  private Address moved;
  private int next;

  @Setup
  public void setup() {
    navigator = CompanyFocus.hq();
    lens = navigator.toPath();
    lenses = paths(lens, CompanyFocus.billing().toPath(), CompanyFocus.shipping().toPath(), lens);
    mixed = paths(lens, CompanyFocus.hq(), CompanyFocus.billing(), CompanyFocus.shipping());
    Address address = new Address("1 High Street", "York");
    company = new Company(address, address, address);
    moved = new Address("2 Low Street", "Leeds");
  }

  @SafeVarargs
  private static FocusPath<Company, Address>[] paths(FocusPath<Company, Address>... paths) {
    return paths;
  }

  @Benchmark
  public Address getLens() {
    return lens.get(company);
  }

  @Benchmark
  public Address getNavigator() {
    return navigator.get(company);
  }

  @Benchmark
  public Company setLens() {
    return lens.set(moved, company);
  }

  @Benchmark
  public Company setNavigator() {
    return navigator.set(moved, company);
  }

  @Benchmark
  public Company modifyLens() {
    return lens.modify(address -> moved, company);
  }

  @Benchmark
  public Company modifyNavigator() {
    return navigator.modify(address -> moved, company);
  }

  @Benchmark
  public Address callSiteLenses() {
    return lenses[next++ & 3].get(company);
  }

  @Benchmark
  public Address callSiteMixed() {
    return mixed[next++ & 3].get(company);
  }
}
