(ns xtest.xtest
  (:gen-class)
  (:require
    [clojure.string]
    [reitit.core :as r]
    [weave.core :as weave]
    [weave.session :as session]
    [weave.components :as c]
    [clj-http.client :as http]
    [clojure.data.json :as json]
    [clojure.data.codec.base64 :as base64]))

(let [click-count (atom 0)]

  (defn click-count-view []
    [::c/view#app
     [::c/center-hv
      [::c/card
       [:div.text-center.text-6xl.font-bold.mb-6.text-blue-600
        @click-count]
       [::c/button
        {:size :xl
         :variant :primary
         :data-on-click (weave/handler
                          (swap! click-count inc)
                          (weave/push-html!
                            (click-count-view)))}
        "Increment Count"]]]]))


(let [todos (atom ["Pickup groceries"
                   "Finish Project"])]

  (defn todo-view []
    [::c/view#app
     [::c/row.justify-center
      [::c/card.mt-3
       [:h1.text-2xl.font-bold.mb-4.text-gray-800
        "Todo List"]
       [:ul {:class "space-y-2 mb-6"}
        (map-indexed
          (fn [idx x]
            [:li.flex.items-center.justify-between.p-3.rounded
             [:span.text-gray-700 x]
             [::c/button
              {:size :md
               :variant :danger
               :data-on-click (weave/handler
                                (swap! todos (fn [items]
                                               (vec (concat
                                                      (subvec items 0 idx)
                                                      (subvec items (inc idx))))))
                                (weave/push-html!
                                  (todo-view)))}
              "Delete"]])
          @todos)]
       [:form
        {:class "mt-4 space-y-4"
         :data-on-submit (weave/handler
                           {:type :form}
                           (swap! todos conj (-> weave/*request* :params :bar))
                           (weave/push-html!
                             (todo-view)))}
        [:div {:class "flex space-x-2 m-3"}
         [::c/input
          {:name "bar"
           :placeholder "Add new todo item"
           :required true}]
         [::c/button
          {:type "submit"
           :size :md
           :variant :primary}
          "Add"]]]]]]))

(let [router (r/router
               [["/views/one" ::view-one]
                ["/views/two" ::view-two]])]

  (defn navigation-view []
    [::c/view#app
     [::c/col
      {:class "w-1/3 m-5"}
      [::c/row
       [::c/card.mb-5.w-full
        [::c/flex-between
         [::c/button
          {:size :md
           :variant :primary
           :data-on-click (weave/handler
                            (weave/push-path! "/views/one" navigation-view))}
          "Page One"]
         [::c/button
          {:size :md
           :variant :primary
           :data-on-click (weave/handler
                            (weave/push-path! "/views/two" navigation-view))}
          "Page Two"]]]]

      [::c/row
       [::c/card.w-full
        (case (get-in (r/match-by-path router weave/*app-path*) [:data :name])
          ::view-one [:div.text-center
                      [:h2.text-xl.font-bold
                       "Page One Content"]
                      [:p "This is the content for page one."]]
          ::view-two [:div.text-center
                      [:h2.text-xl.font-bold
                       "Page Two Content"]
                      [:p "This is the content for page two."]]
          [:div.text-center.text-gray-500
           "Select a page from the navigation above"])]]]]))

(def routes
  (-> (r/router
        [["/sign-in" {:name :sign-in}]
         ["/app" {:name :app
                  :auth-required? true}]])))

(declare session-view)

(def router (weave/make-router))

(defn session-app-view []
  [:div
   [::c/alert.m-5 {:type :info}
    [:p.font-semibold
     (str "Welcome, " (or (:name (:identity weave/*request*)) "User") "!")]
    [:p.text-sm
     "You are currently logged in."]]
   [::c/row.justify-center
    [::c/button
     {:size :xl
      :variant :primary
      :data-on-click (weave/handler
                       (weave/set-cookie! (session/sign-out))
                       (weave/broadcast-path! "/sign-in")
                       (weave/push-reload!))}
     "Sign Out"]]])

(defn session-sign-in-view []
  [::c/sign-in
   {:title "Welcome Back"
    :username-label "Username"
    :username-placeholder "Enter your username"
    :password-label "Password"
    :password-placeholder "Enter your password"
    :submit-text "Sign In"
    :forgot-password-text "Forgot your password?"
    :forgot-password-url "/#/forgot-password"
    :register-text "Don't have an account?"
    :register-url "/#/register"
    :on-submit (weave/handler
                 {:type :form}
                 (weave/set-cookie!
                   (session/sign-in
                     {:name (:username (:params weave/*request*)) :role "User"}))
                 (weave/broadcast-path! "/app")
                 (weave/push-reload!))}])

(defn session-view []
  [::c/view#app
   (case (router routes)
     :sign-in (session-sign-in-view)
     :app [::c/center-hv
           [::c/card
            (session-app-view)]]
     (session-sign-in-view))])

;; Login application with API integration
(let [auth-state (atom {:logged-in? false :user nil :credentials nil})
      login-message (atom nil)]

  (defn- encode-basic-auth [email password]
    "Encode email and password for HTTP Basic Auth"
    (let [credentials (str email ":" password)
          encoded (base64/encode (.getBytes credentials))]
      (str "Basic " (String. encoded))))

  (defn- call-login-api [email password]
    "Call the /users/login API with email and password"
    (try
      (let [response (http/post "http://localhost:3100/users/login"
                               {:headers {"Content-Type" "application/json"}
                                :body (json/write-str {:email email :password password})
                                :as :json
                                :throw-exceptions false})]
        (if (= 200 (:status response))
          {:success true 
           :user (:user (:body response))
           :message (:message (:body response))}
          {:success false 
           :error (or (get-in response [:body :error]) "Login failed")}))
      (catch Exception e
        {:success false :error (str "Connection error: " (.getMessage e))})))

  (defn- authenticated-request 
    "Make an authenticated API request using stored credentials"
    [method url & {:keys [body query-params] :or {body nil query-params nil}}]
    (let [credentials (:credentials @auth-state)]
      (if credentials
        (try
          (http/request {:method method
                         :url url
                         :headers {"Content-Type" "application/json"
                                  "Authorization" credentials}
                         :body (when body (json/write-str body))
                         :query-params query-params
                         :as :json
                         :throw-exceptions false})
          (catch Exception e
            {:status 500 :body {:error (str "Request failed: " (.getMessage e))}}))
        {:status 401 :body {:error "Not authenticated"}})))

  (defn login-view []
    [::c/view#app
     [::c/center-hv
      [::c/card
       (if (:logged-in? @auth-state)
         ;; Dashboard view when logged in
         [:div.text-center
          [:h2.text-2xl.font-bold.mb-4.text-green-600 "Login Successful!"]
          [:p.mb-4 (str "Welcome, " (get-in @auth-state [:user :first-name]) "!")]
          [:div.space-y-4
           [::c/button
            {:size :lg
             :variant :primary
             :data-on-click (weave/handler
                              (let [response (authenticated-request :get "http://localhost:3100/users/get")]
                                (reset! login-message 
                                        (if (= 200 (:status response))
                                          {:type :success :text (str "Found " (count (:body response)) " users")}
                                          {:type :error :text (str "Error: " (get-in response [:body :error]))})))
                              (weave/push-html! (login-view)))}
            "Test API - Get Users"]
           [::c/button
            {:size :lg
             :variant :secondary
             :data-on-click (weave/handler
                              (reset! auth-state {:logged-in? false :user nil :credentials nil})
                              (reset! login-message nil)
                              (weave/push-html! (login-view)))}
            "Logout"]]
          (when @login-message
            [::c/alert.mt-4 {:type (:type @login-message)}
             (:text @login-message)])]
         
         ;; Login form when not logged in
         [:div
          [:h2.text-2xl.font-bold.mb-6.text-center "XTest Login"]
          [:form
           {:class "space-y-4"
            :data-on-submit (weave/handler
                              {:type :form}
                              (let [params (:params weave/*request*)
                                    email (:email params)
                                    password (:password params)]
                                (if (or (clojure.string/blank? email) (clojure.string/blank? password))
                                  (reset! login-message {:type :error :text "Please enter both email and password"})
                                  (let [result (call-login-api email password)]
                                    (if (:success result)
                                      (do
                                        (reset! auth-state {:logged-in? true 
                                                           :user (:user result)
                                                           :credentials (encode-basic-auth email password)})
                                        (reset! login-message {:type :success :text (:message result)}))
                                      (reset! login-message {:type :error :text (:error result)}))))
                                (weave/push-html! (login-view))))}
           [::c/input
            {:name "email"
             :type "email"
             :placeholder "Enter your email"
             :value "alice.smith3@example.com"
             :required true
             :label "Email"}]
           [::c/input
            {:name "password"
             :type "password"
             :placeholder "Enter your password"
             :value "Secur3P@ssword!"
             :required true
             :label "Password"}]
           [::c/button
            {:type "submit"
             :size :xl
             :variant :primary
             :class "w-full"}
            "Login"]]
          (when @login-message
            [::c/alert.mt-4 {:type (:type @login-message)}
             (:text @login-message)])])]]]))

(defn run [options]
  (let [view (condp = (:view options)
               :click-count #'click-count-view
               :todo #'todo-view
               :session #'session-view
               :navigation #'navigation-view
               :login #'login-view)]
    (weave/run view options)))

(defn start
  ([] (start {:port 9090 :view :login }))                      ;; zero-arity for -M (calls with empty map)
  ([options]
   (println "Using options:" options)

   (let [view (condp = (or (:view options) :login)
                :click-count #'click-count-view
                :todo #'todo-view
                :session #'session-view
                :navigation #'navigation-view
                :login #'login-view)
         port (or (:port options) 9090)]
     (weave/run view {:http-kit {:port port}}))   
   ))

(defn -main [& _]
  (start))

(comment
  (def s (weave/run
           #'click-count-view {:port 8080
                               :csrf-secret "my-csrf-secret"
                               :jwt-secret "my-jwt-secret"}))

  (s :timeout 100)
  ;;
  )