(ns adi.data.test-02-process-refs
  (:use midje.sweet
        adi.utils
        adi.checkers)
  (:require [adi.data :as ad]))


(def t0-map
  {:next  [{:type       :ref}]
   :value [{:type       :long}]})

(def t0-data
  {:value 1
   :next {:value 2
          :next {:value 3
                 :next {:value 4}}}})

(fact "t0 - process does not change"
    (ad/process t0-map t0-data)
    => t0-data)


(def t1a-map
  (flatten-keys
   {:link  {:next  [{:type        :ref
                     :ref-ns      :link}]
            :value [{:type        :long}]}}))

(def t1b-map
  (flatten-keys
   {:link  {:next  [{:type        :ref}]
            :value [{:type        :long}]}}))

(def t1-res
  {:link/value 1
      :link/next {:link/value 2
                  :link/next {:link/value 3
                              :link/next {:link/value 4}}}})

(fact "t1 - the ref-ns is expanded"
  (ad/process t1a-map
              {:link {:value 1
                      :next {:value 2
                             :next  {:value 3
                                     :next {:value 4}}}}})
  => t1-res

  (ad/process t1b-map  ;; if ref-ns is not avaliable, the links have to be make explicit
              {:link {:value 1
                      :next {:link {:value 2
                                    :next {:link {:value 3
                                                   :next {:link {:value 4}}}}}}}})
  => t1-res

  (ad/process t1b-map t1-res) ;; or there is no change when using a flatmap
  => t1-res)

(def t2-map
  (flatten-keys
   {:link  {:next  [{:type        :ref}]}
    :value [{:type    :long}]}))

(def t2-data
  {:value 1
   :link {:next {:value 2
                 :link {:next  {:value 3
                                :link {:next {:value 4}}}}}}})

(def t3-map
  (flatten-keys
   {:link  {:next  [{:type        :ref
                     :ref-ns      :link}]}
    :value [{:type   :long}]}))


(fact
  (ad/process t3-map {:value 1})
  => {:value 1}

  (ad/process t3-map
              {:value 1
               :link {:next {:+ {:value 2}}}})
  => {:value 1
      :link/next {:value 2}}

   (ad/process t3-map
              {:+/value 1
               :link/next {:+/value 2}})
  => {:value 1
      :link/next {:value 2}}


  (ad/process t3-map
              {:value 1
               :link {:next {:+ {:value 2}
                             :next {:+ {:value 3}
                                    :next {:+ {:value 4}}}}}})
  => {:value 1
      :link/next {:value 2
                  :link/next {:value 3
                              :link/next {:value 4}}}})



(fact "Different types of data links are allowed"


  (ad/process t2-map t2-data)
  => {:value 1
      :link/next {:value 2
                  :link/next {:value 3
                              :link/next {:value 4}}}}

)