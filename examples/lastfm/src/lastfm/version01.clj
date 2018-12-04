(ns lastfm.version01
  (:require [parallel.core :as p]
            [clojure.string :as s])
  (:import [java.io BufferedReader FileReader Reader StringReader File]))

;; #######################
;; ### Files and utils ###
;; #######################

;; Split original files into segments
(defn plays [] "data/lastfm-dataset-360K/splits")
(defn details [] "data/lastfm-dataset-360K/details")
(defn listeners [] "data/lastfm-dataset-1K/splits")

(def clean-xform
  (comp (map s/trim)
        (remove s/blank?)
        (map #(s/split % #"\t"))))

(defn process
  [fname xcomp]
  (p/process-folder fname (comp clean-xform xcomp)))

(defn load-user-info [fname]
  (p/process-folder
    fname
    (completing
      (fn reducef [m [userid :as attrs]]
        (assoc! m userid (subvec attrs 1 (count attrs))))
      persistent!)
    (fn ([] (transient {})) ([m1 m2] (into m1 m2)))
    clean-xform))

; (require '[lastfm.version01 :as v1] :reload)
; (def details (time (v1/load-user-info (v1/details))))
; "Elapsed time: 683.946281 msecs"

;; #######################
;; ######## API ##########
;; #######################

;; What are the most played artists?
(defn top-artists [folder]
  (->> (process folder (map #(nth % 2)))
       frequencies
       ; Shall we add a “p/”?
       ; p/frequencies
       (sort-by last >)
       (take 5)))

; (require '[lastfm.version01 :as v1] :reload)
; (time (v1/top-artists (v1/plays)))
; "Elapsed time: 17494.570766 msecs"
; "Elapsed time: 9865.58715 msecs"
; (["radiohead" 77348] ["the beatles" 76339] ["coldplay" 66738]

(defn top-artists-in [fname in-country]
  (let [user-info (load-user-info (details))
        for-country (fn [[user-id]]
                      (let [country (some-> (nth (user-info user-id) 2) s/lower-case)
                            regxp (re-pattern in-country)]
                        (re-find regxp (or country ""))))
        xform (comp clean-xform (filter for-country) (map #(nth % 2)))]
    (->> (p/frequencies (File. fname) xform)
         (sort-by #(nth % 1) >)
         (take 5))))

; (time (v1/top-artists-in (v1/plays) "poland"))
; "Elapsed time: 5017.731108 msecs"
; (["metallica" 3869]
;  ["myslovitz" 3778]
;  ["red hot chili peppers" 3610]
;  ["o.s.t.r." 3440]
;  ["system of a down" 3306])

(defn how-many-songs-played-for [f band]
  (p/process-folder f + +
    (comp
      clean-xform
      (filter (fn [[_ _ played]] (re-find (re-pattern band) (or played ""))))
      (map peek)
      (map #(Integer/valueOf %)))))

; (time (v1/how-many-songs-played-for (v1/plays) "coltrane"))
; "Elapsed time: 4307.20904 msecs"
; 1,157,511

(defn most-played-band-by-day
  [fname]
  (let [keyfn (fn [item]
                (let [[_ ts _ band] item]
                  [(nth (s/split ts #"T") 0) band]))]
    (->>
      (p/frequencies (File. fname) clean-xform keyfn)
      (sort-by #(nth % 1) >)
      ; Shall we add a “p/”?
      ; (p/sort #(compare (nth %2 1) (nth %2 1)))
      (take 5))))

; (time (v1/most-played-band-by-day (v1/listeners)))
; "Elapsed time: 16614.461194 msecs"
; ([["2009-03-21" "Kanye West"] 2331] [["2009-02-28" "T.I."] 2062]
