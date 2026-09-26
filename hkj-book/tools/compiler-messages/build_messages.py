"""Probe every catalogue reproducer, capture the message the processor prints, and render
hkj-book/src/mapping/compiler_errors.md plus its snippet fixture.

Each message shown on the page is the processor's own output for the reproducer beside it,
compiled in package com.example; the verify marker then holds the page to it in the build.

Run from the repository root, after the Gradle task writes the classpath, processor path and javac
it compiles with:

    ./gradlew :hkj-examples:bookMessagesClasspath
    python3 hkj-book/tools/compiler-messages/build_messages.py

The entries are data in messages.py; README.md beside this file says how to add one."""

import pathlib
import re
import subprocess
import sys
import tempfile

HERE = pathlib.Path(__file__).parent
sys.path.insert(0, str(HERE))
from messages import FIXTURE_IMPORTS, FIXTURE_TYPES, GROUPS  # noqa: E402

REPO = HERE.parents[2]
CLASSPATHS = REPO / "hkj-examples/build/book-messages"


def classpath(name):
    path = CLASSPATHS / name
    if not path.exists():
        sys.exit(f"{path} is missing: run `./gradlew :hkj-examples:bookMessagesClasspath` first.")
    return path.read_text(encoding="utf-8").strip()


CP = classpath("classpath.txt")
PP = classpath("processorpath.txt")
JAVAC = classpath("javac.txt")


def release():
    """The feature release of the toolchain's javac: --enable-preview accepts only that release."""
    try:
        out = subprocess.run([JAVAC, "-version"], capture_output=True, encoding="utf-8")
    except OSError as e:
        sys.exit(f"cannot run {JAVAC} ({e}): rerun `./gradlew :hkj-examples:bookMessagesClasspath`.")
    found = re.search(r"javac (\d+)", out.stdout + out.stderr)
    if not found:
        sys.exit(f"cannot read the release from `{JAVAC} -version`: {out.stdout}{out.stderr}")
    return found.group(1)


RELEASE = release()

