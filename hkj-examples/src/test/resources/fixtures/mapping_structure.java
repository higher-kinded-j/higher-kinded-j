// Fixture for hkj-book/src/mapping/structure.md (see hkj-examples/BOOK-SNIPPETS.md).
// The "Across modules" snippet declares only the downstream spec; the pair it nests, and the
// spec mapping that pair, stand in for the dependency (in the gate they share the compilation,
// which resolves identically).
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;

record Customer(String name, String email) {}

record CustomerDto(String name, String email) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}

record Invoice(String id, Customer customer) {}

record InvoiceDto(String id, CustomerDto customer) {}
