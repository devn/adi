(ns adi.data
  (:use [datomic.api :only [tempid]]
        [adi.schema :as as]
        adi.utils))

(defn iid
  "Constructs a new id"
  ([] (tempid :db.part/user))
  ([obj]
     (let [v (hash obj)
           v (if (< 0 v) (- v) v)]
       (tempid :db.part/user v ))))

(defn correct-type? [meta v]
  "Checks to see if v matches the description in the meta.
   throws an exception if the v does not"
  (let [t (:type meta)
        c (or (:cardinality meta) :one)
        chk (type-checks t)]
    (cond (and (= c :one) (not (chk v)))
          (throw (Exception. (format "The value %s is not of type %s" v t)))
          (and (= c :many) (not (set v)))
          (throw (Exception. (format "%s needs to be a set" v)))
          (and (= c :many) (not (every? chk v)))
          (throw (Exception. (format "Not every value in %s is not of type %s" v t))))
    true))

(defn trial-keys [k data]
  (let [kns (seperate-keys k)
        trials [[:+ k] (cons :+ kns) [k] kns]]
    (->> trials
         (filter #(get-in data %))
         first)))


(declare process-data
         process-assoc process-ref process-default)

(defn process-data
  "Processes the data according to the schema specified to form a tree-like
   structure of refs and values for the next step of characterisation."
  ([fm data] (process-data fm data true))
  ([fm data defaults?] (process-data fm fm data {} defaults?))
  ([fm nfm data output defaults?]
     (if-let [[k [meta]] (first nfm)]
       (let [tk     (trial-keys k data)
             v      (cond tk (get-in data tk)
                          defaults? (process-default meta k data))
             output (if v
                      (process-assoc fm meta output k v defaults?)
                      output)]
         (process-data fm (rest nfm) data output defaults?))
       (if-let [ks (trial-keys :db/id data)]
         (assoc output :db/id (get-in data ks))
         output))))

(defn process-default [meta k data]
  (let [n  (seperate-keys (key-ns k))
        m (treeify-keys data)]
    (if (get-in m n) (:default meta))))

(defn process-ref [fm meta v defaults?]
  (let [kns   (seperate-keys (:ref-ns meta))
        refv (extend-key-ns v kns [:+])]
    (process-data fm refv defaults?)))

(defn process-assoc [fm meta output k v defaults?]
  (if (correct-type? meta v)
    (let [t (:type meta)
          c (or (:cardinality meta) :one)]
      (cond (not= t :ref)
            (assoc output k v)

            (= c :one)
            (assoc output k (process-ref fm meta v defaults?))

            (= c :many)
            (assoc output k (set (map #(process-ref fm meta % defaults?) v)))))))

(declare deprocess-data
         deprocess-assoc deprocess-ref)


(defn deprocess-data
  "The opposite of process-data. Takes a map and turns it into a nicer looking
   data-structure"
  ([fm pdata] (deprocess-data fm pdata () (as/make-rset fm)))
  ([fm pdata rset]
     (if-let [id (:db/id pdata)]
       (deprocess-data fm pdata rset {:db/id id} #{id})
       (deprocess-data fm pdata rset {} #{})))
  ([fm pdata rset seen-ids]
     (deprocess-data fm pdata rset {} seen-ids))
  ([fm pdata rset output seen-ids]
     (if-let [[k v] (first pdata)]
       (if-let [[meta] (k fm)]
         (deprocess-data fm
                         (next pdata)
                         rset
                         (deprocess-assoc fm rset meta output k v seen-ids)
                         seen-ids)
         (deprocess-data fm (next pdata) rset output seen-ids))
       output)))

(defn deprocess-assoc [fm rset meta output k v seen-ids]
  (if (correct-type? meta v)
    (let [t (:type meta)
          c (or (:cardinality meta) :one)
          kns (seperate-keys k)]
      (cond (not= t :ref)
            (assoc-in output kns v)

            (= c :one)
            (assoc-in output kns (deprocess-ref fm rset meta v seen-ids))

            (= c :many)
            (assoc-in output kns
                      (set (map #(deprocess-ref fm rset meta % seen-ids) v)))))))

(defn deprocess-ref [fm rset meta v seen-ids]
  (let [id (:db/id v)]
      (cond (seen-ids id)
            {:+ {:db/id id}}

            (not (-> meta :type rset))
            (if id {:+ {:db/id id}} {})

            :else
            (let [nks (seperate-keys (:ref-ns meta))
                  nm  (deprocess-data fm v rset)
                  cm  (get-in nm nks)
                  xm  (dissoc-in nm nks)]
              (if (empty? xm) cm
                  (merge cm (assoc {} :+ xm)))))))

(defn characterise
  "Characterises the data into datomic specific format so that converting
   into datomic datastructures become easy."
  ([fm pdata] (characterise fm pdata {}))
  ([fm pdata output]
     (if-let [[k v] (first pdata)]
       (let [t (-> fm k first :type)]
         (cond (= k :db/id)
               (characterise fm (next pdata) (assoc output k v))

               (and (set? v) (= :ref t))
               (characterise fm (next pdata)
                             (assoc-in output
                                       [:refs-many k]
                                       (set (map #(characterise fm %) v))))
               (set? v)
               (characterise fm (next pdata) (assoc-in output [:data-many k] v))

               (= :ref t)
               (characterise fm (next pdata)
                             (assoc-in output
                                       [:refs-one k]
                                       (characterise fm v)))

               :else
               (characterise fm (next pdata) (assoc-in output [:data-one k] v))))
       (if (:db/id output)
         output
         (assoc output :db/id (iid))))))

(declare build
         build-data-one build-data-many
         build-refs-one build-refs-many)

(defn build
  "Builds the datomic query structure from the
  characterised result"
  ([chdata]
    (concat (build chdata build-data-one build-data-many)
            (build chdata build-refs-one build-refs-many)))
  ([chdata f1 f2]
    (cond (nil? (seq chdata)) []
        :else
        (concat (mapcat (fn [x] (build (second x) f1 f2)) (:refs-one chdata))
                (mapcat (fn [x]
                          (mapcat #(build % f1 f2) (second x))) (:refs-many chdata))
                (f1 chdata)
                (f2 chdata)))))

;; Build Helper Functions

(defn- build-data-one [chd]
  [(assoc (:data-one chd) :db/id (:db/id chd))])

(defn- build-data-many [chd]
  (for [[k vs] (:data-many chd)
        v vs]
    [:db/add (:db/id chd) k v]))

(defn- build-refs-one [chd]
  (for [[k ref] (:refs-one chd)]
    [:db/add (:db/id chd) k (:db/id ref)]))

(defn- build-refs-many [chd]
  (for [[k refs] (:refs-many chd)
        ref refs]
    [:db/add (:db/id chd) k (:db/id ref)]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;  GENERATE-DATA FUNCTIONS
;;
;;
;;              *---------------------------------------*
;;  fm, data -> | process-data -> characterize -> build | -> datomic structures
;;              *---------------------------------------*
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn generate-data
  "Generates the datomic data given a datamap and refs"
  ([fm data]
     (->> (process-data fm data)
          (characterise fm)
          build))
  ([fm data & more]
     (concat (generate-data fm data) (apply generate-data fm more))))











;; Scratch




(comment
  (defn deprocess-data
    "The opposite of process-data. Takes a map and turns it into a nicer looking
   data-structure"
    ([fm pdata] (deprocess-data fm pdata {}))
    ([fm pdata output]
       (if-let [[k v] (first pdata)]
         (if-let [[meta] (k fm)]
           (deprocess-data fm (next pdata) (deprocess-assoc fm meta output k v))
           (deprocess-data fm (next pdata) output))
         output)))

  (defn deprocess-assoc [fm meta output k v]
    (if (correct-type? meta v)
      (let [t (:type meta)
            c (or (:cardinality meta) :one)
            kns (seperate-keys k)]
        (cond (not= t :ref)
              (assoc-in output kns v)

              (= c :one)
              (assoc-in output kns (deprocess-ref fm meta v))

              (= c :many)
              (assoc-in output kns (set (map #(deprocess-ref fm meta %) v)))))))

  (defn deprocess-ref [fm meta v]
    (let [nks (seperate-keys (:ref-ns meta))
          nm  (deprocess-data fm v)
          cm  (get-in nm nks)
          xm  (dissoc-in nm nks)]
      (if (empty? xm) cm
          (merge cm (assoc {} :+ xm)) ))))


(comment


(defn deprocess-data
  "The opposite of process-data. Takes a map and turns it into a nicer looking
   data-structure"
  ([fm pdata]
     (if-let [id (:db/id pdata)]
       (deprocess-data fm pdata {:db/id id} #{id})
       (deprocess-data fm pdata {} #{})))
  ([fm pdata seen-ids]
     (deprocess-data fm pdata {} seen-ids))
  ([fm pdata output seen-ids]
     (if-let [[k v] (first pdata)]
       (if-let [[meta] (k fm)]
         (deprocess-data fm
                         (next pdata)
                         (deprocess-assoc fm meta output k v seen-ids)
                         seen-ids)
         (deprocess-data fm (next pdata) output seen-ids))
       output)))

(defn deprocess-assoc [fm meta output k v seen-ids]
  (if (correct-type? meta v)
    (let [t (:type meta)
          c (or (:cardinality meta) :one)
          kns (seperate-keys k)]
      (cond (not= t :ref)
            (assoc-in output kns v)

            (= c :one)
            (assoc-in output kns (deprocess-ref fm meta v seen-ids))

            (= c :many)
            (assoc-in output kns (set (map #(deprocess-ref fm meta % seen-ids) v)))))))

(defn deprocess-ref [fm meta v seen-ids]
  (if (seen-ids (:db/id v))
    (assoc-in {} [:+ :db/id] (:db/id v))
    (let [nks (seperate-keys (:ref-ns meta))
          nm  (deprocess-data fm v)
          cm  (get-in nm nks)
          xm  (dissoc-in nm nks)]
      (if (empty? xm) cm
          (merge cm (assoc {} :+ xm))))))
)