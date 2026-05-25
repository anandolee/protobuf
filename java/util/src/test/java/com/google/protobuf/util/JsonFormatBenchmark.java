package com.google.protobuf.util;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.proto.JsonTestProto.TestAllTypes;
import com.google.protobuf.util.proto.JsonTestProto.TestRecursive;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@SuppressWarnings({"unchecked", "rawtypes"})
public class JsonFormatBenchmark {

  private TestAllTypes largeSparseMessage;
  private String largeSparseMessageJson;

  private Struct deepStructMessage;
  private String deepStructMessageJson;

  private Struct wideStructMessage;
  private String wideStructMessageJson;

  // Added per request: benchmark on Struct case which also turns on the sortingMapKeys bool.
  private JsonFormat.Printer printer;
  private JsonFormat.Printer sortingPrinter;
  private JsonFormat.Parser parser;

  @Setup
  public void setUp() throws IOException {
    printer = JsonFormat.printer();
    sortingPrinter = JsonFormat.printer().sortingMapKeys();
    parser = JsonFormat.parser();

    // Case 1: Large Sparse message (representing ~100 nested repeated messages with 3rd level depth
    // on some of them)
    TestAllTypes.Builder parentBuilder = TestAllTypes.newBuilder();
    for (int i = 0; i < 100; i++) {
      TestAllTypes.Builder child =
          TestAllTypes.newBuilder().setOptionalInt32(i).setOptionalString("flat string node " + i);

      // Introduce 3rd-level nesting on some messages (every 5th message goes to 3rd level)
      if (i % 5 == 0) {
        child.setOptionalRecursive(
            TestRecursive.newBuilder()
                .setValue(i * 10)
                .setNested(TestRecursive.newBuilder().setValue(i * 100).build()));
      }
      parentBuilder.addRepeatedNestedMessage(
          TestAllTypes.NestedMessage.newBuilder().setValue(i).build());
      parentBuilder.addRepeatedRecursive(child.getOptionalRecursive());
    }
    largeSparseMessage = parentBuilder.build();
    largeSparseMessageJson = printer.print(largeSparseMessage);

    // Case 2: Very deep Struct message with fanout 2 (~10 depth)
    deepStructMessage = createDeepStruct(10);
    deepStructMessageJson = printer.print(deepStructMessage);

    // Case 3: Flat and wide Struct message (5000 x 100 x 5)
    wideStructMessage = createFlatAndWideStruct();
    wideStructMessageJson = printer.print(wideStructMessage);
  }

  private Struct createDeepStruct(int depth) {
    if (depth <= 1) {
      return Struct.newBuilder()
          .putFields("leaf_key_a", Value.newBuilder().setStringValue("leaf_val").build())
          .putFields("leaf_key_b", Value.newBuilder().setStringValue("leaf_val").build())
          .build();
    }
    return Struct.newBuilder()
        .putFields(
            "level_" + depth + "_a",
            Value.newBuilder().setStructValue(createDeepStruct(depth - 1)).build())
        .putFields(
            "level_" + depth + "_b",
            Value.newBuilder().setStructValue(createDeepStruct(depth - 1)).build())
        .build();
  }

  private Struct createFlatAndWideStruct() {
    // Level 3: 5 keys
    Struct.Builder level3Builder = Struct.newBuilder();
    for (int i = 0; i < 5; i++) {
      level3Builder.putFields(
          "leaf_key_" + i, Value.newBuilder().setStringValue("leaf_val_" + i).build());
    }
    Struct level3Struct = level3Builder.build();

    // Level 2: 100 keys
    Struct.Builder level2Builder = Struct.newBuilder();
    for (int i = 0; i < 100; i++) {
      level2Builder.putFields(
          "mid_key_" + i, Value.newBuilder().setStructValue(level3Struct).build());
    }
    Struct level2Struct = level2Builder.build();

    // Level 1: 5000 keys
    Struct.Builder level1Builder = Struct.newBuilder();
    for (int i = 0; i < 5000; i++) {
      level1Builder.putFields(
          "top_key_" + i, Value.newBuilder().setStructValue(level2Struct).build());
    }
    return level1Builder.build();
  }

  // ==========================================
  // PRINTING BENCHMARKS
  // ==========================================

  @Benchmark
  public void benchmarkSparseMessagePrinting(Blackhole bh) throws IOException {
    bh.consume(printer.print(largeSparseMessage));
  }

  @Benchmark
  public void benchmarkDeepStructPrinting(Blackhole bh) throws IOException {
    bh.consume(printer.print(deepStructMessage));
  }

  @Benchmark
  public void benchmarkDeepStructPrintingSorted(Blackhole bh) throws IOException {
    bh.consume(sortingPrinter.print(deepStructMessage));
  }

  @Benchmark
  public void benchmarkWideStructPrinting(Blackhole bh) throws IOException {
    bh.consume(printer.print(wideStructMessage));
  }

  @Benchmark
  public void benchmarkWideStructPrintingSorted(Blackhole bh) throws IOException {
    bh.consume(sortingPrinter.print(wideStructMessage));
  }

  // ==========================================
  // PARSING BENCHMARKS
  // ==========================================

  @Benchmark
  public void benchmarkSparseMessageParsing(Blackhole bh) throws IOException {
    TestAllTypes.Builder builder = TestAllTypes.newBuilder();
    parser.merge(largeSparseMessageJson, builder);
    bh.consume(builder.build());
  }

  @Benchmark
  public void benchmarkDeepStructParsing(Blackhole bh) throws IOException {
    Struct.Builder builder = Struct.newBuilder();
    parser.merge(deepStructMessageJson, builder);
    bh.consume(builder.build());
  }

  @Benchmark
  public void benchmarkWideStructParsing(Blackhole bh) throws IOException {
    Struct.Builder builder = Struct.newBuilder();
    parser.merge(wideStructMessageJson, builder);
    bh.consume(builder.build());
  }
}
