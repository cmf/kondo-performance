# kondo-performance

Simple test to use a large OSS Clojure project (metabase) to test clj-kondo
performance. To use, `git clone https://github.com/metabase/metabase` and then
`clj -X kondo-performance.core/run-test`.

The test deletes the metabase cache and then indexes all the deps. It will print
out the memory use and also create a heap dump, although in some basic testing
the memory numbers line up pretty well with the retained info from the heap
dump.

It will also then find all the `clj` and `cljc` files under `src`, and lint
them. I wrote this test because it felt like that was much slower than it had
been with the earlier version, but in fact it's only slightly slower
(160.90 seconds vs 187.95 seconds).

The output with different versions of kondo is in 
[output-2025.06.05.txt](output-2025.06.05.txt) and 
[output-2025.06.06-20250724.205219-13.txt](output-2025.06.06-20250724.205219-13.txt).
