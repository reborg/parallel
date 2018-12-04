(ns lastfm.version00
  (:require [clojure.string :as s])
  (:import [java.io BufferedReader FileReader Reader StringReader File]))

;; #######################
;; ### Files and utils ###
;; #######################

;; Pointing at the original large TSV
(defn plays [] (FileReader. (File. "data/lastfm-dataset-360K/usersha1-artmbid-artname-plays.tsv")))
(defn details [] (FileReader. (File. "data/lastfm-dataset-360K/usersha1-profile.tsv")))
(defn listeners [] (FileReader. (File. "data/lastfm-dataset-1K/userid-timestamp-artid-artname-traid-traname.tsv")))

(def clean-xform
  (comp (map s/trim)
        (remove s/blank?)
        (map #(s/split % #"\t"))))

(defn process
  ([r xcomp] (process r xcomp conj! []))
  ([r xcomp store! init]
   (let [br (BufferedReader. r)
         lines (line-seq br)
         editable? #(instance? clojure.lang.IEditableCollection %)]
     (transduce
       (comp clean-xform xcomp)
       (completing
         store!
         #(do (.close br) (if (editable? init) (persistent! %) %)))
       (if (editable? init) (transient init) init)
       lines))))

(defn load-user-info [fname]
  (process fname identity
    (fn [m [userid :as attrs]]
      (assoc! m userid (subvec attrs 1 (count attrs)))) {}))

; (require '[lastfm.version00 :as v0] :reload)
; (def details (time (v0/load-user-info (v0/details))))
; "Elapsed time: 1467.065929 msecs"

;; #######################
;; ######## API ##########
;; #######################

;; What are the most played artists?
(defn top-artists [f]
  (->> (process f (map #(nth % 2)))
       frequencies
       (sort-by last >)
       (take 5)))

; (time (v0/top-artists (v0/plays)))
; "Elapsed time: 45463.570766 msecs"
; (["radiohead" 77348] ["the beatles" 76339] ["coldplay" 66738]
;  ["red hot chili peppers" 48989] ["muse" 47015])

(defn top-artists-in [f in-country]
  (let [user-info (load-user-info (details))
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

; (time (v0/top-artists-in (v0/plays) "poland"))
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

; (time (v0/how-many-songs-played-for (v0/plays) "coltrane"))
; "Elapsed time: 23843.245219 msecs"
; 1,157,511

(defn most-played-band-by-day
  [fname]
  (let [keyfn (fn [item]
                (let [[_ ts _ band] item]
                  [(nth (s/split ts #"T") 0) band]))
        reducefn (fn [m item]
                   (let [k (keyfn item)]
                     (assoc! m k (inc (get m k 0)))))]
    (->> (process fname identity reducefn {})
         (sort-by #(nth % 1) >)
         (take 5))))

;; (time (v0/most-played-band-by-day (v0/listeners)))
; "Elapsed time: 65574.980722 msecs"
; ([["2009-03-21" "Kanye West"] 2331] [["2009-02-28" "T.I."] 2062]
