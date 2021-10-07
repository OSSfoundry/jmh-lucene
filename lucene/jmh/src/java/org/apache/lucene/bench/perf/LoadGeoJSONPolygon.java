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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.util.SuppressForbidden;

// ant jar; javac -cp build/core/classes/java /l/util/src/main/perf/LoadGeoJSONPolygon.java

// java -cp build/core/classes/java:/l/util/src/main/perf LoadGeoJSONPolygon.java
// /l/BIGPolygon.mikemccand/cleveland.geojson

/** The type Load geo json polygon. */
@SuppressForbidden(reason = "JMH uses std out for user output")
public class LoadGeoJSONPolygon {

  /** Instantiates a new Load geo json polygon. */
  public LoadGeoJSONPolygon() {}

  /**
   * The entry point of application.
   *
   * @param args the input arguments
   * @throws Exception the exception
   */
  public static void main(String[] args) throws Exception {
    byte[] encoded = Files.readAllBytes(Paths.get(args[0]));
    String s = new String(encoded, StandardCharsets.UTF_8);
    System.out.println("Polygon string is " + s.length() + " characters");
    Polygon[] result = Polygon.fromGeoJSON(s);
    System.out.println(result.length + " polygons:");
    int vertexCount = 0;
    for (Polygon polygon : result) {
      vertexCount += polygon.getPolyLats().length;
      for (Polygon hole : polygon.getHoles()) {
        vertexCount += hole.getPolyLats().length;
      }
      System.out.println(
          "  "
              + polygon.getPolyLats().length
              + " vertices; "
              + polygon.getHoles().length
              + " holes; "
              + vertexCount
              + " total vertices");
    }
  }
}
