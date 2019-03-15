(defproject parallel "0.11"
  :description "A library of parallel-enabled Clojure functions"
  :url "https://github.com/reborg/parallel"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]]
  :java-source-paths ["java"]
  :uberjar-name "parallel.jar"
  :deploy-repositories [["releases" :clojars] ["snapshots" :clojars]]
  :profiles {:dev {:dependencies [[criterium  "0.4.4"]
                                  [com.clojure-goes-fast/clj-java-decompiler "0.1.0"]]
                   :plugins []}}
  :jvm-opts ["-Xmx2g" "-server"]
  :test-refresh {:watch-dirs ["src" "test"] :refresh-dirs ["src" "test"]})
