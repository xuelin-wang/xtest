(ns xtest.api.core
  (:require
    [reitit.ring :as ring]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [clojure.string :as str]
    [clojure.data.codec.base64 :as base64]
    [next.jdbc :as jdbc]
    [xtest.api.user :as user]
    [xtest.api.project :as project]
    [xtest.api.case :as case]
    [xtest.api.db :as db])
  (:import (com.password4j Password)))

(defn- has-any-users?
  "Check if there are any users in the database"
  []
  (try
    (let [db-spec {:jdbcUrl (or (System/getenv "XTDB_JDBC_URL")
                                "jdbc:postgresql://localhost:5432/xtdb")}
          ds (jdbc/get-datasource db-spec)
          result (jdbc/execute! ds ["SELECT COUNT(*) as count FROM user LIMIT 1"])]
      (> (:count (first result)) 0))
    (catch Exception _
      false)))

(defn- parse-basic-auth
  "Parse Basic Auth header and return [email password] or nil if invalid"
  [auth-header]
  (when (and auth-header (str/starts-with? auth-header "Basic "))
    (try
      (let [encoded (subs auth-header 6)
            decoded (String. (base64/decode (.getBytes encoded)) "UTF-8")
            [email password] (str/split decoded #":" 2)]
        (when (and email password)
          [email password]))
      (catch Exception _
        nil))))

(defn- authenticate-user
  "Authenticate user with email and password, return user if valid"
  [email password]
  (when-let [user (db/get-user-by-email email)]
    (when (-> (Password/check password (:password user))
              .withArgon2)
      user)))

(defn wrap-basic-auth
  "Middleware to require HTTP Basic Auth for all requests"
  [handler]
  (fn [request]
    ;; If no users exist yet, allow all requests through
    (if-not (has-any-users?)
      (handler request)
      ;; Otherwise, require authentication
      (let [auth-header (get-in request [:headers "authorization"])]
        (if-let [[email password] (parse-basic-auth auth-header)]
          (if-let [authenticated-user (authenticate-user email password)]
            (handler (assoc request :authenticated-user authenticated-user))
            {:status 401
             :headers {"WWW-Authenticate" "Basic realm=\"API\""
                       "Content-Type" "application/json"}
             :body "{\"error\":\"Invalid credentials\"}"})
          {:status 401
           :headers {"WWW-Authenticate" "Basic realm=\"API\""
                     "Content-Type" "application/json"}
           :body "{\"error\":\"Missing or invalid Authorization header\"}"})))))

(def app
  (ring/ring-handler
    (ring/router
      [
       ["/index.html" {:get {:handler (fn [_]
                                        (let [resource (clojure.java.io/resource "public/index.html")]
                                          (if resource
                                            {:status 200
                                             :headers {"Content-Type" "text/html"
                                                       "Content-Security-Policy" "default-src 'self' 'unsafe-inline' 'unsafe-eval'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https: http:"}
                                             :body (slurp resource)}
                                            {:status 404 :body "Not found"})))}}]

       ["/helixDemo.html" {:get {:handler (fn [_]
                                            (let [resource (clojure.java.io/resource "public/helixDemo.html")]
                                              (if resource
                                                {:status 200
                                                 :headers {"Content-Type" "text/html"
                                                           "Content-Security-Policy" "default-src 'self' 'unsafe-inline' 'unsafe-eval'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https: http:"}
                                                 :body (slurp resource)}
                                                {:status 404 :body "Not found"})))}}]

       ["/main.css" {:get {:handler (fn [_]
                                      (let [resource (clojure.java.io/resource "public/main.css")]
                                        (if resource
                                          {:status 200
                                           :headers {"Content-Type" "text/css"}
                                           :body (slurp resource)}
                                          {:status 404 :body "Not found"})))}}]

       ["/js/*" {:get {:handler (fn [request]
                                  (let [uri (:uri request)
                                        resource-path (str "public" uri)
                                        resource (clojure.java.io/resource resource-path)]
                                    (println "JS request - URI:" uri "Resource path:" resource-path "Resource found:" resource)
                                    (if resource
                                      {:status 200
                                       :headers {"Content-Type" "application/javascript"}
                                       :body (slurp resource)}
                                      {:status 404 :body "Not found"})))}}]       
       
       ["/users/create" {:post user/create-user
                         :middleware [wrap-basic-auth
                                     wrap-params
                                     [wrap-json-body {:keywords? true}]
                                     wrap-json-response]}]
       ["/users/get"    {:get  user/get-users
                         :middleware [wrap-basic-auth
                                     wrap-params
                                     [wrap-json-body {:keywords? true}]
                                     wrap-json-response]}]
       ["/users/update" {:post user/update-user
                         :middleware [wrap-basic-auth
                                     wrap-params
                                     [wrap-json-body {:keywords? true}]
                                     wrap-json-response]}]
       ["/users/delete" {:post user/delete-user
                         :middleware [wrap-basic-auth
                                     wrap-params
                                     [wrap-json-body {:keywords? true}]
                                     wrap-json-response]}]
       ["/users/login"  {:post user/login
                         :middleware [wrap-params
                                     [wrap-json-body {:keywords? true}]
                                     wrap-json-response]}]
       ["/projects/create" {:post project/create-project
                           :middleware [wrap-basic-auth
                                       wrap-params
                                       [wrap-json-body {:keywords? true}]
                                       wrap-json-response]}]
       ["/projects/get"    {:get  project/get-projects
                           :middleware [wrap-basic-auth
                                       wrap-params
                                       [wrap-json-body {:keywords? true}]
                                       wrap-json-response]}]
       ["/projects/delete" {:post project/delete-project
                           :middleware [wrap-basic-auth
                                       wrap-params
                                       [wrap-json-body {:keywords? true}]
                                       wrap-json-response]}]
       ["/cases/create"    {:post case/create-case
                           :middleware [wrap-basic-auth
                                       wrap-params
                                       [wrap-json-body {:keywords? true}]
                                       wrap-json-response]}]
       ["/cases/get"       {:get  case/get-cases
                           :middleware [wrap-basic-auth
                                       wrap-params
                                       [wrap-json-body {:keywords? true}]
                                       wrap-json-response]}]
       ["/cases/delete"    {:post case/delete-case
                           :middleware [wrap-basic-auth
                                       wrap-params
                                       [wrap-json-body {:keywords? true}]
                                       wrap-json-response]}]
       ])
    (ring/create-default-handler)))