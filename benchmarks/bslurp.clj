(ns bslurp)

(require '[criterium.core :refer [bench quick-bench]])
(require '[parallel.core :as p] :reload)
(import '(java.nio ByteBuffer CharBuffer)
        '(java.io PushbackReader InputStream InputStreamReader FileInputStream))

(set! *warn-on-reflection* true)

(def READ_ONLY ^{:private true}
  (java.nio.channels.FileChannel$MapMode/READ_ONLY))

(defn mmap [^String f]
  (let [channel (.getChannel (FileInputStream. f))]
    (.map channel READ_ONLY 0 (.size channel))))

(defn mslurp
  "Including memory mapping for benchmarks."
  [^String f]
  (.. java.nio.charset.Charset (forName "UTF-8")
      (newDecoder) (decode (mmap f))))

;; lot of lines, 2.4M
(let [fname "test/words"] (bench (slurp fname))) ; 8.84ms
(let [fname "test/words"] (bench (p/slurp fname))) ; 3.27ms
(let [fname "test/words"] (bench (p/slurp fname))) ; 3.27ms
(let [fname "test/words"] (binding [p/*mutable* true] (bench (p/slurp fname)))) ; 1.40ms
(let [fname "test/words"] (bench (mslurp fname))) ; 18.67ms

;; less lines, 3.1M
(let [fname "/Users/reborg/prj/my/pwc/test/war-and-peace.txt"] (bench (slurp fname))) ; 14.67 ms
(let [fname "/Users/reborg/prj/my/pwc/test/war-and-peace.txt"] (bench (p/slurp fname))) ; 8.67ms
(let [fname "/Users/reborg/prj/my/pwc/test/war-and-peace.txt"] (bench (mslurp fname))) ; 8.67ms

;; small file
(let [fname "project.clj"] (bench (slurp fname))) ; 35.13 µs
(let [fname "project.clj"] (bench (p/slurp fname))) ; 213.517530 µs
