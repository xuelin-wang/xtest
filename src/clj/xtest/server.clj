(ns xtest.server
  (:gen-class)
  (:require [io.pedestal.http :as server]
            [clojure.core.async :as a]
            [reitit.pedestal :as pedestal]
            [muuntaja.interceptor]
            [reitit.http :as http]
            [reitit.ring :as ring]
            [clojure.string :as str]
            [clojure.data.codec.base64 :as base64]
            [next.jdbc :as jdbc]
            [xtest.api.user :as user]
            [xtest.api.project :as project]
            [xtest.api.case :as case]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [xtest.api.db :as db]

            )
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

(defn interceptor [x]
  (case x
    :wrap-basic-auth
    {:enter
     (fn [{:keys [request] :as ctx}]
       (if-not (has-any-users?)
         ctx
         ;; Otherwise, require authentication
         (let [auth-header (get-in request [:headers "authorization"])]
           (if-let [[email password] (parse-basic-auth auth-header)]
             (if-let [authenticated-user (authenticate-user email password)]
               (assoc-in ctx [:request :authenticated-user] authenticated-user)
               (assoc ctx
                 :response
                 {:status 401
                  :headers {"WWW-Authenticate" "Basic realm=\"API\""
                            "Content-Type" "application/json"}
                  :body "{\"error\":\"Invalid credentials\"}"}
                 ))
             (assoc ctx
               :response
               {:status 401
                :headers {"WWW-Authenticate" "Basic realm=\"API\""
                          "Content-Type" "application/json"}
                :body "{\"error\":\"Missing or invalid Authorization header\"}"}
               )))))
     }
     nil
    )
  )

(defn handler [{:keys [via]}]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (conj via :handler)})

(def async-handler
  {:enter (fn [{:keys [request] :as ctx}]
            (a/go (assoc ctx :response (handler request))))})

(def router
  (pedestal/routing-interceptor
(http/router
      [
       ["/api"
       {}

       ["/sync"
        {:get {:handler handler}}]

["/async"
        {:get {:interceptors [async-handler]}}]
        
        ["/users" {}
         ["/create" 
          {:post {:interceptors 
                  [
                   (interceptor :wrap-basic-auth)
                   ] :handler user/create-user}}
          ]
         
         ["/get" 
          {:get {:interceptors [(interceptor :wrap-basic-auth)] :handler user/get-users}}
          ]

         ["/update"
          {:post {:interceptors [(interceptor :wrap-basic-auth)] :handler user/update-user}}
          ]

         ["/delete"
          {:post {:interceptors [(interceptor :wrap-basic-auth)] :handler user/delete-user}}
          ]

         ["/login"
          {:post {:interceptors [] :handler user/login}}
          ]
         ]
         
        ["/projects" {}
         ["/create"
          {:post {:interceptors
                  [
                   (interceptor :wrap-basic-auth)
                   ] :handler project/create-project}}
          ]

         ["/get"
          {:get {:interceptors
                  [
                   (interceptor :wrap-basic-auth)
                   ] :handler project/get-projects}}
          ]

         ["/delete"
          {:post {:interceptors
                 [
                  (interceptor :wrap-basic-auth)
                  ] :handler project/delete-project}}
          ]
         ]

        ["/cases" {}
         ["/create"
          {:post {:interceptors
                  [
                   (interceptor :wrap-basic-auth)
                   ] :handler case/create-case}}
          ]

         ["/get"
          {:get {:interceptors
                 [
                  (interceptor :wrap-basic-auth)
                  ] :handler case/get-cases}}
          ]

         ["/delete"
          {:post {:interceptors
                  [
                   (interceptor :wrap-basic-auth)
                   ] :handler case/delete-case}}
          ]
         ]
        ]

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
       
       ]

      ;; optional interceptors for all matched routes
      {:data {:interceptors [(interceptor :router)]}})

    ;; optional default ring handlers (if no routes have matched)
    (ring/routes
      (ring/create-resource-handler {:path "/public"})
      (ring/create-default-handler))

    ;; optional top-level routes for both routes & default route
    {:interceptors [(muuntaja.interceptor/format-interceptor)
                    (interceptor :top)]}))

(defn start
  ([] (start {:port 9090 :view :login }))                      ;; zero-arity for -M (calls with empty map)
  ([options]
   (println "Using options:" options)

(let [port (or (:port options) 9090)]
     (-> {::server/type :jetty
          ::server/port port
          ::server/join? false
          ;; no pedestal routes
          ::server/routes []}
(server/default-interceptors)
         ;; use the reitit router
         (pedestal/replace-last-interceptor router)
         (server/dev-interceptors)
         (server/create-server)
         (server/start))
     (println "server running in port " port)
   )))

(defn -main [& _]
  (start))