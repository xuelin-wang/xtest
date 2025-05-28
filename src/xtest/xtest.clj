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
      login-message (atom nil)
      users-data (atom nil)
      projects-data (atom nil)
      cases-data (atom nil)
      selected-project-id (atom nil)
      current-view (atom :dashboard)
      add-user-form (atom {:email "" :first-name "" :last-name "" :password "" :message nil})
      add-project-form (atom {:name "" :_id "" :message nil})]

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

  (defn- delete-user-by-id [user-id]
    "Delete a user by ID using the API"
    (authenticated-request :post "http://localhost:3100/users/delete" 
                          :body {:id user-id}))

  (defn- create-new-user [user-data]
    "Create a new user using the API"
    (authenticated-request :post "http://localhost:3100/users/create" 
                          :body user-data))

  (defn- delete-project-by-id [project-id]
    "Delete a project by ID using the API"
    (authenticated-request :post "http://localhost:3100/projects/delete" 
                          :body {:_id project-id}))

  (defn- create-new-project [project-data]
    "Create a new project using the API"
    (authenticated-request :post "http://localhost:3100/projects/create" 
                          :body project-data))

  (defn- get-cases-by-project-id [project-id]
    "Get cases for a specific project using the API"
    (authenticated-request :get "http://localhost:3100/cases/get" 
                          :query-params {"project-ids" project-id}))

  (defn login-view []
    [::c/view#app
     [:div.flex.justify-start.items-start.min-h-screen.pt-8.pl-8
      [::c/card.w-full.max-w-6xl
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
                                (if (= 200 (:status response))
                                  (do
                                    (reset! users-data (:body response))
                                    (reset! projects-data nil)
                                    (reset! current-view :users-list)
                                    (reset! login-message {:type :success :text (str "Found " (count (:body response)) " users")}))
                                  (do
                                    (reset! users-data nil)
                                    (reset! login-message {:type :error :text (str "Error: " (get-in response [:body :error]))}))))
                              (weave/push-html! (login-view)))}
            "Users"]
           [::c/button
            {:size :lg
             :variant :primary
             :data-on-click (weave/handler
                              (let [response (authenticated-request :get "http://localhost:3100/projects/get")]
                                (if (= 200 (:status response))
                                  (do
                                    (reset! projects-data (:body response))
                                    (reset! users-data nil)
                                    (reset! current-view :projects-list)
                                    (reset! login-message {:type :success :text (str "Found " (count (:body response)) " projects")}))
                                  (do
                                    (reset! projects-data nil)
                                    (reset! login-message {:type :error :text (str "Error: " (get-in response [:body :error]))}))))
                              (weave/push-html! (login-view)))}
            "Projects"]
           [::c/button
            {:size :lg
             :variant :secondary
             :data-on-click (weave/handler
                              (reset! auth-state {:logged-in? false :user nil :credentials nil})
                              (reset! login-message nil)
                              (reset! users-data nil)
                              (reset! projects-data nil)
                              (reset! cases-data nil)
                              (reset! selected-project-id nil)
                              (reset! current-view :dashboard)
                              (weave/push-html! (login-view)))}
            "Logout"]]
          (when @login-message
            [::c/alert.mt-4 {:type (:type @login-message)}
             (:text @login-message)])
          
          ;; Enhanced users table when data is available
          (when (and @users-data (= @current-view :users-list))
            [:div.mt-6.w-full
             [:div.flex.justify-between.items-center.mb-4
              [:h3.text-lg.font-semibold "Users List"]
              [::c/button
               {:size :md
                :variant :primary
                :data-on-click (weave/handler
                                 (reset! current-view :add-user)
                                 (reset! add-user-form {:email "" :first-name "" :last-name "" :password "" :message nil})
                                 (weave/push-html! (login-view)))}
               "Add User"]]
             [:div.overflow-x-auto
              [:table.min-w-full.bg-white.border.border-gray-300.rounded-lg
               [:thead.bg-gray-50
                [:tr
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "User ID"]
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Email"]
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "First Name"]
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Last Name"]
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Actions"]]]
               [:tbody.bg-white.divide-y.divide-gray-200
                (map-indexed
                  (fn [idx user]
                    [:tr {:key idx :class (if (even? idx) "bg-gray-50" "bg-white")}
                     [:td.px-4.py-2.text-sm.text-gray-900.border-b (or (:id user) (:-id user))]
                     [:td.px-4.py-2.text-sm.text-gray-900.border-b (:email user)]
                     [:td.px-4.py-2.text-sm.text-gray-900.border-b (:first-name user)]
                     [:td.px-4.py-2.text-sm.text-gray-900.border-b (:last-name user)]
                     [:td.px-4.py-2.text-sm.border-b
                      [::c/button
                       {:size :sm
                        :variant :danger
                        :data-on-click (weave/handler
                                         (let [response (delete-user-by-id (:id user))]
                                           (if (= 200 (:status response))
                                             (do
                                               (reset! login-message {:type :success :text "User deleted successfully"})
                                               ;; Refresh the users list
                                               (let [refresh-response (authenticated-request :get "http://localhost:3100/users/get")]
                                                 (if (= 200 (:status refresh-response))
                                                   (reset! users-data (:body refresh-response))
                                                   (reset! login-message {:type :error :text "Error refreshing users list"}))))
                                             (reset! login-message {:type :error :text (str "Error deleting user: " (get-in response [:body :error]))})))
                                         (weave/push-html! (login-view)))}
                       "Delete"]]])
                  @users-data)]]]
             [:div.mt-4.space-x-2
              [::c/button
               {:size :md
                :variant :secondary
                :data-on-click (weave/handler
                                 (reset! users-data nil)
                                 (reset! login-message nil)
                                 (reset! current-view :dashboard)
                                 (weave/push-html! (login-view)))}
               "Clear Table"]]])

          ;; Add User Form
          (when (= @current-view :add-user)
            [:div.mt-6.w-full
             [:div.flex.justify-between.items-center.mb-4
              [:h3.text-lg.font-semibold "Add New User"]
              [::c/button
               {:size :md
                :variant :secondary
                :data-on-click (weave/handler
                                 (reset! current-view :users-list)
                                 (reset! add-user-form {:email "" :first-name "" :last-name "" :password "" :message nil})
                                 (weave/push-html! (login-view)))}
               "Back to Users List"]]
             [:form
              {:class "space-y-4 max-w-md"
               :data-on-submit (weave/handler
                                 {:type :form}
                                 (let [params (:params weave/*request*)
                                       user-data {:email (:email params)
                                                  :first-name (:first-name params)
                                                  :last-name (:last-name params)
                                                  :password (:password params)}]
                                   (if (some clojure.string/blank? (vals user-data))
                                     (swap! add-user-form assoc :message {:type :error :text "All fields are required"})
                                     (let [response (create-new-user user-data)]
                                       (if (= 201 (:status response))
                                         (do
                                           (reset! login-message {:type :success :text "User created successfully"})
                                           (reset! current-view :users-list)
                                           (reset! add-user-form {:email "" :first-name "" :last-name "" :password "" :message nil})
                                           ;; Refresh the users list
                                           (let [refresh-response (authenticated-request :get "http://localhost:3100/users/get")]
                                             (when (= 200 (:status refresh-response))
                                               (reset! users-data (:body refresh-response)))))
                                         (swap! add-user-form assoc :message {:type :error :text (str "Error creating user: " (get-in response [:body :error]))})))))
                                 (weave/push-html! (login-view)))}
              [::c/input
               {:name "email"
                :type "email"
                :placeholder "Enter email address"
                :required true
                :label "Email"}]
              [::c/input
               {:name "first-name"
                :type "text"
                :placeholder "Enter first name"
                :required true
                :label "First Name"}]
              [::c/input
               {:name "last-name"
                :type "text"
                :placeholder "Enter last name"
                :required true
                :label "Last Name"}]
              [::c/input
               {:name "password"
                :type "password"
                :placeholder "Enter password (min 10 chars, mixed case, digit, special char)"
                :required true
                :label "Password"}]
              [::c/button
               {:type "submit"
                :size :lg
                :variant :primary
                :class "w-full"}
               "Create User"]]
             (when (:message @add-user-form)
               [::c/alert.mt-4 {:type (get-in @add-user-form [:message :type])}
                (get-in @add-user-form [:message :text])])])

          ;; Enhanced projects table when data is available
          (when (and @projects-data (= @current-view :projects-list))
            [:div.mt-6.w-full
             [:div.flex.justify-between.items-center.mb-4
              [:h3.text-lg.font-semibold "Projects List"]
              [::c/button
               {:size :md
                :variant :primary
                :data-on-click (weave/handler
                                 (reset! current-view :add-project)
                                 (reset! add-project-form {:name "" :_id "" :message nil})
                                 (weave/push-html! (login-view)))}
               "Add Project"]]
             [:div.overflow-x-auto
              [:table.min-w-full.bg-white.border.border-gray-300.rounded-lg
               [:thead.bg-gray-50
                [:tr
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Project ID"]
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Name"]
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Actions"]]]
               [:tbody.bg-white.divide-y.divide-gray-200
                (map-indexed
                  (fn [idx project]
                    [:tr {:key idx :class (if (even? idx) "bg-gray-50" "bg-white")}
                     [:td.px-4.py-2.text-sm.text-gray-900.border-b (:id project)]
                     [:td.px-4.py-2.text-sm.text-gray-900.border-b (:name project)]
                     [:td.px-4.py-2.text-sm.border-b
                      [:div.space-x-2
                       [::c/button
                        {:size :sm
                         :variant :secondary
                         :data-on-click (weave/handler
                                          (let [response (get-cases-by-project-id (:id project))]
                                            (if (= 200 (:status response))
                                              (do
                                                (reset! cases-data (:body response))
                                                (reset! selected-project-id (:id project))
                                                (reset! current-view :cases-list)
                                                (reset! login-message {:type :success :text (str "Found " (count (:body response)) " cases for project " (:id project))}))
                                              (reset! login-message {:type :error :text (str "Error fetching cases: " (get-in response [:body :error]))})))
                                          (weave/push-html! (login-view)))}
                        "Cases"]
                       [::c/button
                        {:size :sm
                         :variant :danger
                         :data-on-click (weave/handler
                                          (let [response (delete-project-by-id (:id project))]
                                            (if (= 200 (:status response))
                                              (do
                                                (reset! login-message {:type :success :text "Project deleted successfully"})
                                                ;; Refresh the projects list
                                                (let [refresh-response (authenticated-request :get "http://localhost:3100/projects/get")]
                                                  (if (= 200 (:status refresh-response))
                                                    (reset! projects-data (:body refresh-response))
                                                    (reset! login-message {:type :error :text "Error refreshing projects list"}))))
                                              (reset! login-message {:type :error :text (str "Error deleting project: " (get-in response [:body :error]))})))
                                          (weave/push-html! (login-view)))}
                        "Delete"]]]])
                  @projects-data)]]]
             [:div.mt-4.space-x-2
              [::c/button
               {:size :md
                :variant :secondary
                :data-on-click (weave/handler
                                 (reset! projects-data nil)
                                 (reset! login-message nil)
                                 (reset! current-view :dashboard)
                                 (weave/push-html! (login-view)))}
               "Clear Table"]]])

          ;; Add Project Form
          (when (= @current-view :add-project)
            [:div.mt-6.w-full
             [:div.flex.justify-between.items-center.mb-4
              [:h3.text-lg.font-semibold "Add New Project"]
              [::c/button
               {:size :md
                :variant :secondary
                :data-on-click (weave/handler
                                 (reset! current-view :projects-list)
                                 (reset! add-project-form {:name "" :_id "" :message nil})
                                 (weave/push-html! (login-view)))}
               "Back to Projects List"]]
             [:form
              {:class "space-y-4 max-w-md"
               :data-on-submit (weave/handler
                                 {:type :form}
                                 (let [params (:params weave/*request*)
                                       project-data {:name (:name params)
                                                    :_id (:_id params)}]
                                   (if (some clojure.string/blank? (vals project-data))
                                     (swap! add-project-form assoc :message {:type :error :text "All fields are required"})
                                     (let [response (create-new-project project-data)]
                                       (if (= 201 (:status response))
                                         (do
                                           (reset! login-message {:type :success :text "Project created successfully"})
                                           (reset! current-view :projects-list)
                                           (reset! add-project-form {:name "" :_id "" :message nil})
                                           ;; Refresh the projects list
                                           (let [refresh-response (authenticated-request :get "http://localhost:3100/projects/get")]
                                             (when (= 200 (:status refresh-response))
                                               (reset! projects-data (:body refresh-response)))))
                                         (swap! add-project-form assoc :message {:type :error :text (str "Error creating project: " (get-in response [:body :error]))})))))
                                 (weave/push-html! (login-view)))}
              [::c/input
               {:name "name"
                :type "text"
                :placeholder "Enter project name"
                :required true
                :label "Project Name"}]
              [::c/input
               {:name "_id"
                :type "text"
                :placeholder "Enter project ID (format: project-123)"
                :required true
                :label "Project ID"}]
              [::c/button
               {:type "submit"
                :size :lg
                :variant :primary
                :class "w-full"}
               "Create Project"]]
             (when (:message @add-project-form)
               [::c/alert.mt-4 {:type (get-in @add-project-form [:message :type])}
                (get-in @add-project-form [:message :text])])])

          ;; Cases list view for specific project
          (when (and @cases-data (= @current-view :cases-list))
            [:div.mt-6.w-full
             [:div.flex.justify-between.items-center.mb-4
              [:h3.text-lg.font-semibold (str "Cases for Project: " @selected-project-id)]
              [::c/button
               {:size :md
                :variant :secondary
                :data-on-click (weave/handler
                                 (reset! current-view :projects-list)
                                 (reset! cases-data nil)
                                 (reset! selected-project-id nil)
                                 (weave/push-html! (login-view)))}
               "Back to Projects"]]
             [:div.overflow-x-auto
              [:table.min-w-full.bg-white.border.border-gray-300.rounded-lg
               [:thead.bg-gray-50
                [:tr
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Case ID"]
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Name"]
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Description"]
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Tags"]]]
               [:tbody.bg-white.divide-y.divide-gray-200
                (if (empty? @cases-data)
                  [:tr
                   [:td.px-4.py-8.text-center.text-gray-500.border-b {:colspan 4}
                    "No cases found for this project"]]
                  (map-indexed
                    (fn [idx case-item]
                      [:tr {:key idx :class (if (even? idx) "bg-gray-50" "bg-white")}
                       [:td.px-4.py-2.text-sm.text-gray-900.border-b (:id case-item)]
                       [:td.px-4.py-2.text-sm.text-gray-900.border-b (:name case-item)]
                       [:td.px-4.py-2.text-sm.text-gray-900.border-b (if (> (count (:description case-item)) 100)
                                                                         (str (subs (:description case-item) 0 100) "...")
                                                                         (:description case-item))]
                       [:td.px-4.py-2.text-sm.text-gray-900.border-b (if (:tags case-item)
                                                                         (clojure.string/join ", " (:tags case-item))
                                                                         "")]])
                    @cases-data))]]
             [:div.mt-4
              [::c/button
               {:size :md
                :variant :secondary
                :data-on-click (weave/handler
                                 (reset! cases-data nil)
                                 (reset! selected-project-id nil)
                                 (reset! login-message nil)
                                 (reset! current-view :dashboard)
                                 (weave/push-html! (login-view)))}
               "Clear Cases"]]]])
          ]
         
         ;; Login form when not logged in  
         [:div
          [:h2.text-2xl.font-bold.mb-6.text-center "XTest Login"]
          [:form
           {:class "space-y-4 max-w-md"
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
           [:div
            [:label.block.text-sm.font-medium.text-gray-700.mb-1 {:for "email"} "Email"]
            [::c/input
             {:name "email"
              :type "email"
              :placeholder "Enter your email"
              :value "alice.smith3@example.com"
              :required true
              :id "email"}]]
           [:div
            [:label.block.text-sm.font-medium.text-gray-700.mb-1 {:for "password"} "Password"]
            [::c/input
             {:name "password"
              :type "password"
              :placeholder "Enter your password"
              :value "Secur3P@ssword!"
              :required true
              :id "password"}]]
           [::c/button
            {:type "submit"
             :size :md
             :variant :primary
             :class "w-auto px-8"}
            "Login"]]
          (when @login-message
            [::c/alert.mt-4 {:type (:type @login-message)}
             (:text @login-message)])
          ])]]]))

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