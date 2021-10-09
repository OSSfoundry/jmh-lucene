package org.apache.lucene.jmh.benchmarks;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.jmh.generators.Queries.QueryGen;
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
    return randomValues.get((int) random.next(0, randomValues.size() - 1));
  }
}
