(ns xtest.api.core
  (:require
    [reitit.ring :as ring]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [xtest.api.user :as user]))

(def app
  (ring/ring-handler
    (ring/router
      [["/users/create" {:post user/create-user}]
       ["/users/get"    {:get  user/get-user-by-email}]]
      {:data {:middleware [wrap-params
                           [wrap-json-body {:keywords? true}]
                           wrap-json-response]}})
    (ring/create-default-handler)))