SHORT = {
    "no-wire-counterpart": "A domain component has no same-named wire component",
    "no-usable-source": "Types differ and nothing converts them",
    "leaf-names-no-component": "A leaf's name matches no component, usually a typo",
    "rename-already-claimed": "Two renames point at one wire component",
    "neither-rename-leaf-bridge": "An abstract method says nothing about what it is",
    "getter-named-after-domain": "A derived field carries a domain component's name",
    "projection-with-derived-fields": "A smaller wire also declares a derived field",
    "own-type-parameters": "A leaf, rename or marker declares its own `<R>`",
    "cannot-be-reached": "A member names a type its package cannot see",
    "component-cannot-be-reached": "A mapped type is hidden from the spec's package",
    "envelope-cannot-be-reached": "An envelope's type is hidden from its companion's package",
    "redeclares-the-mapping": "The spec declares a mapping method, MapStruct-style",
    "collides-with-a-generated-member": "A spec method clashes with a generated one",
    "wire-has-more-components": "The wire has components nothing fills",
    "projection-field-has-no-domain-source": "A smaller wire names a missing component",
    "rename-names-no-component": "A rename's `to` names nothing on the wire",
    "derived-field-names-no-component": "A derived field names nothing on the wire",
    "both-map-to-one-wire-component": "A rename targets a component already filled",
    "unmapped-names-a-mapped-property": "An `@Unmapped` marker names a paired property",
    "add-optional-bridge": "A domain `Optional` faces a plain wire component",
    "bridges-to-a-primitive": "The bridged wire component is a primitive",
    "declared-non-null": "The bridged wire component is declared non-null",
    "bridged-leaf-over-whole-optional": "A bridged leaf is declared over the `Optional`",
    "redundant-on-a-bean-wire": "**Note.** A bean wire bridges without the marker",
    "mix-in-is-a-spec": "A spec extends another spec",
    "extended-raw": "A generic mix-in is extended without type arguments",
    "reached-through-raw": "A raw clause further up erases a generic mix-in",
    "conflicting-renames": "Two mix-ins rename one component two ways",
    "array-constructor": "A lifted array's element type is generic",
    "key-leaf-never-runs": "A key leaf sits beside a whole-map leaf",
    "raw-map": "A key leaf faces a raw `Map`",
    "more-than-one-spec": "Two specs map the same pair",
    "subtype-has-no-spec": "A sealed subtype has no spec",
    "subtype-never-produced": "A sealed wire subtype has no spec producing it",
    "subtype-targeted-twice": "Two domain subtypes map to one wire subtype",
    "no-meaning-on-a-sealed-mapping": "A sealed spec declares a leaf or marker",
    "spreads-a-shared-name": "A flattened name collides with a domain name",
    "spreads-across-a-bean": "`@Flatten` on a bean wire",
    "flattens-a-group-member": "`@Flatten` inside a flattened group",
    "bean-domain": "The domain is a bean, not a record",
    "setter-with-no-getter": "An accessor has no partner",
    "unmapped-names-no-accessor": "An `@Unmapped` marker names nothing left out",
    "getter-only-list-build": "A getter-only `List` is raw or a wildcard",
    "bridged-to-a-getter-only-list": "A domain `Optional` faces a getter-only `List`",
    "reads-some-writes-others": "A bean reads some names and writes others",
    "extends-both-tiers": "One spec extends `MappingSpec` and `UpdateSpec`",
    "primitive-patch-property": "A PATCH property is a primitive",
    "record-patch-wire": "A PATCH wire is a record",
    "getter-only-list-patch": "A PATCH bean has a getter-only `List`",
    "optional-on-a-patch": "A plain PATCH property faces a domain `Optional`",
    "generic-bean-or-patch": "A generic spec maps a bean or a PATCH",
    "abstract-leaf-needs-a-generic-spec": "A concrete spec declares a leaf with no body",
    "merge-component-ambiguous": "Two merge sources carry one component",
    "merge-plain-return": "A fallible merge declares a plain return",
    "merge-validated-identity": "A merge of copies declares a Validated return",
    "merge-unfilled": "No merge source names a target component",
    "envelope-primitive-context": "An envelope context component is a primitive",
    "envelope-variant-not-a-record": "An envelope variant is a class",
    "envelope-generic": "An envelope hierarchy is generic",
    "no-impl-class": "The generated Impl does not exist",
    "no-such-surface": "The tier does not offer that method",
}

TYPE_START = re.compile(r"^(@|record |class |interface |sealed |final |abstract |enum )")


def split(code):
    """Top-level types, and trailing statements the harness would wrap in a method."""
    types, statements = [], []
    depth = 0
    for line in code.split("\n"):
        if depth == 0 and line.strip() and not TYPE_START.match(line) and not line.startswith(" ") \
                and not line.startswith("}"):
            statements.append(line)
            continue
        types.append(line)
        depth += line.count("{") - line.count("}")
    return "\n".join(types), "\n".join(statements)


def probe(entry):
    types, statements = split(entry["code"])
    source = "package com.example;\n\n" + FIXTURE_IMPORTS + "\n" + types + "\n\n" + FIXTURE_TYPES
    if statements:
        source += "\nfinal class Probe {\n  void snippet() throws Throwable {\n" + statements + "\n  }\n}\n"
    with tempfile.TemporaryDirectory() as tmp:
        tmp = pathlib.Path(tmp)
        (tmp / "com/example").mkdir(parents=True)
        src = tmp / "com/example/Probe.java"
        src.write_text(source, encoding="utf-8")
        out = tmp / "out"
        out.mkdir()
        result = subprocess.run(
            # English diagnostics whatever the locale, since the page quotes them and the gate
            # reads them in English too.
            [JAVAC, "-J-Duser.language=en", "-J-Duser.country=US", "-J-Dstderr.encoding=UTF-8",
             "--release", RELEASE, "--enable-preview", "-parameters",
             "-Xlint:unchecked,rawtypes", "-Xlint:-preview", "-classpath", CP,
             "-processorpath", PP, "-d", str(out), "-s", str(out), str(src)],
            capture_output=True, encoding="utf-8")
    return result.returncode, result.stderr


