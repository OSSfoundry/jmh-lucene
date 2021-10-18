---
output: pdf_document
---  
  
  
  
  
#  JMH Profiler Setup {#jmh-profiler-setup }
  
JMH ships with a number of built-in profiler options that have grown in number over time. The profiler system is also pluggable,
allowing for "after-market" profiler implementations to be added on the fly.
  
Many of these profilers, most often the ones that stay in the realm of Java, will work across platforms and architectures and do
so right out of the box. Others may be targeted at a specific OS, though there is a good chance a similar profiler for other OS's
may exist where possible. A couple of very valuable profilers also require additional setup and environment to either work fully
or at all.
  
##  Async-Profiler and Perfasm {#async-profiler-and-perfasm }
  
This guide will cover setting up both the async-profiler and the Perfasm profiler. Currently, we roughly cover two Linux family trees,
but much of the information can be extrapolated or help point in the right direction for other systems.
  
---
  
<table>
<tr><td><b>Path 1: Arch, Manjaro, etc</b></td> <td><b>Path 2: Debian, Ubuntu, etc</b></td>
<tr><td>
<image src="https://user-images.githubusercontent.com/448788/137563725-0195a732-da40-4c8b-a5e8-fd904a43bb79.png"/>
<image src="https://user-images.githubusercontent.com/448788/137563722-665de88f-46a4-4939-88b0-3f96e56989ea.png"/>
<td>
<image src="https://user-images.githubusercontent.com/448788/137563909-6c2d2729-2747-47a0-b2bd-f448a958b5be.png"/>
<image src="https://user-images.githubusercontent.com/448788/137563908-738a7431-88db-47b0-96a4-baaed7e5024b.png"/></td></tr>
</table>
  
If you run `jmh.sh` with the `lprof` argument, it will make an attempt to only list the profilers that it detects will work in your particular environment.
You should do this first to see where you stand.
  
<div style="z-index: 8;  background-color: black; border-style: solid; border-width: 1px; border-radius: 12px; padding-top: 8px;" data-code-wrap="true">
  
