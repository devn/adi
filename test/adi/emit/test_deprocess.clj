(ns adi.emit.test-deprocess
  (:use midje.sweet
        adi.schema
        adi.data.samples
        adi.emit.deprocess))

(fact "initial lookup"
  (-> d1-env :schema :lu)
  => {:node/parent :node/parent
      :node/value :node/value
      :leaf/node :leaf/node
      :leaf/value :leaf/value
      :node/children :node/children
      :node/_parent :node/children
      :node/leaves :node/leaves
      :leaf/_node :node/leaves})

(fact "deprocess-init-env"
  (deprocess-init-env {})
  => {}

  (deprocess-init-env {:view #{:node/value}})
  => {:view {:node/value :show}}

  (deprocess-init-env {:view {:node/value :show}})
  => {:view {:node/value :show}})

(fact "deprocess-assoc-data"
  (deprocess-assoc-data {} :node/value "hello" d1-env)
  => {}

  (deprocess-assoc-data {} :node/value "hello"
                        (assoc d1-env :view {:node/value :show}))
  => {:node/value "hello"}

  (deprocess-assoc-data {} :node/value "hello"
                        (assoc-in d1-env [:deprocess :data-default] :show))
  => {:node/value "hello"})

(fact "deprocess-ref"
  (deprocess-ref :node/parent {:node/value "hello"}
                 (-> d1-fgeni :node/parent first)
                 (assoc d1-env :view {:node/value :show})
                 #{})
  => nil

  (deprocess-ref :node/parent {:node/value "hello"}
                 (-> d1-fgeni :node/parent first)
                 (assoc d1-env :view {:node/parent :show})
                 #{})
  => {}

  (deprocess-ref :node/parent {:db/id 1 :node/value "hello"}
                 (-> d1-fgeni :node/parent first)
                 (assoc d1-env :view {:node/parent :show
                                      :node/value :show})
                 #{})
  => {:+ {:db {:id 1}}, :value "hello"}

  (deprocess-ref :node/parent {:db/id 1 :node/value "hello"}
                 (-> d1-fgeni :node/parent first)
                 (assoc d1-env :view {:node/parent :ids
                                      :node/value :show})
                 #{})
  => {:+ {:db {:id 1}}}

  (deprocess-ref :node/parent {:db/id 1 :node/value "hello"}
                 (-> d1-fgeni :node/parent first)
                 (assoc d1-env :view {:node/parent :show
                                      :node/value :show})
                 #{1})
  => {:+ {:db {:id 1}}})

(fact "deprocess-assoc-ref"
  (deprocess-assoc-ref {} :node/parent {:node/value "hello"}
                 (-> d1-fgeni :node/parent first)
                 (assoc d1-env :view {:node/parent :show
                                      :node/value :show})
                 #{})
  => {:node/parent {:value "hello"}}

  (deprocess-assoc-ref {} :node/children #{{:node/value "hello"}}
                 (-> d1-fgeni :node/children first)
                 (assoc d1-env :view {:node/children :show
                                      :node/value :show})
                 #{})
  => {:node/children #{{:value "hello"}}})

(fact "deprocess-assoc"
  (deprocess-assoc {} :node/value "hello"
                   (-> d1-fgeni :node/value first)
                   d1-env
                   #{})
  => {}

  (deprocess-assoc {} :node/value "hello"
                   (-> d1-fgeni :node/value first)
                   (assoc d1-env :view {:node/value :show})
                   #{})
  => {:node/value "hello"}

  (deprocess-assoc {} :node/value "hello"
                   (-> d1-fgeni :node/value first)
                   (assoc-in d1-env [:deprocess :data-default] :show)
                    #{})
  => {:node/value "hello"})

(fact "deprocess-fm"
  (deprocess-fm {:db/id 1} d1-env #{})
  => {}

  (deprocess-fm {} {:db/id 1} d1-env #{})
  => {}

  (deprocess-fm {:node/value "hello"}
                d1-env #{})
  => {}

  (deprocess-fm {:node/value "hello"}
                (assoc d1-env :view {:node/value :show}) #{})
  => {:node/value "hello"}

  (deprocess-fm {:db/id 0 :node/parent {:db/id 1 :node/value "hello"}}
                (assoc d1-env :view {:node/value :show
                                     :node/parent :show}) #{})
  => {:node/parent {:+ {:db {:id 1}}, :value "hello"}}

  (deprocess-fm {:db/id 0 :node/parent {:db/id 1 :node/value "hello"}}
                (assoc d1-env :view {:node/value :show
                                     :node/parent :show}) #{1})
  => {:node/parent {:+ {:db {:id 1}}}}

  (deprocess-fm {:db/id 0 :node/parent {:db/id 1 :node/value "hello"}}
                (assoc d1-env :view {:node/value :show
                                     :node/parent :show}) #{0 1})
  => {:node/parent {:+ {:db {:id 1}}}})

(fact "deprocess-view"
  (deprocess-view {:node/value "hello"}
                  (assoc d1-env :view {:node/value :show
                                       :node/parent :show})
                  #{}
                  {:node/value :show
                   :node/parent :show})
  => {:node/value "hello"}

  (deprocess-view {:node/value "hello"}
                  (assoc d1-env :view {:node/value :show
                                       :node/parent :show})
                  #{}
                  {:node/value :show
                   :node/parent :show})
  => {:node/value "hello"}

  (deprocess-view {:db/id 0
                   :node/value "root"
                   :node/children #{{:db/id 1 :node/value "L1"}
                                    {:db/id 2 :node/value "L2"}}}
                  (assoc d1-env :deprocess {:data-default :show}
                         :view {:node/children :show})
                  #{}
                  {:node/children :show})
  {:node/children #{{:+ {:db {:id 1}}, :value "L1"}
                    {:+ {:db {:id 2}}, :value "L2"}}}
  )

(fact "deprocess"
  (deprocess {:node/value "hello"}
             (assoc d1-env :deprocess {:data-default :show
                                       :refs-default :ids}))
  => {:node {:value "hello"}}

  (deprocess {:node/parent {:node/value "hello"}}
             (assoc d1-env :deprocess {:data-default :show
                                       :refs-default :ids}
                    :view #{:node/parent}))
  => {:node {:parent {:value "hello"}}}


  (deprocess {:db/id 0 :node/parent {:db/id 1 :node/value "hello"}}
             (assoc d1-env :deprocess {:data-default :show
                                       :refs-default :ids}
                    :view #{:node/parent}))
  => {:db {:id 0}, :node {:parent {:+ {:db {:id 1}}, :value "hello"}}}

  (deprocess {:node/children #{{:node/value "hello"}}}
             (assoc d1-env :deprocess {:data-default :show
                                       :refs-default :ids}
                    :view #{:node/children}))
  => {:node {:children #{{:value "hello"}}}}

  (deprocess {:db/id 0
              :node/value "root"
              :node/_parent #{{:db/id 1 :node/value "hello1"}
                              {:db/id 2 :node/value "hello2"}}}
             (assoc d1-env :deprocess {:data-default :show}
                    :view #{:node/children}))
  => {:db {:id 0},
      :node {:value "root",
             :children #{{:+ {:db {:id 1}}
                          :value "hello1"}
                         {:+ {:db {:id 2}}
                          :value "hello2"}}}}

  (deprocess {:db/id 0
              :node/value "root"
              :node/children #{{:db/id 1
                                :node/value "L1"
                                :node/children #{{:db/id 11
                                                  :node/value "L11"}
                                                 {:db/id 12
                                                  :node/value "L12"}}}
                               {:db/id 2
                                :node/value "L2"
                                :node/children #{{:db/id 21
                                                  :node/value "L21"}
                                                 {:db/id 22
                                                  :node/value "L22"}}}}}
             (assoc d1-env :deprocess {:data-default :show}
                    :view #{:node/children}))
  => {:db {:id 0}
      :node {:value "root"
             :children #{{:+ {:db {:id 1}}
                          :value "L1"
                          :children #{{:+ {:db {:id 11}}, :value "L11"}
                                      {:+ {:db {:id 12}}, :value "L12"}}}
                         {:+ {:db {:id 2}}
                          :value "L2"
                          :children #{{:+ {:db {:id 21}}, :value "L21"}
                                      {:+ {:db {:id 22}}, :value "L22"}}}}}})
