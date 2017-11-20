(defproject parallel "0.1"
  :description "A library of parallel-enabled Clojure functions"
  :url "https://github.com/reborg/parallel"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :source-paths ["src" "playground" "benchmarks"]
  :profiles {:dev {:dependencies [[criterium  "0.4.4"]]
                   :plugins []}}
  :jvm-opts ["-Xmx2g" "-server"]
  :test-refresh {:watch-dirs ["src" "test"]
                 :refresh-dirs ["src" "test"]})
