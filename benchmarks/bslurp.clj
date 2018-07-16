(ns bslurp)

(require '[criterium.core :refer [bench quick-bench]])
(require '[parallel.core :as p] :reload)
(import '(java.nio ByteBuffer CharBuffer)
        '(java.io File PushbackReader InputStream InputStreamReader FileInputStream))

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
(let [fname "test/words" file (File. fname)] (bench (slurp file))) ; 8.84ms
(let [fname "test/words" file (File. fname)] (bench (p/slurp file))) ; 2.87ms
(let [fname "test/words" file (File. fname)] (binding [p/*mutable* true] (bench (p/slurp file)))) ; 1.40ms
(let [fname "test/words" file (File. fname)] (bench (mslurp file))) ; 18.67ms

;; less lines, 3.1M
(let [fname (File. "/Users/reborg/prj/my/pwc/test/war-and-peace.txt")] (bench (slurp fname))) ; 14.67 ms
(let [fname (File. "/Users/reborg/prj/my/pwc/test/war-and-peace.txt")] (bench (p/slurp fname))) ; 7.67ms
(let [fname (File. "/Users/reborg/prj/my/pwc/test/war-and-peace.txt")] (bench (mslurp fname))) ; 8.67ms

;; small file, no no.
(let [fname (File. "project.clj")] (bench (slurp fname))) ; 35.13 µs
(let [fname (File. "project.clj")] (bench (p/slurp fname))) ; 213.517530 µs
