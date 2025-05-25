(ns xtest.xtest
  (:require [weave.core :as weave])
(:gen-class))

(defn view []
  [:div.p-6
   [:h1#label.text-2xl.font-bold "Hello Weave!"]
   [:button.bg-blue-500.text-white.px-4.py-2.rounded
    {:data-on-click
     (weave/handler
       (weave/push-html!
         [:h1#label.text-2xl.font-bold "Button was clicked!"]))}
    "CLICK ME"]])

(defn start
  ([] (start {:port 9090}))                      ;; zero-arity for -M (calls with empty map)
  ([opts]
   (println "Using port:" (or (:port opts) 9090))
   (weave/run view {:http-kit {:port (or (:port opts) 9090)}})))

(defn -main [& _]
  (start))
