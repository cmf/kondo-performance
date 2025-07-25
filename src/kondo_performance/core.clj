(ns kondo-performance.core
  (:require [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clj-kondo.core :as kondo])
  (:import (com.sun.management HotSpotDiagnosticMXBean)
           (java.io File)
           (java.lang.management ManagementFactory)
           (java.util.regex Pattern)))

(defn dump-heap
  "Triggers a heap dump to the specified file path.

  `output-path`: The file path to save the .hprof file to.
  `live?`: If true, dumps only live objects (those reachable by the GC);
           if false, dumps all objects on the heap."
  [output-path live?]
  (try
    (let [server (ManagementFactory/getPlatformMBeanServer)
          mx-bean (ManagementFactory/newPlatformMXBeanProxy
                    server
                    "com.sun.management:type=HotSpotDiagnostic"
                    HotSpotDiagnosticMXBean)]
      (.dumpHeap mx-bean output-path live?)
      (println "Heap dump saved to" output-path))
    (catch Exception e
      (println "Failed to create heap dump:" (.getMessage e)))))

(defn run-test [args]
  (let [metabase (io/file "metabase")
        _ (println "Calculating classpath...")
        response (sh/sh "clojure" "-Spath" :dir metabase)]
    (if (= 0 (:exit response))
      (let [classpath (into []
                            (comp
                              (map #(let [file (io/file %)]
                                      (if (.isAbsolute file)
                                        file
                                        (io/file metabase %))))
                              (map #(.getAbsolutePath ^File %)))
                            (str/split (:out response) (Pattern/compile (Pattern/quote File/pathSeparator))))
            start (System/nanoTime)]
        (sh/sh "rm" "-rf" "metabase/.clj-kondo/.cache")
        (kondo/run! {:lint         classpath
                     :config-dir   (io/file metabase ".clj-kondo")
                     :dependencies true
                     :parallel     true
                     :copy-configs true})
        (let [end (System/nanoTime)
              duration (/ (double (- end start)) 1e9)]
          (println "Primed cache in" (String/format "%.2f" (into-array [duration])) "seconds.")
          (System/gc)
          (let [mb (ManagementFactory/getMemoryMXBean)
                heap (.getHeapMemoryUsage mb)
                used (.getUsed heap)
                committed (.getCommitted heap)
                max (.getMax heap)]
            (println "Heap used:" (/ used 1024.0 1024) "MB")
            (println "Heap committed:" (/ committed 1024.0 1024) "MB")
            (println "Heap max:" (/ max 1024.0 1024) "MB")
            (sh/sh "rm" "metabase-prime.hprof")
            (dump-heap "metabase-prime.hprof" true)))

        (let [src (io/file metabase "src")
              start (System/nanoTime)]
          (run! (fn [file]
                  (let [text (slurp file)]
                    (println (.getAbsolutePath file))
                    (with-in-str text
                      (kondo/run! {:lint       ["-"]
                                   :filename   (.getAbsolutePath file)
                                   :config-dir (io/file metabase ".clj-kondo")}))))
                (eduction
                  (filter #(.isFile %))
                  (filter #(or (str/ends-with? (.getName %) ".clj")
                               (str/ends-with? (.getName %) ".cljc")))
                  (file-seq src)))
          (let [end (System/nanoTime)
                duration (/ (double (- end start)) 1e9)]
            (println "Inspected all files in" (String/format "%.2f" (into-array [duration])) "seconds."))))

      (println "Error getting metabase classpath: " response))))

(comment
  (let [metabase (io/file "metabase")
        src (io/file metabase "src")]
    (into []
          (comp
            (filter #(.isFile %))
            (filter #(or (str/ends-with? (.getName %) ".clj")
                         (str/ends-with? (.getName %) ".cljc"))))
          (file-seq src)))

  (-main))
