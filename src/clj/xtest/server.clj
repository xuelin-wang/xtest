(ns xtest.server
  (:gen-class)
  (:require [io.pedestal.http :as server]
            [clojure.core.async :as a]
            [reitit.pedestal :as pedestal]
            [muuntaja.interceptor]
            [reitit.http :as http]
            [reitit.ring :as ring]
[io.pedestal.http.route :as route]
            [io.pedestal.http.ring-middlewares :as middlewares]
            [ring.middleware.file :as ring-file]
            [ring.middleware.file-info :as ring-file-info]
            ))


(defn interceptor [x]
  (cond
    (#{:api :sync :async :get} x)
    {:enter (fn [ctx] (update-in ctx [:request :via] (fnil conj []) {:enter x}))
     :leave (fn [ctx] (update-in ctx [:response :body] conj {:leave x}))}
    :else
    {}
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
       {:interceptors [(interceptor :api)]}

       ["/sync"
        {:interceptors [(interceptor :sync)]
         :get {:interceptors [(interceptor :get)]
               :handler handler}}]

["/async"
        {:interceptors [(interceptor :async)]
         :get {:interceptors [(interceptor :get) async-handler]}}]]

["/index.html" {:get {:handler (fn [_]
                                        (let [resource (clojure.java.io/resource "public/index.html")]
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