def message_of(entry, stderr):
    """The first diagnostic carrying the entry's marker, as (kind, text), or (None, None)."""
    lines = stderr.split("\n")
    for i, line in enumerate(lines):
        m = re.search(r"(error|warning|Note): (.*)$", line)
        if not m:
            continue
        kind, text = m.group(1), m.group(2)
        # A message from a generated file (an Impl, or a companion such as an envelope's Errors)
        # names that file, since the reader never wrote it; one from the reader's own declaration
        # does not.
        where = re.match(r"^.*?[\\/](\w+\.java):\d+:", line)
        if where and where.group(1) != "Probe.java":
            text = where.group(1) + ": " + text
        if text.startswith("cannot find symbol"):
            extra = [l.strip() for l in lines[i + 1:i + 6] if l.strip().startswith(("symbol:", "location:"))]
            text = "\n".join([text] + extra)
        if entry.get("marker", entry["fragment"]) in text:
            return kind, text
    return None, None


def wrap(text, width=96):
    out = []
    for para in text.split("\n"):
        words, line = para.split(" "), ""
        for w in words:
            if line and len(line) + 1 + len(w) > width:
                out.append(line)
                line = w
            else:
                line = f"{line} {w}" if line else w
        out.append(line)
    return "\n".join(out)


def entry_md(e, message):
    kind = "verify:reports" if e.get("kind") == "note" else "verify:rejects"
    note = " (a note)" if e.get("kind") == "note" else ""
    text, target = e["rule"]
    return f"""### `{e['heading']}`{note} {{#{e['id']}}}

{e['meaning']}

**Fix.** {e['fix']}

```
{wrap(message)}
```

The rule: [{text}]({target}).

~~~admonish example title="A declaration that produces it" collapsible=true
<!-- {kind} "{e.get('marker', e['fragment'])}" -->
```java
{e['code']}
```
~~~
"""


def main():
    failures = []
    sections, triage = [], []
    for title, gid, entries in GROUPS:
        rows = [f"**[{title}](#{gid})**", "", "| The message says | What it means |", "|---|---|"]
        body = [f"## {title} {{#{gid}}}", ""]
        for e in entries:
            marker = e.get("marker", e["fragment"])
            if e["fragment"] not in e["heading"]:
                sys.exit(f"{e['id']}: the fragment is not in the heading")
            if '"' in marker or len(marker.strip()) < 10:
                sys.exit(f"{e['id']}: the marker must be at least 10 characters, with no double quote")
            code, stderr = probe(e)
            kind, msg = message_of(e, stderr)
            # The gate's own test: a refusal is an error the compile fails on; a note leaves the
            # compile clean.
            if e.get("kind") == "note":
                held = msg is not None and code == 0
            else:
                held = msg is not None and kind == "error" and code != 0
            if not held:
                expected = ("a clean compile with a note quoting" if e.get("kind") == "note"
                            else "a failed compile with an error quoting")
                failures.append((e["id"], code, f"expected {expected} '{marker}'\n" + stderr[-1500:]))
                continue
            rows.append(f"| [`{e.get('display', e['fragment'])}`](#{e['id']}) | {SHORT[e['id']]} |")
            body.append(entry_md(e, msg))
        triage.append("\n".join(rows))
        sections.append("\n".join(body))
    if failures:
        for f in failures:
            print("FAILED", f[0], "exit", f[1]); print(f[2]); print("-" * 60)
        sys.exit(1)

    count = sum(len(g[2]) for g in GROUPS)
    seen = ["**Seen most often**", "", "| The message says | What it means |", "|---|---|"]
    by_id = {e["id"]: e for _, _, es in GROUPS for e in es}
    for eid in MOST_OFTEN:
        e = by_id[eid]
        seen.append(f"| [`{e.get('display', e['fragment'])}`](#{eid}) | {SHORT[eid]} |")
    page = PAGE.format(triage="\n\n".join(["\n".join(seen)] + triage),
                       sections="\n---\n\n".join(sections))
    (REPO / "hkj-book/src/mapping/compiler_errors.md").write_text(page, encoding="utf-8")
    fixture = FIXTURE_HEADER + FIXTURE_IMPORTS + "\n" + FIXTURE_TYPES
    (REPO / "hkj-examples/src/test/resources/fixtures/mapping_compiler_errors.java").write_text(
        fixture, encoding="utf-8")
    print(count, "entries rendered")


