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

(defnc confirmation-dialog
  [{:keys [show message on-confirm on-cancel position]}]
  (let [[input-value set-input-value] (hooks/use-state "")]
    (when show
    (d/div {:style {:position "fixed"
                    :top 0
                    :left 0
                    :right 0
                    :bottom 0
                    :background-color "rgba(0,0,0,0.5)"
                    :display "flex"
                    :align-items "center"
                    :justify-content "center"
                    :z-index 1000}}
      (d/div {:style {:background-color "white"
                      :border-radius "8px"
                      :box-shadow "0 4px 6px rgba(0,0,0,0.1)"
                      :padding "20px"
                      :min-width "300px"
                      :max-width "400px"
                      :display "flex"
                      :flex-direction "column"}}
        (d/div {:style {:margin-bottom "20px"
                        :font-size "16px"
                        :line-height "1.5"
                        :color "#333"}}
               message)
        (d/div {:style {:margin-bottom "40px"
                        :margin-top "10px"}}
          (d/input {:type "text"
                    :value input-value
                    :onChange (fn [e] (set-input-value (.. e -target -value)))
                    :placeholder "Enter deLEte to confirm"
                    :style {:width "100%"
                            :padding "8px"
                            :border "1px solid #ccc"
                            :border-radius "4px"
                            :box-sizing "border-box"
                            :font-size "14px"
                            :display "block"}}))
        (d/div {:style {:display "flex"
                        :justify-content "flex-end"
                        :gap "10px"
                        :margin-top "20px"
                        :padding-top "10px"}}
          (d/button {:style {:padding "8px 16px"
                             :border "1px solid #ccc"
                             :background-color "white"
                             :border-radius "4px"
                             :cursor "pointer"
                             :font-size "14px"}
                     :onClick on-cancel}
                    "No")
          (d/button {:style {:padding "8px 16px"
                             :border "none"
                             :background-color (if (= input-value "deLEte") "#dc3545" "#ccc")
                             :color "white"
                             :border-radius "4px"
                             :cursor (if (= input-value "deLEte") "pointer" "not-allowed")
                             :font-size "14px"}
                     :disabled (not= input-value "deLEte")
                     :onClick (fn []
                                (when (= input-value "deLEte")
                                  (set-input-value "")
                                  (on-confirm)))}
                    "Yes")))))))

