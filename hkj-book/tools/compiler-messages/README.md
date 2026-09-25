# Compiler Messages generator

The script in this directory generates `hkj-book/src/mapping/compiler_errors.md` and its snippet
fixture, `hkj-examples/src/test/resources/fixtures/mapping_compiler_errors.java`. Edit the data
here and rerun the script. Do not edit the page itself: the next run would overwrite the edit.

## Run it

From the repository root (the script itself also runs from any directory):

```bash
./gradlew :hkj-examples:bookMessagesClasspath   # writes hkj-examples/build/book-messages/*.txt
python3 hkj-book/tools/compiler-messages/build_messages.py
```

The Gradle task writes the classpath, the processor path and the toolchain's `javac`, the same ones
the book gate compiles with. It also rebuilds the processor's jars, so rerun it after every change
to the processor, or the script captures messages from a stale jar. The script needs Python 3.

The script compiles every entry's reproducer and captures the message the processor really prints,
in English whatever your locale, and renders the page from it. If a reproducer stops producing its
message, it prints the compiler output and fails without writing anything.

**Run it once before you change anything.** It should reproduce the committed page exactly. If it
does not, the processor has reworded a message since the page was last generated: commit that
regeneration on its own, so your change's diff stays yours.

## Add an entry

Each entry is a `dict` in a group of `GROUPS` in `messages.py`:

| Field | What it holds |
|---|---|
| `id` | The entry's anchor on the page. Never change it, because other pages link to it. |
| `heading` | The message with its names replaced: `X` and `Y` for types, `x` and `y` for components or methods, `T` for a type argument, `p` for a package. |
| `fragment` | Distinctive words from the message, shown in the triage table. They must appear in `heading`. |
| `marker` | Optional. The words the gate holds the page to, when they differ from `fragment`. |
| `display` | Optional. The triage table's text, when `fragment` alone reads badly. |
| `meaning`, `fix` | Usually one sentence each. The fix is code or an imperative. |
| `rule` | `(link text, target)`: the rule's home, usually on `rules.md`. Write that heading first, so the link resolves. |
| `code` | The smallest declaration that provokes the message, compiled in package `com.example` with the shared imports and types at the top of `messages.py`. A top-level declaration starts with an annotation, `record`, `class`, `interface`, `sealed`, `final`, `abstract` or `enum`; any other top-level line is a call-site statement, which goes at the end and which the script wraps in a method. |
| `kind` | `"note"` for a note rather than an error. The entry is then held by `verify:reports`. |

The marker, or the fragment when there is no marker, must be at least 10 characters long and
contain no `"`, because it becomes the verify comment's quoted fragment.

Each entry also needs a line in `SHORT` in `build_messages.py`, the triage table's "what it means"
column. The rest of the page lives in `build_messages.py` too:
- `MOST_OFTEN` lists the entries in the "Seen most often" table.
- `PAGE` is the page's intro, diagram and footer. It is a `str.format` template, so a literal `{` or
  `}` is doubled, as in the diagram's `P{{...}}` shapes.
- A new group in `GROUPS` needs a title and an id. The id becomes a section anchor, and it is as
  permanent as an entry's.

## What the build holds

After the script runs, `./gradlew :hkj-examples:bookVerify` compiles every reproducer on each build.
The build fails if a message stops carrying the words its marker quotes. It does not check the rest
of the message shown on the page, so rerun the script after any change to a message's wording.

## Which messages belong here

The page catalogues the refusals a reader is likely to meet, not every refusal the processor has.
The [Mapping Chapter Guide](../../../docs/MAPPING-CHAPTER-GUIDE.md) says which qualify, and where
the rule each one enforces lives.
