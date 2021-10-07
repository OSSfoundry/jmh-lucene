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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.PackedQuadPrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.PrintStreamInfoStream;
import org.apache.lucene.util.SuppressForbidden;
import org.locationtech.spatial4j.context.SpatialContext;

// javac -cp
// build/queries/lucene-queries-6.0.0-SNAPSHOT.jar:spatial/lib/spatial4j-0.5.jar:build/spatial/lucene-spatial-6.0.0-SNAPSHOT.jar:build/core/lucene-core-6.0.0-SNAPSHOT.jar:build/analysis/common/lucene-analyzers-common-6.0.0-SNAPSHOT.jar /l/util/src/main/perf/IndexOSM.java

// rm -rf javaindex; java -cp
// /l/util/src/main/perf:build/queries/lucene-queries-6.0.0-SNAPSHOT.jar:spatial/lib/spatial4j-0.5.jar:build/spatial/lucene-spatial-6.0.0-SNAPSHOT.jar:build/core/lucene-core-6.0.0-SNAPSHOT.jar:build/analysis/common/lucene-analyzers-common-6.0.0-SNAPSHOT.jar IndexOSM javaindex

/** The type Index osm. */
@SuppressForbidden(reason = "JMH uses std out for user output")
public class IndexOSM {

  /** Instantiates a new Index osm. */
  public IndexOSM() {}

  /**
   * The entry point of application.
   *
   * @param args the input arguments
   * @throws IOException the io exception
   */
  public static void main(String[] args) throws IOException {
    Directory dir = FSDirectory.open(Paths.get(args[0]));
    IndexWriterConfig iwc = new IndexWriterConfig(new WhitespaceAnalyzer());
    iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
    iwc.setMaxBufferedDocs(109630);
    iwc.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH);
    iwc.setMergePolicy(new LogDocMergePolicy());
    iwc.setMergeScheduler(new SerialMergeScheduler());
    iwc.setInfoStream(new PrintStreamInfoStream(System.out));
    IndexWriter w = new IndexWriter(dir, iwc);

    SpatialContext ctx = SpatialContext.GEO;

    // int maxLevels = 11;
    // SpatialPrefixTree grid = new GeohashPrefixTree(ctx, maxLevels);
    SpatialPrefixTree grid = new PackedQuadPrefixTree(ctx, 25);
    // SpatialPrefixTree grid = new QuadPrefixTree(ctx, 20);

    RecursivePrefixTreeStrategy strategy = new RecursivePrefixTreeStrategy(grid, "myGeoField");
    // strategy.setPruneLeafyBranches(false);

    CharsetDecoder decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

    int BUFFER_SIZE = 1 << 16; // 64K
    InputStream is =
        new FileInputStream(
            new File("/lucenedata/open-street-maps/latlon.subsetPlusAllLondon.txt"));
    BufferedReader reader = new BufferedReader(new InputStreamReader(is, decoder), BUFFER_SIZE);
    int count = 0;
    long t0 = System.currentTimeMillis();
    while (true) {
      String line = reader.readLine();
      if (line == null) {
        break;
      }
      String[] parts = line.split(",");

      double lat = Double.parseDouble(parts[1]);
      double lng = Double.parseDouble(parts[2]);
      Document doc = new Document();
      // doc.add(new StoredField("id", id));
      // doc.add(new NumericDocValuesField("id", id));
      for (Field f : strategy.createIndexableFields(ctx.makePoint(lng, lat))) {
        doc.add(f);
      }
      w.addDocument(doc);
      count++;
      if (count % 1000000 == 0) {
        System.out.println(count + "...");
      }
    }
    long t1 = System.currentTimeMillis();
    System.out.println(((t1 - t0) / 1000.) + " sec to index");

    // System.out.println("Force merge...");
    // w.forceMerge(1);
    long t2 = System.currentTimeMillis();
    // System.out.println(((t2-t1)/1000.) + " sec to forceMerge");
    w.close();
    long t3 = System.currentTimeMillis();
    System.out.println(((t3 - t2) / 1000.) + " sec to close");
    dir.close();
  }
}
