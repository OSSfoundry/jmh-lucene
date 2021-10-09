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
package org.apache.lucene.jmh.benchmarks;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.jmh.generators.RandomnessSource;

public class RndCollector<T> {

  private final int max;
  private final RandomnessSource random;
  private List<T> randomValues = new ArrayList<>();

  public RndCollector(RandomnessSource random, int max) {
    this.max = max;
    this.random = random;
  }

  public void collect(T val) {
    if (randomValues.size() < max) {
      randomValues.add(val);
    }
  }

  public List<T> getValues() {
    return randomValues;
  }

  public T getRandomValue() {
    return randomValues.get((int) random.next(0, randomValues.size() - 1L));
  }
}
