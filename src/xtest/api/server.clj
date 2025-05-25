(ns xtest.api.server
  (:require
    [ring.adapter.jetty :refer [run-jetty]]
    [xtest.api.core :refer [app]]
    [xtest.api.db :as db])
  (:gen-class))

(defn -main
  "Entry point for running the REST API server."
  [& _]
  (db/init-db!)
  (run-jetty app {:port 3100 :join? false})
  (println "API server running on port 3100"))