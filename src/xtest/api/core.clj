(ns xtest.api.core
  (:require
    [reitit.ring :as ring]
    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [xtest.api.user :as user]))

(def app
  (ring/ring-handler
    (ring/router
      [["/api"
        {:middleware [[wrap-json-body {:keywords? true}]
                      wrap-json-response]}
       ["/users" {:post user/create-user}]]])))