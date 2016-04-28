(ns ctia.flows.hooks.redis-event-hook-test
  (:require [clojure.test :refer [deftest is join-fixtures testing use-fixtures]]
            [ctia.lib.redis :as lr]
            [ctia.properties :refer [properties]]
            [ctia.properties.getters :as pg]
            [ctia.test-helpers.core :as test-helpers :refer [post]])
  (:import [java.util.concurrent CountDownLatch TimeUnit]))

(use-fixtures :once test-helpers/fixture-schema-validation)

(use-fixtures :each (join-fixtures [test-helpers/fixture-properties:clean
                                    test-helpers/fixture-properties:redis-store
                                    test-helpers/fixture-ctia
                                    test-helpers/fixture-allow-all-auth]))

(deftest ^:disabled test-events-pubsub
  (testing "Events are published to redis"
    (let [results (atom [])
          finish-signal (CountDownLatch. 3)
          {:keys [timeout-ms channel-name] :as redis-config} (get-in @properties [:ctia :hook :redis])
          [host port] (pg/parse-host-port redis-config)
          listener (lr/subscribe-to-messages (lr/server-connection host port timeout-ms)
                                             channel-name
                                             (fn test-events-pubsub-fn [ev]
                                               (swap! results conj ev)
                                               (.countDown finish-signal)))]
      (let [{judgment-1-id :id
             :as judgement-1}
            (post "ctia/judgement"
                  :body {:observable {:value "1.2.3.4"
                                      :type "ip"}
                         :disposition 1
                         :source "source"
                         :priority 100
                         :severity 100
                         :confidence "Low"
                         :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})

            {judgement-2-id :id
             :as judgement-2}
            (post "ctia/judgement"
                  :body {:observable {:value "1.2.3.4"
                                      :type "ip"}
                         :disposition 2
                         :source "source"
                         :priority 100
                         :severity 100
                         :confidence "Low"
                         :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})

            {judgement-3-id :id
             :as judgement-3}
            (post "ctia/judgement"
                  :body {:observable {:value "1.2.3.4"
                                      :type "ip"}
                         :disposition 3
                         :source "source"
                         :priority 100
                         :severity 100
                         :confidence "Low"
                         :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})])

      (is (.await finish-signal 10 TimeUnit/SECONDS)
          "Unexpected timeout waiting for subscriptions")
      (is (= [{:owner "Unknown"
               :entity {:valid_time
                        {:start_time #inst "2016-02-11T00:40:48.212-00:00",
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"},
                        :observable {:value "1.2.3.4", :type "ip"},
                        :type "judgement",
                        :source "source",
                        :disposition 1,
                        :disposition_name "Clean",
                        :priority 100,
                        :id judgement-1-id
                        :severity 100,
                        :confidence "Low",
                        :owner "Unknown"}
               :id judgement-1-id}
              {:owner "Unknown"
               :entity {:valid_time
                        {:start_time #inst "2016-02-11T00:40:48.212-00:00",
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"},
                        :observable {:value "1.2.3.4", :type "ip"},
                        :type "judgement",
                        :source "source",
                        :disposition 2,
                        :disposition_name "Malicious",
                        :priority 100,
                        :id judgement-2-id
                        :severity 100,
                        :confidence "Low",
                        :owner "Unknown"}
               :id judgement-2-id}
              {:owner "Unknown"
               :entity {:valid_time
                        {:start_time #inst "2016-02-11T00:40:48.212-00:00",
                         :end_time #inst "2525-01-01T00:00:00.000-00:00"},
                        :observable {:value "1.2.3.4", :type "ip"},
                        :type "judgement",
                        :source "source",
                        :disposition 3,
                        :disposition_name "Suspicious",
                        :priority 100,
                        :id judgement-3-id
                        :severity 100,
                        :confidence "Low",
                        :owner "Unknown"}
               :id judgement-3-id}]
             (->> @results
                  (map #(dissoc % :timestamp :http-params))
                  (map #(update % :entity dissoc :created)))))
      (lr/close-listener listener))))
