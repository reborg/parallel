(ns lastfm.version01
  (:require [parallel.core :as p]
            [clojure.string :as s])
  (:import [java.io BufferedReader FileReader Reader StringReader File]))

;; #######################
;; ### Files and utils ###
;; #######################

;;
;; split -l 10000 -a 3 usersha1-artmbid-artname-plays.tsv segment-
;; (into [] (rest (file-seq (java.io.File. "data/lastfm-dataset-360K/splits"))))
;; 1756 files.

(defn users-splits [] "data/lastfm-dataset-360K/splits")
(defn users-details [] "data/lastfm-dataset-360K/usersha1-profile.tsv")

(defn listeners-small [] "data/lastfm-dataset-1K/small.tsv")
(defn listeners-full [] "data/lastfm-dataset-1K/userid-timestamp-artid-artname-traid-traname.tsv")

(defn process [folder xcomp]
  (p/process-folder
    folder
    (comp (map s/trim)
          (remove s/blank?)
          (map #(s/split % #"\t"))
          xcomp)))

(defn- load-user-info
  "Loads users personal details into a map
  of user ids into user details."
  [f]
  (process f identity
    (fn [m [userid :as attrs]]
      (assoc! m userid (subvec attrs 1 (count attrs)))) {}))

;; #######################
;; ######## API ##########
;; #######################

(defn top-artists [folder]
  (->> (process folder (map #(nth % 2)))
       frequencies
       (sort-by last >)
       (take 5)))

; (require '[lastfm.version01 :as v1] :reload)
; (time (v1/top-artists (v1/users-splits)))
; "Elapsed time: 17.570766 msecs"
; (["radiohead" 77348] ["the beatles" 76339] ["coldplay" 66738]
;  ["red hot chili peppers" 48989] ["muse" 47015])

(defn top-artists-in [f in-country]
  (let [user-info (load-user-info (users-details))
        for-country (fn [[user-id]]
                      (let [country (some-> (nth (user-info user-id) 2) s/lower-case)
                            regxp (re-pattern in-country)]
                        (re-find regxp (or country ""))))]
    (->> (process f
           (comp
             (filter for-country)
             (map #(nth % 2))))
         frequencies
         (sort-by last >)
         (take 5))))

; (time (plain/top-artists (plain/users-full) "poland"))
; "Elapsed time: 37677.731108 msecs"
; (["metallica" 3869]
;  ["myslovitz" 3778]
;  ["red hot chili peppers" 3610]
;  ["o.s.t.r." 3440]
;  ["system of a down" 3306])

(defn how-many-songs-played-for [f band]
  (process f
    (comp
      (filter
        (fn [[_ _ played]]
          (re-find (re-pattern band) (or played ""))))
      (map peek)
      (map #(Integer/valueOf %)))
    + 0))

; (time (plain/how-many-songs-played-for (plain/users-full) "coltrane"))
; "Elapsed time: 23843.245219 msecs"
; 1,157,511

(defn most-played-band-by-day
  "Most played songs would be also interesting, but there's not
  enough mem for that."
  [f]
  (->>
    (process f identity
             (fn [m [_ ts _ band]]
               (let [k [(nth (s/split ts #"T") 0) band]]
                 (assoc! m k (inc (get m k 0))))) {})
    (sort-by last >)
    (take 5)))

;; (time (plain/most-played-band-by-day (plain/listeners-full)))
; "Elapsed time: 65574.980722 msecs"
; ([["2009-03-21" "Kanye West"] 2331] [["2009-02-28" "T.I."] 2062] [["2008-12-27" "Kanye West"] 1828] [["2009-02-14" "Kanye West"] 1606] [["2009-02-07" "Kanye West"] 1583])

;; Example Step1: plain approach.
;; Althoug this is a "plain" approach, there's a lot of Clojure knowledge implied and specific design choices to avoid out of memory. For example:

; 1. Specific care has been taken to avoid loading the entire file in mem.
; 2. Transducers have been used when possible.
; 3. Note the use of transients
; 4. Design of functions so that aggregations of data happen while processing the file.
; 5. We couldn't use "frequencies" in the last case, although that would be an easy choice.
; 6. Any group-by or sorting needs to leave outside the transducing context.
; 7. "nth" instead of "first, last etc."
; 8. A most-played-song-by-day, although desiderable, can't be achieved without a super large heap.
