// Fixture for hkj-book/src/monads/vstream_advanced.md
//
// The page covers four separate extensions - StreamTraversal, the reactive bridge, the natural
// transformations and VStreamContext - so what each section elides is small: an event source, a
// context to query, and the transformations a later snippet composes.
//
// `StreamTraversal` is not imported here: the page quotes its shape as a top-level interface, and
// a single-type import would collide with that declaration. The snippets that use it name it.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.NaturalTransformation;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VStreamTransformations;
import org.higherkindedj.hkt.effect.context.VStreamContext;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.stream.StreamKind;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vstream.VStreamReactive;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;

record Event(String name) {}

class Fixture {

  static final VStreamContext<Integer> ctx = VStreamContext.range(1, 20);

  static final NaturalTransformation<StreamKind.Witness, VStreamKind.Witness> streamToVStream =
      VStreamTransformations.streamToVStream();

  static final NaturalTransformation<VStreamKind.Witness, ListKind.Witness> vstreamToList =
      VStreamTransformations.vstreamToList();

  static Flow.Publisher<Event> getEventSource() {
    return VStreamReactive.toPublisher(VStream.of(new Event("started")));
  }
}
