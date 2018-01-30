(defproject parallel "0.1"
  :description "A library of parallel-enabled Clojure functions"
  :url "https://github.com/reborg/parallel"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]]
  ; :source-paths ["src" "playground" "benchmarks"]
  :source-paths ["src" "benchmarks"]
  :java-source-paths ["java"]
  :uberjar-name "parallel.jar"
  :profiles {:dev {:dependencies [[criterium  "0.4.4"]] :plugins []}}
  :deploy-repositories [["releases"  {:sign-releases false :url "https://clojars.org"}]
                        ["snapshots" {:sign-releases false :url "https://clojars.org"}]]
  :jvm-opts ["-Xmx2g" "-server"]
  :test-refresh {:watch-dirs ["src" "test"] :refresh-dirs ["src" "test"]})