![](https://user-images.githubusercontent.com/448788/137610566-883825b7-e66c-4d8b-a6a5-61542bc08d23.png )
  
```Shell
./jmh.sh -lprof
```
</div>
  
In our case, we will start with very **minimal** Arch and Ubuntu clean installations, and so we already know there is _**no chance**_ that async-profiler or Perfasm
are going to run.
  
In fact, first we have to install a few project build requirements before thinking too much about JMH profiler support
  
  
<div style="z-index: 8;  background-color: black; border-style: solid; border-width: 1px; border-radius: 12px; padding-top: 8px;" data-code-wrap="true">
  
![](https://user-images.githubusercontent.com/448788/137610116-eff6d0b7-e862-40fb-af04-452aaf585387.png )
```Shell
sudo pacman -Syu wget jdk-openjdk11
```
</div>
  
  
---
  
Here we give async-profiler a try on Arch anyway and observe the failure indicating that we need to obtain the async-profiler library and
put it in the correct location at a minimum.
  
  
<div style="z-index: 8;  background-color: black; border-style: solid; border-width: 1px; border-radius: 12px; padding-top: 8px;" data-code-wrap="true">
  
![](https://user-images.githubusercontent.com/448788/137607441-f083e1fe-b3e5-4326-a9ca-2109c9cef985.png )
  
```Shell
./jmh.sh BenchMark -prof async
```
<pre>
   <image src="https://user-images.githubusercontent.com/448788/137534191-01c2bc7a-5c1f-42a2-8d66-a5d1a5280db4.png"/>  Profilers failed to initialize, exiting.
  
    Unable to load async-profiler. Ensure asyncProfiler library is on LD_LIBRARY_PATH (Linux)
    DYLD_LIBRARY_PATH (Mac OS), or -Djava.library.path.
  
    Alternatively, point to explicit library location with: '-prof async:libPath={path}'
  
    no asyncProfiler in java.library.path: [/usr/java/packages/lib, /usr/lib64, /lib64, /lib, /usr/lib]
    </pre>
</div>
  
  
  
###  Install async-profiler {#install-async-profiler }
  
<div style="z-index: 8;  background-color: black; border-style: solid; border-width: 1px; border-radius: 12px; padding-top: 8px;" data-code-wrap="true" >
  
![](https://user-images.githubusercontent.com/448788/137610116-eff6d0b7-e862-40fb-af04-452aaf585387.png )
```Shell
wget -c https://github.com/jvm-profiling-tools/async-profiler/releases/download/v2.5/async-profiler-2.5-linux-x64.tar.gz -O - | tar -xz
sudo mkdir -p /usr/java/packages/lib
sudo cp async-profiler-2.5-linux-x64/build/* /usr/java/packages/lib
```
</div>
  
That should work out better, but there is still an issue that will prevent a successful profiling run. async-profiler relies on Linux's perf,
and in any recent Linux kernel, perf is restricted from doing its job without some configuration loosening.
  
The following changes will  persist across restarts, and that is likely how you should leave things.
  
```zsh
sudo sysctl -w kernel.kptr_restrict=0
sudo sysctl -w kernel.perf_event_paranoid=1
```
  
Now we **should** see a bit of success.
  
<div style="z-index: 8;  background-color: black; border-style: solid; border-width: 1px; border-radius: 12px; padding-top: 8px;" data-code-wrap="true">
  
![](https://user-images.githubusercontent.com/448788/137607441-f083e1fe-b3e5-4326-a9ca-2109c9cef985.png )
```Shell
./jmh.sh FuzzyQuery -prof async:output=flamegraph
```
</div>
  
  
But you will also find the following if you look closely at the logs. We do not want the debug symbols stripped from Java for the best experience.
And it also turns out that if we want to use async-profilers alloc option to sample and create flamegraphs for heap usage, the debug symbols
are required.
  
![iconmonstr-server-10-240](https://user-images.githubusercontent.com/448788/137535636-f940af84-0bbf-46fa-a9b2-915e36e4a709.png )
> 
>```[WARN] Install JVM debug symbols to improve profile accuracy```
  
###  Install Java Debug symbols {#install-java-debug-symbols }
  
---
  
<div style="z-index: 8;  background-color: black; border-style: solid; border-width: 1px; border-radius: 12px; padding-top: 8px;" data-code-wrap="true">
  
![](https://user-images.githubusercontent.com/448788/137610566-883825b7-e66c-4d8b-a6a5-61542bc08d23.png )
  
```Shell
sudo apt update
sudo apt upgrade
sudo apt install openjdk-11-dbg
```
</div>
  
---
  
  
On the **Arch** side we will rebuild the Java 11 package, but turn off the option that strips debug symbols. Often, large OS package and Java repositories originated in SVN and can be a bit a of a bear to wrestle with git about for just a fraction
of the repository, we do so GitHub API workaround efficiency.
  
<div style="z-index: 8;  background-color: black; border-style: solid; border-width: 1px; border-radius: 12px; padding-top: 8px;" data-code-wrap="true">
  
![prompt-arch](https://user-images.githubusercontent.com/448788/137610116-eff6d0b7-e862-40fb-af04-452aaf585387.png )
  
```Shell
sudo pacman -Syu dkms base-devel linux-headers dkms git vi jq --needed --noconfirm
  
curl -sL "https://api.github.com/repos/archlinux/svntogit-packages/contents/java11-openjdk/repos/extra-x86_64" \
| jq -r '.[] | .download_url' | xargs -n1 wget
```
</div>
  
Now we need to change that option in PKGBUILD. Choose your favorite editor. (nano, vim, emacs, ne, nvim, tilde etc)
  
  
  
<div style="z-index: 8;  background-color: black; border-style: solid; border-width: 1px; border-radius: 12px; padding-top: 8px;" data-code-wrap="true">
  
![](https://user-images.githubusercontent.com/448788/137610116-eff6d0b7-e862-40fb-af04-452aaf585387.png )
  
```Shell
vi PKGBUILD
```
</div>
  
We insert a single option line:
  
```Diff
arch=('x86_64')
url='https://openjdk.java.net/'
license=('custom')
+   options=('debug' '!strip')
makedepends=('java-environment>=10' 'java-environment<12' 'cpio' 'unzip' 'zip' 'libelf' 'libcups' 'libx11' 'libxrender' 'libxtst' 'libxt' 'libxext' 'libxrandr' 'alsa-lib' 'pandoc'
```
  
And then we build. (`-s: --syncdeps -i: --install  -f: --force`)
  
<div style="z-index: 8;  background-color: black; border-style: solid; border-width: 1px; border-radius: 12px; padding-top: 8px;" data-code-wrap="true">
  
![](https://user-images.githubusercontent.com/448788/137610116-eff6d0b7-e862-40fb-af04-452aaf585387.png )
  
```Shell
makepkg -sif
```
</div>
  
When that is done, if everything went well, we should be able to succesfully run asyncprofiler in alloc mode to generate a flamegreaph based on memory rather than cpu.
  
  
##  Perfasm {#perfasm }
####  ~~HDIS ROUGHER DUMP STIL~~ {#~~hdis-rougher-dump-stil~~ }
  
**hsdis** for assembly output
  
---
  
##  Arch {#arch }
![](https://user-images.githubusercontent.com/448788/137563725-0195a732-da40-4c8b-a5e8-fd904a43bb79.png ) 
  
---
  
  
[//]: # ( https://aur.archlinux.org/packages/java11-openjdk-hsdis/)
  
If you have `yay` or another **AUR** helper available, or if you have the AUR enabled in your package manager, simply install `java11-openjdk-hdis`
  
If you do not have simple access to **AUR**, set it up or just grab the package manually:
  
  
<div style="z-index: 8;  background-color: black; border-style: solid; border-width: 1px; border-radius: 12px; padding-top: 8px;" data-code-wrap="true">
  
![](https://user-images.githubusercontent.com/448788/137610116-eff6d0b7-e862-40fb-af04-452aaf585387.png )
  
```Shell
$ wget -c https://aur.archlinux.org/cgit/aur.git/snapshot/java11-openjdk-hsdis.tar.gz -O - | tar -xz
$ cd java11-openjdk-hsdis/
$ ls
binutils-compat.patch  binutils.patch  PKGBUILD
```
</div>
  
<br/>
  
---
  
##  Ubuntu {#ubuntu }
![](https://user-images.githubusercontent.com/448788/137563908-738a7431-88db-47b0-96a4-baaed7e5024b.png )
  
---
  
<div style="z-index: 8;  background-color: black; border-style: solid; border-width: 1px; border-radius: 12px; padding-top: 8px;" data-code-wrap="true">
  
![](https://user-images.githubusercontent.com/448788/137610566-883825b7-e66c-4d8b-a6a5-61542bc08d23.png )
  
```Shell
sudo apt update
sudo apt -y upgrade
sudo apt -y install openjdk-11-jdk git wget jq
```
</div>
  
<br/>
  
<div style="z-index: 8;  background-color: black; border-style: solid; border-width: 1px; border-radius: 12px; padding-top: 8px;" data-code-wrap="true">
  
![](https://user-images.githubusercontent.com/448788/137610566-883825b7-e66c-4d8b-a6a5-61542bc08d23.png )
  
```Shell
curl -sL "https://api.github.com/repos/openjdk/jdk11/contents/src/utils/hsdis" | jq -r '.[] | .download_url' | xargs -n1 wget
  
# Newer versions of binutils don't appear to compile, must use 2.28 for JDK 11
wget http://ftp.heanet.ie/mirrors/ftp.gnu.org/gnu/binutils/binutils-2.28.tar.gz
tar xzvf binutils-2.28.tar.gz
make BINUTILS=binutils-2.28 ARCH=amd64
```
</div>
  
  