(defn create-user [email password user-data]
  (let [auth-header (str "Basic " (js/btoa (str email ":" password)))
        headers (js/Headers.)]
    ;(set! (.-Authorization headers) auth-header)
    (.set headers "Authorization" auth-header)
    (.set headers "Content-Type" "application/json")
    (js/console.error "password:::")
    (js/console.error (:password user-data))
    (js/console.error (js/JSON.stringify (clj->js user-data)) )
    (-> (js/fetch "/api/users/create" 
                  (js/Object. #js {:method "POST"
                                   :headers headers
                                   :body (js/JSON.stringify (clj->js user-data))}))
        (.then #(.json %))
        (.catch #(js/console.error "Error creating user:" %)))))

(defn delete-user [email password user-id]
  (let [auth-header (str "Basic " (js/btoa (str email ":" password)))
        headers (js/Object.)]
    (set! (.-Authorization headers) auth-header)
    (set! (.-Content-Type headers) "application/json")
    (-> (js/fetch (str "/api/users/delete?id=" user-id) 
                  (js/Object. #js {:method "POST"
                                   :headers headers
                                   }))
        (.then #(.json %))
        (.catch #(js/console.error "Error deleting user:" %)))))

(defnc users-table
  [{:keys [users email password on-user-deleted]}]
  (let [[show-dialog set-show-dialog] (hooks/use-state false)
        [user-to-delete set-user-to-delete] (hooks/use-state nil)
        [show-create-form set-show-create-form] (hooks/use-state false)
        [new-user-email set-new-user-email] (hooks/use-state "")
        [new-user-first-name set-new-user-first-name] (hooks/use-state "")
        [new-user-last-name set-new-user-last-name] (hooks/use-state "")
        [new-user-password set-new-user-password] (hooks/use-state "")
        [create-user-error set-create-user-error] (hooks/use-state nil)
        [show-password set-show-password] (hooks/use-state false)]
    (println users)
    (<>
      ($ confirmation-dialog {:show show-dialog
                              :message (when user-to-delete
                                         (str "Please confirm delete user " (.-email user-to-delete) " by entering word deLEte(case sensitive) below, and click \"Yes\" button at bottom of the dialog."))
                              :on-confirm (fn []
                                            (set-show-dialog false)
                                            (when user-to-delete
                                              (-> (delete-user email password (.-id user-to-delete))
                                                  (.then (fn [result]
                                                           (js/console.log "User deleted:" result)
                                                           (set-user-to-delete nil)
                                                           (when on-user-deleted
                                                             (on-user-deleted)))))))
                              :on-cancel (fn []
                                           (set-show-dialog false)
                                           (set-user-to-delete nil))})
      (d/div {:style {:margin-top "20px"}}
        (d/div {:style {:display "flex"
                        :justify-content "space-between"
                        :align-items "center"
                        :margin-bottom "15px"}}
          (d/h3 {:style {:margin 0}} "Users")
          (d/button {:style {:padding "8px 16px"
                             :background-color "#28a745"
                             :color "white"
                             :border "none"
                             :border-radius "4px"
                             :cursor "pointer"
                             :font-size "14px"}
                     :onClick (fn []
                                (set-show-create-form (not show-create-form)))}
                    "Create User"))
        
        (when show-create-form
          (d/div {:style {:background-color "#f8f9fa"
                          :border "1px solid #dee2e6"
                          :border-radius "4px"
                          :padding "20px"
                          :margin-bottom "20px"}}
            (d/h4 {:style {:margin-top 0
                           :margin-bottom "15px"}} "Create New User")
            (when create-user-error
              (d/div {:style {:color "#dc3545"
                              :font-size "14px"
                              :margin-bottom "15px"
                              :padding "8px"
                              :background-color "#f8d7da"
                              :border "1px solid #f5c6cb"
                              :border-radius "4px"}}
                     create-user-error))
            (d/div {:style {:display "grid"
                            :grid-template-columns "1fr 1fr"
                            :gap "15px"
                            :margin-bottom "15px"}}
              (d/div
                (d/label {:style {:display "block"
                                  :margin-bottom "5px"
                                  :font-weight "bold"}} "Email:")
                (d/input {:type "email"
                          :value new-user-email
                          :onChange (fn [e] (set-new-user-email (.. e -target -value)))
                          :autoComplete "off"
                          :style {:width "100%"
                                  :padding "8px"
                                  :border "1px solid #ccc"
                                  :border-radius "4px"
                                  :box-sizing "border-box"}}))
              (d/div
                (d/label {:style {:display "block"
                                  :margin-bottom "5px"
                                  :font-weight "bold"}} "First Name:")
                (d/input {:type "text"
                          :value new-user-first-name
                          :onChange (fn [e] (set-new-user-first-name (.. e -target -value)))
                          :autoComplete "off"
                          :style {:width "100%"
                                  :padding "8px"
                                  :border "1px solid #ccc"
                                  :border-radius "4px"
                                  :box-sizing "border-box"}})))
            (d/div {:style {:display "grid"
                            :grid-template-columns "1fr 1fr"
                            :gap "15px"
                            :margin-bottom "20px"}}
              (d/div
                (d/label {:style {:display "block"
                                  :margin-bottom "5px"
                                  :font-weight "bold"}} "Last Name:")
                (d/input {:type "text"
                          :value new-user-last-name
                          :onChange (fn [e] (set-new-user-last-name (.. e -target -value)))
                          :autoComplete "off"
                          :style {:width "100%"
                                  :padding "8px"
                                  :border "1px solid #ccc"
                                  :border-radius "4px"
                                  :box-sizing "border-box"}}))
              (d/div
                (d/label {:style {:display "block"
                                  :margin-bottom "5px"
                                  :font-weight "bold"}} "Password:")
                (d/div {:style {:position "relative"
                                :display "flex"
                                :align-items "center"}}
                  (d/input {:type (if show-password "text" "password")
                            :value new-user-password
                            :onChange (fn [e] (set-new-user-password (.. e -target -value)))
                            :autoComplete "new-password"
                            :style {:width "100%"
                                    :padding "8px 40px 8px 8px"
                                    :border "1px solid #ccc"
                                    :border-radius "4px"
                                    :box-sizing "border-box"}})
                  (d/button {:type "button"
                             :style {:position "absolute"
                                     :right "8px"
                                     :background "none"
                                     :border "none"
                                     :cursor "pointer"
                                     :padding "4px"
                                     :display "flex"
                                     :align-items "center"
                                     :justify-content "center"
                                     :font-size "16px"
                                     :color "#6c757d"}
                             :onClick (fn [e]
                                        (.preventDefault e)
                                        (set-show-password (not show-password)))}
                            (if show-password "👁️" "🙈")))))
            (d/div {:style {:display "flex"
                            :gap "10px"}}
              (d/button {:style {:padding "8px 16px"
                                 :background-color "#007bff"
                                 :color "white"
                                 :border "none"
                                 :border-radius "4px"
                                 :cursor "pointer"
                                 :font-size "14px"}
                         :onClick (fn []
                                    (let [user-data {:email new-user-email
                                                     :first-name new-user-first-name
                                                     :last-name new-user-last-name
                                                     :password new-user-password}]
                                      (set-create-user-error nil)
                                      (-> (create-user email password user-data)
                                          (.then (fn [result]
                                                   (if (.-error result)
                                                     (do
                                                       (js/console.error "Error creating user:" (.-error result))
                                                       (set-create-user-error (str "Error: " (.-error result))))
                                                     (do
                                                       (js/console.log "User created successfully:" result)
                                                       (set-new-user-email "")
                                                       (set-new-user-first-name "")
                                                       (set-new-user-last-name "")
                                                       (set-new-user-password "")
                                                       (set-create-user-error nil)
                                                       (set-show-create-form false)
                                                       (when on-user-deleted
                                                         (on-user-deleted))))))
                                          (.catch (fn [error]
                                                    (js/console.error "Error creating user:" error)
                                                    (set-create-user-error (str "Network error: " error)))))))}
                        "Create")
              (d/button {:style {:padding "8px 16px"
                                 :background-color "#6c757d"
                                 :color "white"
                                 :border "none"
                                 :border-radius "4px"
                                 :cursor "pointer"
                                 :font-size "14px"}
                         :onClick (fn []
                                    (set-new-user-email "")
                                    (set-new-user-first-name "")
                                    (set-new-user-last-name "")
                                    (set-new-user-password "")
                                    (set-create-user-error nil)
                                    (set-show-password false)
                                    (set-show-create-form false))}
                        "Cancel")))))
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
                           :text-align "left"}} "Last Name")
            (d/th {:style {:border "1px solid #ddd"
                           :padding "8px"
                           :background-color "#f2f2f2"
                           :text-align "left"}} "Action")))
        (d/tbody
          (map-indexed
            (fn [idx user] (d/tr {:key idx}
              (d/td {:style {:border "1px solid #ddd"
                             :padding "8px"}} (.-email user))
              (d/td {:style {:border "1px solid #ddd"
                             :padding "8px"}} (aget user "first-name"))
              (d/td {:style {:border "1px solid #ddd"
                             :padding "8px"}} (aget user "last-name"))
              (d/td {:style {:border "1px solid #ddd"
                             :padding "8px"}}
                (d/button {:style {:background-color "#dc3545"
                                   :color "white"
                                   :border "none"
                                   :padding "4px 8px"
                                   :border-radius "4px"
                                   :cursor "pointer"
                                   :font-size "12px"}
                           :onClick (fn [e]
                                      (.preventDefault e)
                                      (set-user-to-delete user)
                                      (set-show-dialog true))} 
                          "Delete"))))
                       users)))))))

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
                 "Users" ($ users-table {:users users
                                          :email email
                                          :password password
                                          :on-user-deleted (fn []
                                                             (-> (fetch-users email password)
                                                                 (.then #(set-users %))))})
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