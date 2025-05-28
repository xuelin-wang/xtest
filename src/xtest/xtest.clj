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
      add-project-form (atom {:name "" :_id "" :message nil})
      delete-confirmation (atom nil)
      all-cases-by-projects (atom nil)]

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

  (defn- delete-case-by-id [case-id]
    "Delete a case by ID using the API"
    (authenticated-request :post "http://localhost:3100/cases/delete" 
                          :body {:id case-id}))

  (defn- get-all-cases []
    "Get all cases using the API"
    (authenticated-request :get "http://localhost:3100/cases/get"))

  (defn- get-all-cases-by-projects []
    "Get all cases and projects, then organize cases by project"
    (let [projects-response (authenticated-request :get "http://localhost:3100/projects/get")
          cases-response (authenticated-request :get "http://localhost:3100/cases/get")]
      (if (and (= 200 (:status projects-response)) (= 200 (:status cases-response)))
        {:success true
         :projects (:body projects-response)
         :cases (:body cases-response)}
        {:success false
         :error "Failed to fetch projects or cases"})))

  (defn- extract-folder-from-tags [tags]
    "Extract folder path from tags with 'folder:' prefix, return '/' if not found"
    (if tags
      (let [folder-tag (first (filter #(and % (.startsWith % "folder:")) tags))]
        (if folder-tag
          (subs folder-tag 7) ; Remove "folder:" prefix (7 characters)
          "/"))
      "/"))

  (defn- filter-non-folder-tags [tags]
    "Filter out tags that start with 'folder:' prefix"
    (if tags
      (filter #(and % (not (.startsWith % "folder:"))) tags)
      []))

  (defn- normalize-folder-path [folder]
    "Normalize folder path to always start with '/'"
    (if (and folder (not= folder "/"))
      (if (.startsWith folder "/")
        folder
        (str "/" folder))
      "/"))

  (defn- folder-sort-key [folder]
    "Generate sort key for folder path based on hierarchical rules"
    (let [normalized (normalize-folder-path folder)
          parts (if (= normalized "/") 
                  [""] 
                  (vec (remove empty? (clojure.string/split normalized #"/"))))]
      ;; Create a sort key where "/" comes first, then sorted by parts
      ;; Each level: empty string (no subfolder) comes before actual folder names
      (vec (concat 
             (if (= normalized "/") [0] [1]) ; "/" gets priority 0, others get 1
             (mapcat (fn [part] [part ""]) parts))))) ; Add empty string after each part for sub-level sorting

  (defn- sort-cases-by-folder [cases]
    "Sort cases by folder value according to hierarchical rules"
    (sort-by 
      (fn [case-item] 
        (folder-sort-key (extract-folder-from-tags (:tags case-item))))
      cases))

  (defn login-view []
    [::c/view#app
     [:div.flex.justify-start.items-start.min-h-screen.pt-8.pl-8
      [::c/card.w-full.max-w-6xl
       (if (:logged-in? @auth-state)
         ;; Dashboard view when logged in
         [:div.text-center
          [:h2.text-2xl.font-bold.mb-4.text-green-600 "Login Successful!"]
          [:p.mb-4 (str "Welcome, " (get-in @auth-state [:user :first-name]) "!")]
          [:div.flex.space-x-1.mb-6.border-b.border-gray-200
           [:button
            {:class (str "px-4 py-2 font-medium text-sm border-b-2 " 
                        (if (= @current-view :users-list) 
                          "border-red-500 text-red-600" 
                          "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"))
             :data-on-click (weave/handler
                              (let [response (authenticated-request :get "http://localhost:3100/users/get")]
                                (if (= 200 (:status response))
                                  (do
                                    (reset! users-data (:body response))
                                    (reset! projects-data nil)
                                    (reset! cases-data nil)
                                    (reset! current-view :users-list)
                                    (reset! login-message {:type :success :text (str "Found " (count (:body response)) " users")}))
                                  (do
                                    (reset! users-data nil)
                                    (reset! login-message {:type :error :text (str "Error: " (get-in response [:body :error]))}))))
                              (weave/push-html! (login-view)))}
            "Users"]
           [:button
            {:class (str "px-4 py-2 font-medium text-sm border-b-2 " 
                        (if (= @current-view :projects-list) 
                          "border-red-500 text-red-600" 
                          "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"))
             :data-on-click (weave/handler
                              (let [response (authenticated-request :get "http://localhost:3100/projects/get")]
                                (if (= 200 (:status response))
                                  (do
                                    (reset! projects-data (:body response))
                                    (reset! users-data nil)
                                    (reset! cases-data nil)
                                    (reset! current-view :projects-list)
                                    (reset! login-message {:type :success :text (str "Found " (count (:body response)) " projects")}))
                                  (do
                                    (reset! projects-data nil)
                                    (reset! login-message {:type :error :text (str "Error: " (get-in response [:body :error]))}))))
                              (weave/push-html! (login-view)))}
            "Projects"]
           [:button
            {:class (str "px-4 py-2 font-medium text-sm border-b-2 " 
                        (if (= @current-view :cases-list) 
                          "border-red-500 text-red-600" 
                          "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"))
             :data-on-click (weave/handler
                              (reset! users-data nil)
                              (reset! projects-data nil)
                              (reset! current-view :cases-list)
                              ;; If already showing cases for a specific project, keep them
                              (when (not @selected-project-id)
                                ;; Otherwise, fetch all cases grouped by projects
                                (let [response (get-all-cases-by-projects)]
                                  (if (:success response)
                                    (do
                                      (reset! all-cases-by-projects response)
                                      (reset! cases-data nil)
                                      (reset! selected-project-id nil)
                                      (reset! login-message {:type :success :text (str "Loaded cases for " (count (:projects response)) " projects")}))
                                    (do
                                      (reset! all-cases-by-projects nil)
                                      (reset! login-message {:type :error :text (str "Error fetching cases: " (:error response))})))))
                              (weave/push-html! (login-view)))}
            "Cases"]
           [:div.ml-auto
            [::c/button
             {:size :md
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
             "Logout"]]]
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
                                         (reset! delete-confirmation {:type :user 
                                                                      :user-id (:id user)
                                                                      :user-email (:email user)})
                                         (weave/push-html! (login-view)))}
                       "Delete"]]])
                  @users-data)]]]
])

          ;; Delete Confirmation Dialog
          (when @delete-confirmation
            [:div.fixed.inset-0.bg-gray-600.bg-opacity-50.overflow-y-auto.h-full.w-full.z-50
             [:div.relative.top-20.mx-auto.p-5.border.w-96.shadow-lg.rounded-md.bg-white
              [:div.mt-3.text-center
               [:div.mx-auto.flex.items-center.justify-center.h-12.w-12.rounded-full.bg-red-100
                [:svg.h-6.w-6.text-red-600 {:fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                 [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.962-.833-2.732 0L3.732 16.5c-.77.833.192 2.5 1.732 2.5z"}]]]
               [:h3.text-lg.leading-6.font-medium.text-gray-900.mt-4 
                (condp = (:type @delete-confirmation)
                  :user "Delete User"
                  :project "Delete Project" 
                  :case "Delete Case")]
               [:div.mt-2.px-7.py-3
                [:p.text-sm.text-gray-500
                 (condp = (:type @delete-confirmation)
                   :user (str "Are you sure you want to delete user " (:user-email @delete-confirmation) "? This action cannot be undone.")
                   :project (str "Are you sure you want to delete project " (:project-name @delete-confirmation) "? This action cannot be undone.")
                   :case (str "Are you sure you want to delete case " (:case-name @delete-confirmation) "? This action cannot be undone."))]]
               [:div.items-center.px-4.py-3.space-x-4.flex.justify-center
                [::c/button
                 {:size :md
                  :variant :secondary
                  :data-on-click (weave/handler
                                   (reset! delete-confirmation nil)
                                   (weave/push-html! (login-view)))}
                 "Cancel"]
                [::c/button
                 {:size :md
                  :variant :danger
                  :data-on-click (weave/handler
                                   (condp = (:type @delete-confirmation)
                                     :user 
                                     ;; Delete user
                                     (let [response (delete-user-by-id (:user-id @delete-confirmation))]
                                       (if (= 200 (:status response))
                                         (do
                                           (reset! login-message {:type :success :text "User deleted successfully"})
                                           ;; Refresh the users list
                                           (let [refresh-response (authenticated-request :get "http://localhost:3100/users/get")]
                                             (if (= 200 (:status refresh-response))
                                               (reset! users-data (:body refresh-response))
                                               (reset! login-message {:type :error :text "Error refreshing users list"}))))
                                         (reset! login-message {:type :error :text (str "Error deleting user: " (get-in response [:body :error]))})))
                                     
                                     :project
                                     ;; Delete project
                                     (let [response (delete-project-by-id (:project-id @delete-confirmation))]
                                       (if (= 200 (:status response))
                                         (do
                                           (reset! login-message {:type :success :text "Project deleted successfully"})
                                           ;; Refresh the projects list
                                           (let [refresh-response (authenticated-request :get "http://localhost:3100/projects/get")]
                                             (if (= 200 (:status refresh-response))
                                               (reset! projects-data (:body refresh-response))
                                               (reset! login-message {:type :error :text "Error refreshing projects list"}))))
                                         (reset! login-message {:type :error :text (str "Error deleting project: " (get-in response [:body :error]))})))
                                     
                                     :case
                                     ;; Delete case
                                     (let [response (delete-case-by-id (:case-id @delete-confirmation))]
                                       (if (= 200 (:status response))
                                         (do
                                           (reset! login-message {:type :success :text "Case deleted successfully"})
                                           ;; Refresh the current cases view
                                           (if @selected-project-id
                                             ;; Refresh project-specific cases
                                             (let [refresh-response (get-cases-by-project-id @selected-project-id)]
                                               (if (= 200 (:status refresh-response))
                                                 (reset! cases-data (:body refresh-response))
                                                 (reset! login-message {:type :error :text "Error refreshing cases list"})))
                                             ;; Refresh all cases grouped by projects
                                             (let [refresh-response (get-all-cases-by-projects)]
                                               (if (:success refresh-response)
                                                 (reset! all-cases-by-projects refresh-response)
                                                 (reset! login-message {:type :error :text "Error refreshing cases list"})))))
                                         (reset! login-message {:type :error :text (str "Error deleting case: " (get-in response [:body :error]))}))))
                                   (reset! delete-confirmation nil)
                                   (weave/push-html! (login-view)))}
                 "Yes, Delete"]]]]])

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
                                          (reset! delete-confirmation {:type :project 
                                                                       :project-id (:id project)
                                                                       :project-name (:name project)})
                                          (weave/push-html! (login-view)))}
                        "Delete"]]]])
                  @projects-data)]]]
])

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
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Folder"]
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Tags"]
                 [:th.px-4.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Actions"]]]
               [:tbody.bg-white.divide-y.divide-gray-200
                (if (empty? @cases-data)
                  [:tr
                   [:td.px-4.py-8.text-center.text-gray-500.border-b {:colspan 6}
                    "No cases found for this project"]]
                  (map-indexed
                    (fn [idx case-item]
                      [:tr {:key idx :class (if (even? idx) "bg-gray-50" "bg-white")}
                       [:td.px-4.py-2.text-sm.text-gray-900.border-b (:id case-item)]
                       [:td.px-4.py-2.text-sm.text-gray-900.border-b (:name case-item)]
                       [:td.px-4.py-2.text-sm.text-gray-900.border-b (if (> (count (:description case-item)) 100)
                                                                         (str (subs (:description case-item) 0 100) "...")
                                                                         (:description case-item))]
                       [:td.px-4.py-2.text-sm.text-gray-900.border-b (extract-folder-from-tags (:tags case-item))]
                       [:td.px-4.py-2.text-sm.text-gray-900.border-b (let [filtered-tags (filter-non-folder-tags (:tags case-item))]
                                                                         (if (seq filtered-tags)
                                                                           (clojure.string/join ", " filtered-tags)
                                                                           ""))]
                       [:td.px-4.py-2.text-sm.border-b
                        [::c/button
                         {:size :sm
                          :variant :danger
                          :data-on-click (weave/handler
                                           (reset! delete-confirmation {:type :case 
                                                                        :case-id (:id case-item)
                                                                        :case-name (:name case-item)})
                                           (weave/push-html! (login-view)))}
                         "Delete"]]])
                    (sort-cases-by-folder @cases-data)))]]
]])

          ;; Standalone Cases view (cases grouped by project)
          (when (and (= @current-view :cases-list) (not @selected-project-id))
            [:div.mt-6.w-full
             [:div.flex.justify-between.items-center.mb-4
              [:h3.text-lg.font-semibold "Cases by Project"]
              [:p.text-sm.text-gray-500 "All projects and their cases"]]
             (if @all-cases-by-projects
               [:div.space-y-6
                (let [projects (:projects @all-cases-by-projects)
                      all-cases (:cases @all-cases-by-projects)]
                  (map-indexed
                    (fn [project-idx project]
                      (let [project-cases (sort-cases-by-folder (filter #(= (:project-id %) (:id project)) all-cases))]
                        [:div {:key project-idx :class "border border-gray-200 rounded-lg p-4 bg-white"}
                         [:div.flex.justify-between.items-center.mb-3
                          [:h4.text-md.font-semibold.text-gray-800 (str "Project: " (:name project))]
                          [:span.text-sm.text-gray-500 (str "(" (count project-cases) " cases)")]]
                         (if (empty? project-cases)
                           [:div.text-center.text-gray-500.py-4
                            [:p "No cases found for this project"]]
                           [:div.overflow-x-auto
                            [:table.min-w-full.bg-white.border.border-gray-300.rounded-lg
                             [:thead.bg-gray-50
                              [:tr
                               [:th.px-3.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Case ID"]
                               [:th.px-3.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Name"]
                               [:th.px-3.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Description"]
                               [:th.px-3.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Folder"]
                               [:th.px-3.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Tags"]
                               [:th.px-3.py-2.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider.border-b "Actions"]]]
                             [:tbody.bg-white.divide-y.divide-gray-200
                              (map-indexed
                                (fn [case-idx case-item]
                                  [:tr {:key case-idx :class (if (even? case-idx) "bg-gray-50" "bg-white")}
                                   [:td.px-3.py-2.text-sm.text-gray-900.border-b (:id case-item)]
                                   [:td.px-3.py-2.text-sm.text-gray-900.border-b (:name case-item)]
                                   [:td.px-3.py-2.text-sm.text-gray-900.border-b (if (> (count (:description case-item)) 80)
                                                                                     (str (subs (:description case-item) 0 80) "...")
                                                                                     (:description case-item))]
                                   [:td.px-3.py-2.text-sm.text-gray-900.border-b (extract-folder-from-tags (:tags case-item))]
                                   [:td.px-3.py-2.text-sm.text-gray-900.border-b (let [filtered-tags (filter-non-folder-tags (:tags case-item))]
                                                                                     (if (seq filtered-tags)
                                                                                       (clojure.string/join ", " filtered-tags)
                                                                                       ""))]
                                   [:td.px-3.py-2.text-sm.border-b
                                    [::c/button
                                     {:size :sm
                                      :variant :danger
                                      :data-on-click (weave/handler
                                                       (reset! delete-confirmation {:type :case 
                                                                                    :case-id (:id case-item)
                                                                                    :case-name (:name case-item)})
                                                       (weave/push-html! (login-view)))}
                                     "Delete"]]])
                                project-cases)]]])]))
                    projects))
]
               [:div.p-8.text-center.text-gray-500.bg-gray-50.rounded-lg
                [:p.text-lg "Loading cases..."]
                [:p.text-sm.mt-2 "Please wait while we fetch cases from all projects"]])])
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