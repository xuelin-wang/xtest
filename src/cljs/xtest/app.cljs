(ns xtest.app
  (:require [helix.core :refer [defnc $ <>]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            ["react-dom/client" :as rdom]
           ))

(defnc login-form
  [{:keys [email password on-email-change on-password-change on-login]}]
  (d/div {:style {:max-width "400px"
                  :margin "50px auto"
                  :padding "20px"
                  :border "1px solid #ddd"
                  :border-radius "8px"
                  :box-shadow "0 2px 4px rgba(0,0,0,0.1)"}}
    (d/h2 {:style {:text-align "center" :margin-bottom "30px"}} "Login")
    (d/form {:onSubmit (fn [e]
                         (.preventDefault e)
                         (on-login))}
      (d/div {:style {:margin-bottom "15px"}}
        (d/label {:style {:display "block" :margin-bottom "5px"}} "Email:")
        (d/input {:type "email"
                  :value email
                  :onChange (fn [e] (on-email-change (.. e -target -value)))
                  :style {:width "100%"
                          :padding "8px"
                          :border "1px solid #ccc"
                          :border-radius "4px"
                          :box-sizing "border-box"}}))
      (d/div {:style {:margin-bottom "20px"}}
        (d/label {:style {:display "block" :margin-bottom "5px"}} "Password:")
        (d/input {:type "password"
                  :value password
                  :onChange (fn [e] (on-password-change (.. e -target -value)))
                  :style {:width "100%"
                          :padding "8px"
                          :border "1px solid #ccc"
                          :border-radius "4px"
                          :box-sizing "border-box"}}))
      (d/button {:type "submit"
                 :style {:width "100%"
                         :padding "10px"
                         :background-color "#007bff"
                         :color "white"
                         :border "none"
                         :border-radius "4px"
                         :cursor "pointer"
                         :font-size "16px"}}
                "Login"))))

(defnc users-table
  [{:keys [users]}]
  (println users)
  (d/div {:style {:margin-top "20px"}}
    (d/h3 "Users")
    (if (empty? users)
      (d/p "Loading users...")
      (d/table {:style {:border-collapse "collapse"
                        :width "100%"
                        :border "1px solid #ddd"}}
        (d/thead
          (d/tr
            (d/th {:style {:border "1px solid #ddd"
                           :padding "8px"
                           :background-color "#f2f2f2"
                           :text-align "left"}} "Email")
            (d/th {:style {:border "1px solid #ddd"
                           :padding "8px"
                           :background-color "#f2f2f2"
                           :text-align "left"}} "First Name")
            (d/th {:style {:border "1px solid #ddd"
                           :padding "8px"
                           :background-color "#f2f2f2"
                           :text-align "left"}} "Last Name")))
        (d/tbody
          (map-indexed
            (fn [idx user] (d/tr {:key idx}
              (d/td {:style {:border "1px solid #ddd"
                             :padding "8px"}} (.-email user))
              (d/td {:style {:border "1px solid #ddd"
                             :padding "8px"}} (aget user "first-name"))
              (d/td {:style {:border "1px solid #ddd"
                             :padding "8px"}} (aget user "last-name"))))
                       users))))))

(defn fetch-users [email password]
  (let [auth-header (str "Basic " (js/btoa (str email ":" password)))
        headers (js/Object.)]
    (set! (.-Authorization headers) auth-header)
    (set! (.-Content-Type headers) "application/json")
    (-> (js/fetch "/api/users/get" 
                  (js/Object. #js {:method "GET"
                                   :headers headers}))
        (.then #(.json %))
        (.catch #(js/console.error "Error fetching users:" %)))))

(defnc main-app
  [{:keys [is-logged-in set-is-logged-in email password]}]
  (let [[selected-link set-selected-link] (hooks/use-state nil)
        [users set-users] (hooks/use-state [])]
    (<>
      ;; Top bar with clickable links
      (d/div {:style {:padding "10px"
                      :border-bottom "1px solid #ccc"
                      :margin-bottom "20px"}}
       (let [links
             (into [] 
                   (map-indexed
               (fn [idx title]
                 (d/a {:key idx
                       :href "#"
                       :style {:margin-right "20px"
                               :text-decoration "none"
                               :color "#007bff"
                               :cursor "pointer"}
                       :onClick (fn [e]
                                  (.preventDefault e)
                                  (set-selected-link title)
                                  (when (= title "Users")
                                    (-> (fetch-users email password)
                                        (.then #(set-users %)))))}  
                      title)
                 )
               ["Projects" "Cases" "Users"]))
             logout #(set-is-logged-in false)
             logout-button
             (d/button {:key 100 :on-click logout}
                       "Logout")]
         (conj links logout-button)
         )
        )
      
      ;; Main content
      (when selected-link
        (d/div {:style {:margin-top "20px"
                        :padding "10px"
                        :background-color "#f8f9fa"
                        :border "1px solid #dee2e6"
                        :border-radius "4px"}}
               (case selected-link
                 "Users" ($ users-table {:users users})
                 (d/p (str "You clicked: " selected-link))))))))

(defnc xtest-app
  []
  (let [[is-logged-in set-is-logged-in] (hooks/use-state false)
        [email set-email] (hooks/use-state "")
        [password set-password] (hooks/use-state "")
        [logged-in-email set-logged-in-email] (hooks/use-state "")
        [logged-in-password set-logged-in-password] (hooks/use-state "")]
    
    (if is-logged-in
      ;; Show main app if logged in
      ($ main-app {:is-logged-in is-logged-in 
                   :set-is-logged-in set-is-logged-in
                   :email logged-in-email
                   :password logged-in-password})
      
      ;; Show login form if not logged in
      ($ login-form {:email email
                     :password password
                     :on-email-change set-email
                     :on-password-change set-password
                     :on-login (fn []
                                 ;; Simple login logic - for demo purposes
                                 ;; In real app, this would make API call
                                 (if (and (not= email "") (not= password ""))
                                   (do
                                     ;; Store credentials for API calls
                                     (set-logged-in-email email)
                                     (set-logged-in-password password)
                                     (set-is-logged-in true)
                                     ;; Clear form on successful login
                                     (set-email "")
                                     (set-password ""))
                                   ;; Login failed - stay on login screen
                                   ;; Could add error message here
                                   (js/alert "Login failed. Please enter valid email and password.")))}))))

(defn ^:export init []
  (let [root (rdom/createRoot (js/document.getElementById "app"))]
    (.render root ($ xtest-app))))