/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.jmh.benchmarks.search;

import static org.apache.lucene.jmh.generators.Docs.docs;
import static org.apache.lucene.jmh.generators.SourceDSL.strings;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.jmh.BaseBenchState;
import org.apache.lucene.jmh.generators.Distribution;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;

/** The type. */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@Threads(1)
@Warmup(time = 1, iterations = 1)
@Measurement(time = 1, iterations = 1)
@Fork(value = 1)
@Timeout(time = 600)
public class FuzzyQuery {

  /** Instantiates a new Json faceting. */
  public FuzzyQuery() {}

  /** The type Bench state. */
  @State(Scope.Benchmark)
  public static class BenchState {

    private Iterator<Document> it;

    /** Instantiates a new Bench state. */
    public BenchState() {}

    /**
     * Sets .
     *
     * @param baseBenchState the base bench state
     * @throws Exception the exception
     */
    @Setup(Level.Trial)
    public void setup(BaseBenchState baseBenchState) throws Exception {
      it =
          docs()
              .field(
                  "field1",
                  strings()
                      .realisticUnicode()
                      .ofLengthBetween(2, 8)
                      .withDistribution(Distribution.UNIFORM))
              .preGenerate(10);
      it.next();
    }

    /**
     * Teardown.
     *
     * @param benchmarkParams the benchmark params
     * @throws Exception the exception
     */
    @TearDown(Level.Trial)
    public void teardown(BenchmarkParams benchmarkParams) throws Exception {}
  }

  /**
   * search
   *
   * @param state the state
   * @return the object
   * @throws Exception the exception
   */
  @Benchmark
  public Object orHighHigh(BenchState state) throws Exception {
    //   String.valueOf(state.it.next());
    return null;
  }
}
