(ns ctia.http.middleware.cache-control
  (:require [ctia.lib.time :refer [format-rfc822-time]]
            [pandect.algo.sha1 :refer [sha1]])
  (:import java.io.File))

(defn- read-request? [request]
  (#{:get :head} (:request-method request)))

(defn- ok-response? [response]
  (= (:status response) 200))

(defn calculate-etag [body]
  (case (class body)
    String (sha1 body)
    File (str (.lastModified body) "-" (.length body))
    (sha1 (.getBytes (pr-str body) "UTF-8"))))

(defn update-headers [headers etag body]
  (merge headers
         {"ETag" etag}
         (when-let [last-modified (or (:updated body)
                                      (:created body))]
           {"Last-Modified" (format-rfc822-time last-modified)})))

(defn wrap-cache-control-headers [handler]
  (fn [req]
    (let [{body :body :as resp} (handler req)
          etag (calculate-etag body)]

      (if (and (read-request? req)
               (ok-response? resp))
        (update resp :headers #(update-headers % etag body))
        resp))))
