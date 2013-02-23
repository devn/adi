(ns adi.query
  (:use [adi.data :only [iid]]
        adi.utils)
  (:require [datomic.api :as d]))

(defn find-ids [db val]
  (cond (number? val) (find-ids {:db/id val})

        (hash-map? val)
        (->> (d/q (concat '[:find ?e :where]
                          (mapv (fn [pair] (cons '?e pair)) val))
                  db)
             (map first))

        (or (vector? val)
            (set? val))
        (mapcat find-ids val)))

(defn find-entities [db val]
  (map #(d/entity db %) (find-ids db val)))