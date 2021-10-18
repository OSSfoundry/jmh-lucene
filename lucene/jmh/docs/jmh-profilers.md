<!--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 -->

# JMH Profilers

- [JMH Profilers](#jmh-profilers)
  - [Introduction](#introduction)
  - [Using JMH Profilers](#using-jmh-profilers)
    - [Using JMH with the Async-Profiler](#using-jmh-with-the-async-profiler)
      - [OS Permissions for Async-Profiler](#os-permissions-for-async-profiler)
    - [Using JMH with the GC Profiler](#using-jmh-with-the-gc-profiler)
    - [Using JMH with the Java Flight Recorder Profiler](#using-jmh-with-the-java-flight-recorder-profiler)

## Introduction

Some may think that the appeal of a micro-benchmark is in the relative ease one
can be put together. The often isolated nature of what is being measured. But
this perspective is actually what can often make them dangerous. Benchmarking
can be easy to approach from a non-rigorous, casual angle that results in the
feeling that they are a relatively straightforward part of the developer's
purview. From this viewpoint, microbenchmarks can appear downright easy. But good
benchmarking is hard. Microbenchmarks are very hard. Java and HotSpot make hard
even harder.

JMH was developed by engineers that understood the dark side of benchmarks very
well. They also work on OpenJDK, so they are abnormally suited to building a
java microbenchmark framework that tackles many common issues that naive
approaches and go-it-alone efforts are likely to trip on. Even still, they will
tell you, JMH is a sharp blade. Best to be cautious and careful when swinging it
around.

The good folks working on JMH did not just build a better than average java
micro-benchmark framework and then leave us to the still many wolves, though. They
also built-in first-class support for the essential tools that the
ambitious developer absolutely needs for defense when bravely trying to
understand performance. This brings us to the JMH profiler options.

## Using JMH Profilers

### Using JMH with the Async-Profiler

It's good practice to check profiler output for micro-benchmarks in order to
verify that they represent the expected application behavior and measure what
you expect to measure. Some example pitfalls include the use of expensive mocks
or accidental inclusion of test setup code in the benchmarked code. JMH includes
[async-profiler](https://github.com/jvm-profiling-tools/async-profiler)
integration that makes this easy:

> ![](https://user-images.githubusercontent.com/448788/137607441-f083e1fe-b3e5-4326-a9ca-2109c9cef985.png)
>
> ![](https://user-images.githubusercontent.com/448788/137502664-30189dd2-326a-488d-90e3-b2c5f7c75994.png) `./jmh.sh -prof async:libPath=/path/to/libasyncProfiler.so\;dir=profile-results`

Run a specific test with async and GC profilers on Linux and flame graph output.

> ![](https://user-images.githubusercontent.com/448788/137607441-f083e1fe-b3e5-4326-a9ca-2109c9cef985.png)
> ![prompt](https://user-images.githubusercontent.com/448788/137502664-30189dd2-326a-488d-90e3-b2c5f7c75994.png) `./jmh.sh -prof gc -prof async:libPath=/path/to/libasyncProfiler.so\;output=flamegraph\;dir=profile-results BenchmarkClass`

With flame graph output:

> ![](https://user-images.githubusercontent.com/448788/137498403-517d15ca-0c14-4fcc-82d9-06fba392306c.png)
>
>```zsh
> $ ./jmh.sh -prof async:libPath=/path/to/libasyncProfiler.so\;output=flamegraph\;dir=profile-results
>```

Simultaneous CPU, allocation, and lock profiling with async profiler 2.0 and Java Flight Recorder
output:

> ![](https://user-images.githubusercontent.com/448788/137498403-517d15ca-0c14-4fcc-82d9-06fba392306c.png)
>
>```zsh
> $ ./jmh.sh -prof async:libPath=/path/to/libasyncProfiler.so\;output=jfr\;alloc\;lock\;dir=profile-results BenchmarkClass
>```

A number of arguments can be passed to configure async profiler, run the
following for a description:

> ![](https://user-images.githubusercontent.com/448788/137498403-517d15ca-0c14-4fcc-82d9-06fba392306c.png)
>
>```zsh
> $ ./jmh.sh -prof async:help
>```

You can also skip specifying libPath if you place the async profiler lib in a
predefined location, such as one of the locations in the env
variable `LD_LIBRARY_PATH` if it has been set (many Linux distributions set this
env variable, Arch by default does not), or `/usr/lib` should work.

#### OS Permissions for Async-Profiler

Async Profiler uses perf to profile native code in addition to Java code. It
will need the following for the necessary access.

> ![](https://user-images.githubusercontent.com/448788/137498403-517d15ca-0c14-4fcc-82d9-06fba392306c.png)  
>
>```zsh
> $ :hash: echo 0 > /proc/sys/kernel/kptr_restrict
> $ :hash: echo 1 > /proc/sys/kernel/perf_event_paranoid
>```
>
or
> ![](https://user-images.githubusercontent.com/448788/137498403-517d15ca-0c14-4fcc-82d9-06fba392306c.png)
>
>```zsh
> $ sudo sysctl -w kernel.kptr_restrict=0
> $ sudo sysctl -w kernel.perf_event_paranoid=1
>```

### Using JMH with the GC Profiler

You can run a benchmark with `-prof gc` to measure its allocation rate:

> ![](https://user-images.githubusercontent.com/448788/137498403-517d15ca-0c14-4fcc-82d9-06fba392306c.png)
>
>```zsh
> $ ./jmh.sh -prof gc:dir=profile-results
>```

Of particular importance is the `norm` alloc rates, which measure the
allocations per operation rather than allocations per second.

### Using JMH with the Java Flight Recorder Profiler

JMH comes with a variety of built-in profilers. Here is an example of using JFR:

> ![](https://user-images.githubusercontent.com/448788/137498403-517d15ca-0c14-4fcc-82d9-06fba392306c.png)
>
>```zsh
> $ ./jmh.sh -prof jfr:dir=profile-results\;configName=jfr-profile.jfc BenchmarkClass
> ```

In this example, we point to the included configuration file with config name, but
you could also do something like settings=default or settings=profile.
