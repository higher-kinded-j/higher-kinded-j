// Fixture for hkj-book/src/monads/vstream_parallel.md
//
// The page fetches profiles, orders and events concurrently, then batches inserts. The clients and
// the streams the snippets feed on are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamPar;
import org.higherkindedj.hkt.vtask.VTask;

record UserProfile(String userId, String name) {}

record Order(String id) {}

record Event(String name) {}

record Record(String id) {}

record InsertResult(String id, boolean inserted) {}

record Image(String url) {}

record UploadResult(String url) {}

final class ApiClient {

  UserProfile fetchProfile(String userId) {
    return new UserProfile(userId, "Alice");
  }
}

final class Db {

  List<InsertResult> batchInsert(List<Record> batch) {
    return batch.stream().map(r -> new InsertResult(r.id(), true)).toList();
  }
}

final class ImageProcessor {

  static Image resize(Image image) {
    return image;
  }
}

class Fixture {

  static final ApiClient apiClient = new ApiClient();

  static final Db db = new Db();

  static final VStream<Integer> stream = VStream.range(1, 11);

  static final VStream<Integer> numbers = VStream.range(1, 100);

  static final VStream<String> customerIds = VStream.of("c1", "c2", "c3");

  static final VStream<Record> recordStream =
      VStream.fromList(List.of(new Record("r1"), new Record("r2")));

  static final VStream<String> imageUrls = VStream.of("https://example.com/1.png");

  static Integer expensiveComputation(int n) {
    return n * 2;
  }

  static VStream<Order> fetchOrders(String customerId) {
    return VStream.of(new Order(customerId + "-1"));
  }

  static VStream<Event> fetchEventsFromServiceA() {
    return VStream.of(new Event("a"));
  }

  static VStream<Event> fetchEventsFromServiceB() {
    return VStream.of(new Event("b"));
  }

  static VStream<Event> fetchEventsFromServiceC() {
    return VStream.of(new Event("c"));
  }

  static Image download(String url) {
    return new Image(url);
  }

  static UploadResult upload(Image image) {
    return new UploadResult(image.url());
  }
}
