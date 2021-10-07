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
package org.apache.lucene.bench.search;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.classic.ClassicAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.shingle.ShingleAnalyzerWrapper;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.bench.perf.IndexState;
import org.apache.lucene.bench.perf.IndexThreads;
import org.apache.lucene.bench.perf.LineFileDocs;
import org.apache.lucene.bench.perf.LocalTaskSource;
import org.apache.lucene.bench.perf.OpenDirectory;
import org.apache.lucene.bench.perf.PerfUtils;
import org.apache.lucene.bench.perf.Task;
import org.apache.lucene.bench.perf.TaskParser;
import org.apache.lucene.bench.perf.TaskSource;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene90.Lucene90Codec;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.index.ConcurrentMergeScheduler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoDeletionPolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.spell.DirectSpellChecker;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NRTCachingDirectory;
import org.apache.lucene.util.Constants;
import org.apache.lucene.util.InfoStream;
import org.apache.lucene.util.PrintStreamInfoStream;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.SuppressForbidden;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;

/** The type. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Threads(1)
@Warmup(time = 15, iterations = 5)
@Measurement(time = 20, iterations = 5)
@Fork(value = 1)
@Timeout(time = 600)
@SuppressForbidden(reason = "JMH uses std out for user output")
public class SearchPerf {

  /** Instantiates a new Json faceting. */
  public SearchPerf() {}

  private static IndexSearcher createIndexSearcher(
      IndexReader reader, ExecutorService executorService) {
    return new IndexSearcher(reader, executorService);
  }

  /** The type Single index searcher. */
  // ReferenceManager that never changes its searcher:
  public static class SingleIndexSearcher extends ReferenceManager<IndexSearcher> {

    /**
     * Instantiates a new Single index searcher.
     *
     * @param s the s
     */
    public SingleIndexSearcher(IndexSearcher s) {
      this.current = s;
    }

    @Override
    public void decRef(IndexSearcher ref) throws IOException {
      ref.getIndexReader().decRef();
    }

    @Override
    protected IndexSearcher refreshIfNeeded(IndexSearcher ref) {
      return null;
    }

    @Override
    protected boolean tryIncRef(IndexSearcher ref) {
      return ref.getIndexReader().tryIncRef();
    }

    @Override
    protected int getRefCount(IndexSearcher ref) {
      return ref.getIndexReader().getRefCount();
    }
  }

  /** The type Bench state. */
  @State(Scope.Benchmark)
  @SuppressForbidden(reason = "JMH uses std out for user output")
  public static class BenchState {

    /** The Index path. */
    @Param({
      "/mnt/d1/lucene-bench/indices/wikimedium5m.lucene_baseline.facets.taxonomy:Date.taxonomy:Month.taxonomy:DayOfYear.sortedset:Month.sortedset:DayOfYear.Lucene90.Lucene90.nd5M/index"
    })
    String indexPath;

    /** The Dir. */
    @Param({"MMapDirectory"})
    String dirImpl;
    /** The Field name. */
    @Param({"body"})
    String fieldName;

    /** The Analyzer. */
    @Param({"StandardAnalyzer"})
    String analyzer;
    /** The Tasks file. */
    @Param({"/mnt/d1/lucene-bench/data/wikimedium500.tasks"})
    String tasksFile;
    /** The Search thread count. */
    @Param({"2"})
    int searchThreadCount;
    /** The Print heap. */
    @Param({"true"})
    boolean printHeap;
    /** The Do pk lookup. */
    @Param({"false"})
    boolean doPKLookup;
    /** The Do concurrent searches. */
    @Param({"true"})
    boolean doConcurrentSearches;
    /** The Top n. */
    @Param({"10"})
    int topN;
    /** The Do stored loads. */
    @Param({"false"})
    boolean doStoredLoads;

    /** The Random seed. */
    // Used to shuffle the random subset of tasks:
    @Param({"0"})
    long randomSeed;

    /** The Similarity. */
    // TODO: this could be way better.
    @Param({"BM25Similarity"})
    String similarity;

    /** The Commit. */
    @Param({"multi"})
    String commit;
    /** The Hilite. */
    @Param({"FastVectorHighlighter"})
    String hiliteImpl;

    /** The Log file. */
    @Param({"search.log"})
    String logFile;

    /** The Verify check sum. */
    @Param({"false"})
    boolean verifyCheckSum;

    /** The Recache filter deletes. */
    @Param({"false"})
    boolean recacheFilterDeletes;

    /** The Vector file. */
    @Param({""})
    String vectorFile;

    /** The Index thread count. */
    @Param({"1"})
    int indexThreadCount;
    /** The Line docs file. */
    @Param({"/mnt/d1/lucene-bench/data/enwiki-20120502-lines-1k.txt"})
    String lineDocsFile;
    /** The Docs per sec per thread. */
    @Param({"10"})
    float docsPerSecPerThread;
    /** The Reopen every sec. */
    @Param({"5"})
    float reopenEverySec;
    /** The Store body. */
    @Param({"false"})
    boolean storeBody;
    /** The Tvs body. */
    @Param({"false"})
    boolean tvsBody;
    /** The Use cfs. */
    @Param({"false"})
    boolean useCFS;
    /** The Default postings format. */
    @Param({"Lucene90"})
    String defaultPostingsFormat;
    /** The Id field postings format. */
    @Param({"Lucene90"})
    String idFieldPostingsFormat;
    /** The Verbose. */
    @Param({"false"})
    boolean verbose;
    /** The Clone docs. */
    @Param({"false"})
    boolean cloneDocs;
    /** The Mode. */
    @Param({"update"})
    String mode;

    /** The Facets. */
    @Param({"taxonomy"})
    String facets;

    /** The Nrt. */
    @Param({"true"})
    boolean nrt;

    /** The Executor service. */
    ExecutorService executorService;
    /** The Mgr. */
    ReferenceManager<IndexSearcher> mgr;
    /** The Reopen thread. */
    Thread reopenThread;
    /** The Shutdown. */
    volatile boolean shutdown;
    /** The Threads. */
    IndexThreads threads;
    /** The Tasks. */
    TaskSource tasks;

    private IndexState indexState;

    /** Instantiates a new Bench state. */
    public BenchState() {}

    /**
     * Sets .
     *
     * @param benchmarkParams the benchmark params
     * @throws Exception the exception
     */
    @Setup(Level.Trial)
    public void setup(BenchmarkParams benchmarkParams) throws Exception {

      Directory dir0;
      //      final String dirPath = args.getString("-indexPath") + "/index";
      //      final String dirImpl = args.getString("-dirImpl");

      OpenDirectory od = OpenDirectory.get(dirImpl);

      dir0 = od.open(Paths.get(indexPath));

      // TODO: NativeUnixDir?

      //      final String analyzer = args.getString("-analyzer");
      //      final String tasksFile = args.getString("-taskSource");
      //      final int searchThreadCount = args.getInt("-searchThreadCount");
      //      final String fieldName = args.getString("-field");
      //      final boolean printHeap = args.getFlag("-printHeap");
      //      final boolean doPKLookup = args.getFlag("-pk");
      //      final boolean doConcurrentSearches = args.getFlag("-concurrentSearches");
      //      final int topN = args.getInt("-topN");
      //      final boolean doStoredLoads = args.getFlag("-loadStoredFields");

      int cores = Runtime.getRuntime().availableProcessors();

      if (doConcurrentSearches) {
        executorService =
            new ThreadPoolExecutor(
                cores,
                cores,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                  Thread thread = new Thread(r);
                  thread.setName("ConcurrentSearches");
                  thread.setDaemon(true);
                  return thread;
                });
      } else {
        executorService = null;
      }

      // Used to choose which random subset of tasks we will
      // run, to generate the PKLookup tasks, and to generate
      // any random pct filters:
      //      final long staticRandomSeed = args.getLong("-staticSeed");
      //
      //      // Used to shuffle the random subset of tasks:
      //      final long randomSeed = args.getLong("-seed");
      //
      //      // TODO: this could be way better.
      //      final String similarity = args.getString("-similarity");
      // now reflect
      final Class<? extends Similarity> simClazz =
          Class.forName("org.apache.lucene.search.similarities." + similarity)
              .asSubclass(Similarity.class);
      final Similarity sim = simClazz.newInstance();

      System.out.println("Using dir impl " + dir0.getClass().getName());
      System.out.println("Analyzer " + analyzer);
      System.out.println("Similarity " + similarity);
      System.out.println("Search thread count " + searchThreadCount);
      System.out.println("topN " + topN);
      System.out.println("JVM " + (Constants.JRE_IS_64BIT ? "is" : "is not") + " 64bit");
      System.out.println("Pointer is " + RamUsageEstimator.NUM_BYTES_OBJECT_REF + " bytes");
      System.out.println("Concurrent segment reads is " + doConcurrentSearches);

      final Analyzer a;
      if (analyzer.equals("EnglishAnalyzer")) {
        a = new EnglishAnalyzer();
      } else if (analyzer.equals("ClassicAnalyzer")) {
        a = new ClassicAnalyzer();
      } else if (analyzer.equals("StandardAnalyzer")) {
        a = new StandardAnalyzer();
      } else if (analyzer.equals("StandardAnalyzerNoStopWords")) {
        a = new StandardAnalyzer(CharArraySet.EMPTY_SET);
      } else if (analyzer.equals("ShingleStandardAnalyzer")) {
        a =
            new ShingleAnalyzerWrapper(
                new StandardAnalyzer(CharArraySet.EMPTY_SET),
                2,
                2,
                ShingleFilter.DEFAULT_TOKEN_SEPARATOR,
                true,
                true,
                ShingleFilter.DEFAULT_FILLER_TOKEN);
      } else {
        throw new RuntimeException("unknown analyzer " + analyzer);
      }

      final IndexWriter writer;
      final Directory dir;

      //      final String commit = args.getString("-commit");
      //      final String hiliteImpl = args.getString("-hiliteImpl");
      //
      //      final String logFile = args.getString("-log");

      final long tSearcherStart = System.currentTimeMillis();

      //      final boolean verifyCheckSum = !args.getFlag("-skipVerifyChecksum");
      //      final boolean recacheFilterDeletes = args.getFlag("-recacheFilterDeletes");
      //      final String vectorFile;
      if (vectorFile.isEmpty()) {
        vectorFile = null;
      }

      if (recacheFilterDeletes) {
        throw new UnsupportedOperationException("recacheFilterDeletes was deprecated");
      }

      if (nrt) {
        // TODO: get taxoReader working here too
        // TODO: factor out & share this CL processing w/ Indexer
        //        final int indexThreadCount = args.getInt("-indexThreadCount");
        //        final String lineDocsFile = args.getString("-lineDocsFile");
        //        final float docsPerSecPerThread = args.getFloat("-docsPerSecPerThread");
        //        final float reopenEverySec = args.getFloat("-reopenEverySec");
        //        final boolean storeBody = args.getFlag("-store");
        //        final boolean tvsBody = args.getFlag("-tvs");
        //        final boolean useCFS = args.getFlag("-cfs");
        //        final String defaultPostingsFormat = args.getString("-postingsFormat");
        //        final String idFieldPostingsFormat = args.getString("-idFieldPostingsFormat");
        //        final boolean verbose = args.getFlag("-verbose");
        //        final boolean cloneDocs = args.getFlag("-cloneDocs");
        final IndexThreads.Mode threadsMode =
            IndexThreads.Mode.valueOf(mode.toUpperCase(Locale.ROOT));

        final long reopenEveryMS = (long) (1000 * reopenEverySec);

        if (verbose) {
          InfoStream.setDefault(new PrintStreamInfoStream(System.out));
        }

        if (!dirImpl.equals("RAMExceptDirectPostingsDirectory")) {
          System.out.println("Wrap NRTCachingDirectory");
          dir0 = new NRTCachingDirectory(dir0, 20, 400.0);
        }

        dir = dir0;

        final IndexWriterConfig iwc = new IndexWriterConfig(a);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.APPEND);
        iwc.setRAMBufferSizeMB(256.0);
        iwc.setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE);

        // TODO: also RAMDirExceptDirect...?  need to
        // ... block deletes against wrapped FSDir?

        if (commit != null && commit.length() > 0) {
          System.out.println("Opening writer on commit=" + commit);
          iwc.setIndexCommit(PerfUtils.findCommitPoint(commit, dir));
        }

        ((TieredMergePolicy) iwc.getMergePolicy()).setNoCFSRatio(useCFS ? 1.0 : 0.0);
        // ((TieredMergePolicy) iwc.getMergePolicy()).setMaxMergedSegmentMB(1024);
        // ((TieredMergePolicy) iwc.getMergePolicy()).setReclaimDeletesWeight(3.0);
        // ((TieredMergePolicy) iwc.getMergePolicy()).setMaxMergeAtOnce(4);

        final Codec codec =
            new Lucene90Codec() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                return PostingsFormat.forName(
                    field.equals("id") ? idFieldPostingsFormat : defaultPostingsFormat);
              }
            };
        iwc.setCodec(codec);

        final ConcurrentMergeScheduler cms = (ConcurrentMergeScheduler) iwc.getMergeScheduler();
        // Only let one merge run at a time...
        // ... but queue up up to 4, before index thread is stalled:
        cms.setMaxMergesAndThreads(4, 1);

        iwc.setMergedSegmentWarmer(
            new IndexWriter.IndexReaderWarmer() {
              @Override
              @SuppressForbidden(reason = "JMH uses std out for user output")
              public void warm(LeafReader reader) throws IOException {
                final long t0 = System.currentTimeMillis();
                // System.out.println("DO WARM: " + reader);
                IndexSearcher s = createIndexSearcher(reader, executorService);
                s.setQueryCache(null); // don't bench the cache
                s.search(new TermQuery(new Term(fieldName, "united")), 10);
                final long t1 = System.currentTimeMillis();
                System.out.println(
                    "warm segment="
                        + reader
                        + " numDocs="
                        + reader.numDocs()
                        + ": took "
                        + (t1 - t0)
                        + " msec");
              }
            });

        writer = new IndexWriter(dir, iwc);
        System.out.println("Initial writer.maxDoc()=" + writer.getDocStats().maxDoc);

        // TODO: add -nrtBodyPostingsOffsets instead of
        // hardwired false:
        boolean addDVFields =
            threadsMode == IndexThreads.Mode.BDV_UPDATE
                || threadsMode == IndexThreads.Mode.NDV_UPDATE;
        LineFileDocs lineFileDocs =
            new LineFileDocs(
                lineDocsFile,
                false,
                storeBody,
                tvsBody,
                false,
                cloneDocs,
                null,
                null,
                null,
                addDVFields,
                null,
                0);
        threads =
            new IndexThreads(
                new Random(17),
                writer,
                new AtomicBoolean(false),
                lineFileDocs,
                indexThreadCount,
                -1,
                false,
                false,
                threadsMode,
                docsPerSecPerThread,
                null,
                -1.0,
                -1);
        threads.start();

        mgr =
            new SearcherManager(
                writer,
                new SearcherFactory() {
                  @Override
                  public IndexSearcher newSearcher(IndexReader reader, IndexReader previous) {
                    IndexSearcher s = createIndexSearcher(reader, executorService);
                    s.setQueryCache(null); // don't bench the cache
                    s.setSimilarity(sim);
                    return s;
                  }
                });

        System.out.println("reopen every " + reopenEverySec);

        reopenThread =
            new Thread() {
              @Override
              @SuppressForbidden(reason = "JMH uses std out for user output")
              public void run() {
                try {
                  final long startMS = System.currentTimeMillis();

                  int reopenCount = 1;
                  while (!shutdown) {
                    final long sleepMS =
                        startMS + (reopenCount * reopenEveryMS) - System.currentTimeMillis();
                    if (sleepMS < 0) {
                      System.out.println(
                          "WARNING: reopen fell behind by " + Math.abs(sleepMS) + " ms");
                    } else {
                      Thread.sleep(sleepMS);
                    }

                    mgr.maybeRefresh();
                    reopenCount++;
                    IndexSearcher s = mgr.acquire();
                    try {
                      System.out.println(
                          String.format(
                              Locale.ENGLISH,
                              "%.1fs: done reopen; writer.maxDoc()=%d; searcher.maxDoc()=%d; searcher.numDocs()=%d",
                              (System.currentTimeMillis() - startMS) / 1000.0,
                              writer.getDocStats().maxDoc,
                              s.getIndexReader().maxDoc(),
                              s.getIndexReader().numDocs()));
                    } finally {
                      mgr.release(s);
                    }
                  }
                } catch (InterruptedException e) {
                  System.out.println(e.getMessage());
                  Thread.currentThread().interrupt();
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }
            };
        reopenThread.setName("ReopenThread");
        reopenThread.setPriority(4 + Thread.currentThread().getPriority());
        reopenThread.start();

      } else {
        dir = dir0;
        writer = null;
        final DirectoryReader reader;
        if (commit != null && commit.length() > 0) {
          System.out.println("Opening searcher on commit=" + commit);
          reader = DirectoryReader.open(PerfUtils.findCommitPoint(commit, dir));
        } else {
          // open last commit
          reader = DirectoryReader.open(dir);
        }

        IndexSearcher s = createIndexSearcher(reader, executorService);
        s.setQueryCache(null); // don't bench the cache
        s.setSimilarity(sim);
        System.out.println(
            "maxDoc="
                + reader.maxDoc()
                + " numDocs="
                + reader.numDocs()
                + " %tg live docs="
                + (100. * reader.maxDoc() / reader.numDocs()));

        mgr = new SingleIndexSearcher(s);
      }

      System.out.println(
          (System.currentTimeMillis() - tSearcherStart) + " msec to init searcher/NRT");

      {
        IndexSearcher s = mgr.acquire();
        try {
          System.out.println(
              "Searcher: numDocs="
                  + s.getIndexReader().numDocs()
                  + " maxDoc="
                  + s.getIndexReader().maxDoc()
                  + ": "
                  + s);
        } finally {
          mgr.release(s);
        }
      }

      // System.out.println("searcher=" + searcher);

      FacetsConfig facetsConfig = new FacetsConfig();
      facetsConfig.setHierarchical("Date.taxonomy", true);

      // all unique facet group fields ($facet alone, by default):
      final Set<String> facetFields = new HashSet<>();

      // facet dim name -> facet method
      final Map<String, Integer> facetDimMethods = new HashMap<>();
      if (facets != null) {
        for (String arg : facets.split(",")) {
          String[] dims = arg.split(";");
          String facetGroupField;
          String facetMethod;
          if (dims[0].equals("taxonomy") || dims[0].equals("sortedset")) {
            // method --> use the default facet field for this group
            facetGroupField = FacetsConfig.DEFAULT_INDEX_FIELD_NAME;
            facetMethod = dims[0];
          } else {
            // method:indexFieldName --> use a custom facet field for this group
            int i = dims[0].indexOf(":");
            if (i == -1) {
              throw new IllegalArgumentException(
                  "-facets: expected (taxonomy|sortedset):fieldName but got " + dims[0]);
            }
            facetMethod = dims[0].substring(0, i);
            if (facetMethod.equals("taxonomy") == false
                && facetMethod.equals("sortedset") == false) {
              throw new IllegalArgumentException(
                  "-facets: expected (taxonomy|sortedset):fieldName but got " + dims[0]);
            }
            facetGroupField = dims[0].substring(i + 1);
          }
          facetFields.add(facetGroupField);
          for (int i = 1; i < dims.length; i++) {
            int flag;
            if (facetDimMethods.containsKey(dims[i])) {
              flag = facetDimMethods.get(dims[i]);
            } else {
              flag = 0;
            }
            if (facetMethod.equals("taxonomy")) {
              flag |= 1;
              facetsConfig.setIndexFieldName(dims[i] + ".taxonomy", facetGroupField + ".taxonomy");
            } else {
              flag |= 2;
              facetsConfig.setIndexFieldName(
                  dims[i] + ".sortedset", facetGroupField + ".sortedset");
            }
            facetDimMethods.put(dims[i], flag);
          }
        }
      }

      TaxonomyReader taxoReader;
      Path taxoPath = Paths.get(indexPath, "facets");
      Directory taxoDir = od.open(taxoPath);
      if (DirectoryReader.indexExists(taxoDir)) {
        taxoReader = new DirectoryTaxonomyReader(taxoDir);
        System.out.println("Taxonomy has " + taxoReader.getSize() + " ords");
      } else {
        taxoReader = null;
      }

      long staticRandomSeed = 0;
      final Random staticRandom = new Random(staticRandomSeed);
      // final Random random = new Random(randomSeed);

      final DirectSpellChecker spellChecker = new DirectSpellChecker();
      indexState =
          new IndexState(
              mgr, taxoReader, fieldName, spellChecker, hiliteImpl, facetsConfig, facetDimMethods);

      final QueryParser queryParser = new QueryParser("body", a);
      TaskParser taskParser =
          new TaskParser(
              indexState, queryParser, fieldName, topN, staticRandom, vectorFile, doStoredLoads);

      // Load the tasks from a file:
      final int taskRepeatCount = 1;
      final int numTaskPerCat = 5;
      tasks =
          new LocalTaskSource(
              indexState, taskParser, tasksFile, staticRandom, numTaskPerCat, doPKLookup);
      System.out.println("Task repeat count " + taskRepeatCount);
      System.out.println("Tasks file " + tasksFile);
      System.out.println("Num task per cat " + numTaskPerCat);
    }

    /**
     * Teardown.
     *
     * @param benchmarkParams the benchmark params
     * @throws Exception the exception
     */
    @TearDown(Level.Trial)
    public void teardown(BenchmarkParams benchmarkParams) throws Exception {
      executorService.shutdown();
      shutdown = true;
      reopenThread.interrupt();
      threads.stop();
      reopenThread.join();
      mgr.close();
    }
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
    Task task = state.tasks.nextTask("OrHighHigh");
    task.go(state.indexState);
    return task;
  }

  /**
   * search
   *
   * @param state the state
   * @return the object
   * @throws Exception the exception
   */
  @Benchmark
  public Object respell(BenchState state) throws Exception {
    Task task = state.tasks.nextTask("Respell");
    task.go(state.indexState);
    return task;
  }

  /**
   * search
   *
   * @param state the state
   * @return the object
   * @throws Exception the exception
   */
  @Benchmark
  public Object fuzzy1(BenchState state) throws Exception {
    Task task = state.tasks.nextTask("Fuzzy1");
    task.go(state.indexState);
    return task;
  }

  /**
   * search
   *
   * @param state the state
   * @return the object
   * @throws Exception the exception
   */
  @Benchmark
  public Object fuzzy2(BenchState state) throws Exception {
    Task task = state.tasks.nextTask("Fuzzy2");
    task.go(state.indexState);
    return task;
  }

  /**
   * And high med object.
   *
   * @param state the state
   * @return the object
   * @throws Exception the exception
   */
  @Benchmark
  public Object andHighMed(BenchState state) throws Exception {
    Task task = state.tasks.nextTask("AndHighMed");
    task.go(state.indexState);
    return task;
  }

  /**
   * Or high med object.
   *
   * @param state the state
   * @return the object
   * @throws Exception the exception
   */
  @Benchmark
  public Object orHighMed(BenchState state) throws Exception {
    Task task = state.tasks.nextTask("OrHighMed");
    task.go(state.indexState);
    return task;
  }

  /**
   * Prefix 3 object.
   *
   * @param state the state
   * @return the object
   * @throws Exception the exception
   */
  @Benchmark
  public Object prefix3(BenchState state) throws Exception {
    Task task = state.tasks.nextTask("Prefix3");
    task.go(state.indexState);
    return task;
  }

  /**
   * Term object.
   *
   * @param state the state
   * @return the object
   * @throws Exception the exception
   */
  @Benchmark
  public Object term(BenchState state) throws Exception {
    Task task = state.tasks.nextTask("Term");
    task.go(state.indexState);
    return task;
  }
}
