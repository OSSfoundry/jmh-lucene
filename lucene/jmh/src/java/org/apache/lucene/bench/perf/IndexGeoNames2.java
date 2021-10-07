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
package org.apache.lucene.bench.perf;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.PrintStreamInfoStream;
import org.apache.lucene.util.SuppressForbidden;

// rm -rf /l/scratch/indices/geonames; pushd core; ant jar; popd; javac -d /l/util/build -cp
// build/core/classes/java:build/analysis/common/classes/java
// /l/util/src/main/perf/IndexGeoNames2.java; java -cp
// /l/util/build:build/core/classes/java:build/analysis/common/classes/java perf.IndexGeoNames2
// /lucenedata/geonames/allCountries.txt /l/scratch/indices/geonames

/** The type Index geo names 2. */
@SuppressForbidden(reason = "JMH uses std out for user output")
public class IndexGeoNames2 {

  /** Instantiates a new Index geo names 2. */
  public IndexGeoNames2() {}

  /**
   * The entry point of application.
   *
   * @param args the input arguments
   * @throws Exception the exception
   */
  public static void main(String[] args) throws Exception {
    String geoNamesFile = args[0];
    Path indexPath = Paths.get(args[1]);

    Directory dir = FSDirectory.open(indexPath);
    IndexWriter iw =
        new IndexWriter(
            dir,
            new IndexWriterConfig(null)
                .setRAMBufferSizeMB(50)
                // .setRAMBufferSizeMB(1)
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE)
                .setMergePolicy(NoMergePolicy.INSTANCE)
                .setInfoStream(new PrintStreamInfoStream(System.out)));
    FileInputStream fs = new FileInputStream(geoNamesFile);
    BufferedReader r = new BufferedReader(new InputStreamReader(fs, StandardCharsets.UTF_8));
    String line = null;
    Document doc = new Document();
    Field[] fields = new Field[19];
    for (int i = 0; i < fields.length; i++) {
      fields[i] = new StringField("" + i, "", Field.Store.NO);
      doc.add(fields[i]);
    }
    int docCount = 0;
    long prev = System.currentTimeMillis();
    while ((line = r.readLine()) != null) {
      if ((++docCount % 10000) == 0) {
        long curr = System.currentTimeMillis();
        System.out.println("Indexed: " + docCount + " (" + (curr - prev) + ")");
        prev = curr;
      }
      String[] parts = line.split("\t");
      for (int i = 0; i < fields.length; i++) {
        fields[i].setStringValue(parts[i]);
      }
      iw.addDocument(doc);
    }
    r.close();
    iw.close();
    dir.close();
  }
}