MOST_OFTEN = ["no-impl-class", "no-wire-counterpart", "no-usable-source", "add-optional-bridge",
              "leaf-names-no-component", "redeclares-the-mapping"]

FIXTURE_HEADER = """// Fixture for hkj-book/src/mapping/compiler_errors.md
//
// Generated by hkj-book/tools/compiler-messages/build_messages.py: edit the entries in messages.py
// there and rerun it, rather than editing this file.
//
// Every entry on that page carries a minimal declaration that provokes the message its heading
// quotes, under `<!-- verify:rejects -->` or `<!-- verify:reports -->`. The reproducers elide
// their imports, and the few that convert an email or a map of labels share the leaves below.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

"""

PAGE = """# Mapping Compiler Messages

_The refusals you are most likely to meet from the mapping processor, what each means, and the fix._

<!-- Generated by hkj-book/tools/compiler-messages/build_messages.py. Edit the entries in
     messages.py, or the page text, SHORT and MOST_OFTEN in build_messages.py, and rerun the script
     rather than editing this page; see README.md there. -->

When the processor cannot write correct code for a spec, it refuses at compile time, pointing at your declaration, with a message that says what is wrong, why, and what to write. Look the message up in [Find your message](#find-your-message): each entry gives the fix and the full message in the open, with a declaration that produces it folded away. Every declaration here is compiled on each build, and the build fails if its message stops carrying the words the entry quotes. A message not listed still carries its own what, why and fix, and its rule is on [Rules and Limits](rules.md).

~~~admonish info title="Reading an entry"
- **Headings** quote the message with its names replaced: `X` and `Y` for types, `x` and `y` for components or methods, `T` for a type argument, `p` for a package. The `@GenerateMapping:` prefix is left off.
- **The words:** the *domain* is your record, the *wire* the DTO, the *spec* the `@GenerateMapping` interface, and a *leaf* a `default ValidatedPrism` method that converts one field.
- **Errors and notes:** an error stops the build; a note stops nothing. One entry is a note, and says so. The processor prints a few other notes, each saying how it read a declaration, such as a bean it maps one way only.
- **The full messages** are printed for declarations compiled in a package `com.example`.
~~~

---

## Find your message {{#find-your-message}}

{triage}

---

## Where the message came from {{#where-the-message-came-from}}

```mermaid
flowchart TD
    accTitle: Which stage produced the message
    accDescr: The processor refuses most specs at the declaration. A spec it accepts is written as an Impl, which javac then compiles, and your own code calls it. A refused spec writes no Impl, so a call to it also fails.
    S["Your spec"] --> P{{"Can the processor<br/>write correct code for it?"}}
    P -->|no| R["Refused at the spec:<br/>most of this page"]
    P -->|yes| G["Impl generated"]
    G --> J{{"Does javac accept<br/>the generated Impl?"}}
    J -->|no| X["An error inside<br/>a generated Impl"]
    J -->|yes| C{{"Does your call site<br/>use a method the Impl has?"}}
    C -->|no| CS["cannot find symbol,<br/>at your call site"]
    C -->|yes| OK(["Builds"])
    R -.->|"no Impl written"| CS

    classDef step fill:#8caaee,stroke:#1e66f5,color:#232634
    classDef decision fill:#e5c890,stroke:#df8e1d,color:#232634
    classDef error fill:#e78284,stroke:#d20f39,color:#232634
    classDef ok fill:#a6d189,stroke:#40a02b,color:#232634
    class S,G step
    class P,J,C decision
    class R,X,CS error
    class OK ok
```

Most messages come from the first branch: the processor reads your spec, finds a shape it cannot map correctly, and says so where you declared it. A refused spec writes no Impl, so every call to it also reports `cannot find symbol`; fix the refusal and those go with it. An error inside a generated file, an `*Impl` or a companion such as `*Errors`, means the processor accepted a declaration it should have refused. Please [report it](https://github.com/higher-kinded-j/higher-kinded-j/issues) with the declaration, since the cause is usually still there.

---

{sections}
---

**Previous:** [Rules and Limits](rules.md)
"""

if __name__ == "__main__":
    main()
