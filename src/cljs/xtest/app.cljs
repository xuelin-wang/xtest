(ns xtest.app
  (:require [helix.core :refer [defnc $ <>]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            ["react-dom/client" :as rdom]
           ))

(defnc login-form
  [{:keys [email password on-email-change on-password-change on-login error-message]}]
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
      (when error-message
        (d/div {:style {:color "#dc3545"
                        :font-size "14px"
                        :margin-bottom "15px"
                        :padding "8px"
                        :background-color "#f8d7da"
                        :border "1px solid #f5c6cb"
                        :border-radius "4px"}}
               error-message))
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

(defn create-project [email password project-data]
  (let [auth-header (str "Basic " (js/btoa (str email ":" password)))
        headers (js/Headers.)]
    (.set headers "Authorization" auth-header)
    (.set headers "Content-Type" "application/json")
    (-> (js/fetch "/api/projects/create" 
                  (js/Object. #js {:method "POST"
                                   :headers headers
                                   :body (js/JSON.stringify (clj->js project-data))}))
        (.then #(.json %))
        (.catch #(js/console.error "Error creating project:" %)))))

(defn create-case [email password case-data]
  (let [auth-header (str "Basic " (js/btoa (str email ":" password)))
        headers (js/Headers.)]
    (.set headers "Authorization" auth-header)
    (.set headers "Content-Type" "application/json")
    (-> (js/fetch "/api/cases/create" 
                  (js/Object. #js {:method "POST"
                                   :headers headers
                                   :body (js/JSON.stringify (clj->js case-data))}))
        (.then #(.json %))
        (.catch #(js/console.error "Error creating case:" %)))))

(defn login-user [email password]
  (let [headers (js/Headers.)]
    (.set headers "Content-Type" "application/json")
    (-> (js/fetch "/api/users/login" 
                  (js/Object. #js {:method "POST"
                                   :headers headers
                                   :body (js/JSON.stringify (clj->js {:email email :password password}))}))
        (.then #(.json %))
        (.catch #(js/console.error "Error logging in:" %)))))

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

(defn delete-project [email password project-id]
  (let [auth-header (str "Basic " (js/btoa (str email ":" password)))
        headers (js/Object.)]
    (set! (.-Authorization headers) auth-header)
    (set! (.-Content-Type headers) "application/json")
    (-> (js/fetch (str "/api/projects/delete?id=" project-id) 
                  (js/Object. #js {:method "POST"
                                   :headers headers
                                   }))
        (.then #(.json %))
        (.catch #(js/console.error "Error deleting project:" %)))))

(defn delete-case [email password case-id]
  (let [auth-header (str "Basic " (js/btoa (str email ":" password)))
        headers (js/Object.)]
    (set! (.-Authorization headers) auth-header)
    (set! (.-Content-Type headers) "application/json")
    (-> (js/fetch (str "/api/case/delete?id=" case-id) 
                  (js/Object. #js {:method "POST"
                                   :headers headers
                                   }))
        (.then #(.json %))
        (.catch #(js/console.error "Error deleting case:" %)))))

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

(defnc projects-table
  [{:keys [projects email password on-project-deleted]}]
  (let [[show-dialog set-show-dialog] (hooks/use-state false)
        [project-to-delete set-project-to-delete] (hooks/use-state nil)
        [show-create-form set-show-create-form] (hooks/use-state false)
        [new-project-id set-new-project-id] (hooks/use-state "")
        [new-project-name set-new-project-name] (hooks/use-state "")
        [create-project-error set-create-project-error] (hooks/use-state nil)]
    (<>
      ($ confirmation-dialog {:show show-dialog
                              :message (when project-to-delete
                                         (str "Please confirm delete project " (.-name project-to-delete) " by entering word deLEte(case sensitive) below, and click \"Yes\" button at bottom of the dialog."))
                              :on-confirm (fn []
                                            (set-show-dialog false)
                                            (when project-to-delete
                                              (-> (delete-project email password (.-id project-to-delete))
                                                  (.then (fn [result]
                                                           (js/console.log "Project deleted:" result)
                                                           (set-project-to-delete nil)
                                                           (when on-project-deleted
                                                             (on-project-deleted)))))))
                              :on-cancel (fn []
                                           (set-show-dialog false)
                                           (set-project-to-delete nil))})
      (d/div {:style {:margin-top "20px"}}
        (d/div {:style {:display "flex"
                        :justify-content "space-between"
                        :align-items "center"
                        :margin-bottom "15px"}}
          (d/h3 {:style {:margin 0}} "Projects")
          (d/button {:style {:padding "8px 16px"
                             :background-color "#28a745"
                             :color "white"
                             :border "none"
                             :border-radius "4px"
                             :cursor "pointer"
                             :font-size "14px"}
                     :onClick (fn []
                                (set-show-create-form (not show-create-form)))}
                    "Create Project"))
        
        (when show-create-form
          (d/div {:style {:background-color "#f8f9fa"
                          :border "1px solid #dee2e6"
                          :border-radius "4px"
                          :padding "20px"
                          :margin-bottom "20px"}}
            (d/h4 {:style {:margin-top 0
                           :margin-bottom "15px"}} "Create New Project")
            (when create-project-error
              (d/div {:style {:color "#dc3545"
                              :font-size "14px"
                              :margin-bottom "15px"
                              :padding "8px"
                              :background-color "#f8d7da"
                              :border "1px solid #f5c6cb"
                              :border-radius "4px"}}
                     create-project-error))
            (d/div {:style {:display "grid"
                            :grid-template-columns "1fr 1fr"
                            :gap "15px"
                            :margin-bottom "15px"}}
              (d/div
                (d/label {:style {:display "block"
                                  :margin-bottom "5px"
                                  :font-weight "bold"}} "ID:")
                (d/input {:type "text"
                          :value new-project-id
                          :onChange (fn [e] (set-new-project-id (.. e -target -value)))
                          :autoComplete "off"
                          :style {:width "100%"
                                  :padding "8px"
                                  :border "1px solid #ccc"
                                  :border-radius "4px"
                                  :box-sizing "border-box"}}))
              (d/div
                (d/label {:style {:display "block"
                                  :margin-bottom "5px"
                                  :font-weight "bold"}} "Name:")
                (d/input {:type "text"
                          :value new-project-name
                          :onChange (fn [e] (set-new-project-name (.. e -target -value)))
                          :autoComplete "off"
                          :style {:width "100%"
                                  :padding "8px"
                                  :border "1px solid #ccc"
                                  :border-radius "4px"
                                  :box-sizing "border-box"}})))
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
                                    (let [project-data {:id new-project-id
                                                        :name new-project-name}]
                                      (set-create-project-error nil)
                                      (-> (create-project email password project-data)
                                          (.then (fn [result]
                                                   (if (.-error result)
                                                     (do
                                                       (js/console.error "Error creating project:" (.-error result))
                                                       (set-create-project-error (str "Error: " (.-error result))))
                                                     (do
                                                       (js/console.log "Project created successfully:" result)
                                                       (set-new-project-id "")
                                                       (set-new-project-name "")
                                                       (set-create-project-error nil)
                                                       (set-show-create-form false)
                                                       (when on-project-deleted
                                                         (on-project-deleted))))))
                                          (.catch (fn [error]
                                                    (js/console.error "Error creating project:" error)
                                                    (set-create-project-error (str "Network error: " error)))))))}
                        "Create")
              (d/button {:style {:padding "8px 16px"
                                 :background-color "#6c757d"
                                 :color "white"
                                 :border "none"
                                 :border-radius "4px"
                                 :cursor "pointer"
                                 :font-size "14px"}
                         :onClick (fn []
                                    (set-new-project-id "")
                                    (set-new-project-name "")
                                    (set-create-project-error nil)
                                    (set-show-create-form false))}
                        "Cancel"))))
        (if (empty? projects)
          (d/p "Loading projects...")
          (d/table {:style {:border-collapse "collapse"
                            :width "100%"
                            :border "1px solid #ddd"}}
            (d/thead
              (d/tr
                (d/th {:style {:border "1px solid #ddd"
                               :padding "8px"
                               :background-color "#f2f2f2"
                               :text-align "left"}} "ID")
                (d/th {:style {:border "1px solid #ddd"
                               :padding "8px"
                               :background-color "#f2f2f2"
                               :text-align "left"}} "Name")
                (d/th {:style {:border "1px solid #ddd"
                               :padding "8px"
                               :background-color "#f2f2f2"
                               :text-align "left"}} "Action")))
            (d/tbody
              (map-indexed
                (fn [idx project] (d/tr {:key idx}
                  (d/td {:style {:border "1px solid #ddd"
                                 :padding "8px"}} (.-id project))
                  (d/td {:style {:border "1px solid #ddd"
                                 :padding "8px"}} (.-name project))
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
                                          (set-project-to-delete project)
                                          (set-show-dialog true))} 
                              "Delete"))))
                           projects))))))))

(defnc cases-table
  [{:keys [cases projects email password on-project-changed loading selected-project-id on-case-deleted]}]
  (let [sorted-projects (sort-by #(.-name %) projects)
        [show-dialog set-show-dialog] (hooks/use-state false)
        [case-to-delete set-case-to-delete] (hooks/use-state nil)
        [show-create-form set-show-create-form] (hooks/use-state false)
        [new-case-id set-new-case-id] (hooks/use-state "")
        [new-case-name set-new-case-name] (hooks/use-state "")
        [new-case-description set-new-case-description] (hooks/use-state "")
        [new-case-steps set-new-case-steps] (hooks/use-state "")
        [new-case-tags set-new-case-tags] (hooks/use-state "")
        [create-case-error set-create-case-error] (hooks/use-state nil)]
    (<>
      ($ confirmation-dialog {:show show-dialog
                              :message (when case-to-delete
                                         (str "Please confirm delete case " (aget case-to-delete "name") " by entering word deLEte(case sensitive) below, and click \"Yes\" button at bottom of the dialog."))
                              :on-confirm (fn []
                                            (set-show-dialog false)
                                            (when case-to-delete
                                              (-> (delete-case email password (aget case-to-delete "id"))
                                                  (.then (fn [result]
                                                           (js/console.log "Case deleted:" result)
                                                           (set-case-to-delete nil)
                                                           (when on-case-deleted
                                                             (on-case-deleted)))))))
                              :on-cancel (fn []
                                           (set-show-dialog false)
                                           (set-case-to-delete nil))})
      (d/div {:style {:margin-top "20px"}}
        (d/div {:style {:display "flex"
                        :justify-content "space-between"
                        :align-items "center"
                        :margin-bottom "15px"}}
          (d/h3 {:style {:margin 0}} "Cases")
          (when (not-empty projects)
            (d/div {:style {:display "flex"
                            :align-items "center"
                            :gap "10px"}}
              (d/label {:style {:font-weight "bold"}} "Project:")
              (d/select {:value (or selected-project-id "")
                         :onChange (fn [e]
                                     (let [new-project-id (.. e -target -value)]
                                       (when on-project-changed
                                         (on-project-changed new-project-id))))
                         :style {:padding "4px 8px"
                                 :border "1px solid #ccc"
                                 :border-radius "4px"
                                 :font-size "14px"}}
                (map (fn [project]
                       (d/option {:key (.-id project)
                                  :value (.-id project)}
                                 (.-name project)))
                     sorted-projects))
              (d/button {:style {:padding "8px 16px"
                                 :background-color "#28a745"
                                 :color "white"
                                 :border "none"
                                 :border-radius "4px"
                                 :cursor "pointer"
                                 :font-size "14px"
                                 :margin-left "20px"}
                         :onClick (fn []
                                    (set-show-create-form (not show-create-form)))}
                        "Create New Case"))))
        
        (when show-create-form
          (d/div {:style {:background-color "#f8f9fa"
                          :border "1px solid #dee2e6"
                          :border-radius "4px"
                          :padding "20px"
                          :margin-bottom "20px"}}
            (d/h4 {:style {:margin-top 0
                           :margin-bottom "15px"}} "Create New Case")
            (when create-case-error
              (d/div {:style {:color "#dc3545"
                              :font-size "14px"
                              :margin-bottom "15px"
                              :padding "8px"
                              :background-color "#f8d7da"
                              :border "1px solid #f5c6cb"
                              :border-radius "4px"}}
                     create-case-error))
            (d/div {:style {:display "grid"
                            :grid-template-columns "1fr 1fr"
                            :gap "15px"
                            :margin-bottom "15px"}}
              (d/div
                (d/label {:style {:display "block"
                                  :margin-bottom "5px"
                                  :font-weight "bold"}} "ID:")
                (d/input {:type "text"
                          :value new-case-id
                          :onChange (fn [e] (set-new-case-id (.. e -target -value)))
                          :autoComplete "off"
                          :style {:width "100%"
                                  :padding "8px"
                                  :border "1px solid #ccc"
                                  :border-radius "4px"
                                  :box-sizing "border-box"}}))
              (d/div
                (d/label {:style {:display "block"
                                  :margin-bottom "5px"
                                  :font-weight "bold"}} "Name:")
                (d/input {:type "text"
                          :value new-case-name
                          :onChange (fn [e] (set-new-case-name (.. e -target -value)))
                          :autoComplete "off"
                          :style {:width "100%"
                                  :padding "8px"
                                  :border "1px solid #ccc"
                                  :border-radius "4px"
                                  :box-sizing "border-box"}})))
            (d/div {:style {:margin-bottom "15px"}}
              (d/label {:style {:display "block"
                                :margin-bottom "5px"
                                :font-weight "bold"}} "Description:")
              (d/textarea {:value new-case-description
                           :onChange (fn [e] (set-new-case-description (.. e -target -value)))
                           :autoComplete "off"
                           :style {:width "100%"
                                   :padding "8px"
                                   :border "1px solid #ccc"
                                   :border-radius "4px"
                                   :box-sizing "border-box"
                                   :rows 3}}))
            (d/div {:style {:margin-bottom "15px"}}
              (d/label {:style {:display "block"
                                :margin-bottom "5px"
                                :font-weight "bold"}} "Steps:")
              (d/textarea {:value new-case-steps
                           :onChange (fn [e] (set-new-case-steps (.. e -target -value)))
                           :autoComplete "off"
                           :style {:width "100%"
                                   :padding "8px"
                                   :border "1px solid #ccc"
                                   :border-radius "4px"
                                   :box-sizing "border-box"
                                   :rows 4}}))
            (d/div {:style {:margin-bottom "20px"}}
              (d/label {:style {:display "block"
                                :margin-bottom "5px"
                                :font-weight "bold"}} "Tags:")
              (d/input {:type "text"
                        :value new-case-tags
                        :onChange (fn [e] (set-new-case-tags (.. e -target -value)))
                        :autoComplete "off"
                        :placeholder "Enter tags separated by commas"
                        :style {:width "100%"
                                :padding "8px"
                                :border "1px solid #ccc"
                                :border-radius "4px"
                                :box-sizing "border-box"}}))
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
                                    (let [case-data {:id new-case-id
                                                     :name new-case-name
                                                     :description new-case-description
                                                     :steps new-case-steps
                                                     :tags new-case-tags
                                                     :project-id selected-project-id}]
                                      (set-create-case-error nil)
                                      (-> (create-case email password case-data)
                                          (.then (fn [result]
                                                   (if (.-error result)
                                                     (do
                                                       (js/console.error "Error creating case:" (.-error result))
                                                       (set-create-case-error (str "Error: " (.-error result))))
                                                     (do
                                                       (js/console.log "Case created successfully:" result)
                                                       (set-new-case-id "")
                                                       (set-new-case-name "")
                                                       (set-new-case-description "")
                                                       (set-new-case-steps "")
                                                       (set-new-case-tags "")
                                                       (set-create-case-error nil)
                                                       (set-show-create-form false)
                                                       (when on-case-deleted
                                                         (on-case-deleted))))))
                                          (.catch (fn [error]
                                                    (js/console.error "Error creating case:" error)
                                                    (set-create-case-error (str "Network error: " error)))))))}
                        "Create")
              (d/button {:style {:padding "8px 16px"
                                 :background-color "#6c757d"
                                 :color "white"
                                 :border "none"
                                 :border-radius "4px"
                                 :cursor "pointer"
                                 :font-size "14px"}
                         :onClick (fn []
                                    (set-new-case-id "")
                                    (set-new-case-name "")
                                    (set-new-case-description "")
                                    (set-new-case-steps "")
                                    (set-new-case-tags "")
                                    (set-create-case-error nil)
                                    (set-show-create-form false))}
                        "Cancel"))))
      (cond
        loading (d/p "Loading cases...")
        (empty? cases) (d/p "No cases found for the selected project.")
        :else (d/table {:style {:border-collapse "collapse"
                                :width "100%"
                                :border "1px solid #ddd"}}
                (d/thead
                  (d/tr
                    (d/th {:style {:border "1px solid #ddd"
                                   :padding "8px"
                                   :background-color "#f2f2f2"
                                   :text-align "left"}} "Case ID")
                    (d/th {:style {:border "1px solid #ddd"
                                   :padding "8px"
                                   :background-color "#f2f2f2"
                                   :text-align "left"}} "Case Name")
                    (d/th {:style {:border "1px solid #ddd"
                                   :padding "8px"
                                   :background-color "#f2f2f2"
                                   :text-align "left"}} "Action")))
                (d/tbody
                  (map-indexed
                    (fn [idx case] (d/tr {:key idx}
                      (d/td {:style {:border "1px solid #ddd"
                                     :padding "8px"}} (aget case "id"))
                      (d/td {:style {:border "1px solid #ddd"
                                     :padding "8px"}} (aget case "name"))
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
                                              (set-case-to-delete case)
                                              (set-show-dialog true))} 
                                  "Delete"))))
                               cases))))))))

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

(defn fetch-projects [email password]
  (let [auth-header (str "Basic " (js/btoa (str email ":" password)))
        headers (js/Object.)]
    (set! (.-Authorization headers) auth-header)
    (set! (.-Content-Type headers) "application/json")
    (-> (js/fetch "/api/projects/get" 
                  (js/Object. #js {:method "GET"
                                   :headers headers}))
        (.then #(.json %))
        (.catch #(js/console.error "Error fetching projects:" %)))))

(defn fetch-cases [email password & [project-ids]]
  (let [auth-header (str "Basic " (js/btoa (str email ":" password)))
        headers (js/Object.)
        url (if project-ids
              (str "/api/cases/get?project-ids=" project-ids)
              "/api/cases/get")]
    (set! (.-Authorization headers) auth-header)
    (set! (.-Content-Type headers) "application/json")
    (-> (js/fetch url 
                  (js/Object. #js {:method "GET"
                                   :headers headers}))
        (.then #(.json %))
        (.catch #(js/console.error "Error fetching cases:" %)))))

(defnc main-app
  [{:keys [is-logged-in set-is-logged-in email password]}]
  (let [[selected-link set-selected-link] (hooks/use-state nil)
        [users set-users] (hooks/use-state [])
        [projects set-projects] (hooks/use-state [])
        [cases set-cases] (hooks/use-state [])
        [cases-loading set-cases-loading] (hooks/use-state false)
        [selected-project-id set-selected-project-id] (hooks/use-state nil)]
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
                               :cursor "pointer"
                               :padding-bottom "5px"
                               :border-bottom (if (= selected-link title) 
                                                "2px solid red" 
                                                "2px solid transparent")}
                       :onClick (fn [e]
                                  (.preventDefault e)
                                  (set-selected-link title)
                                  (when (= title "Users")
                                    (-> (fetch-users email password)
                                        (.then #(set-users %))))
                                  (when (= title "Projects")
                                    (-> (fetch-projects email password)
                                        (.then #(set-projects %))))
                                  (when (= title "Cases")
                                    (set-cases-loading true)
                                    (-> (fetch-projects email password)
                                        (.then (fn [fetched-projects]
                                                 (set-projects fetched-projects)
                                                 (if (not-empty fetched-projects)
                                                   (let [sorted-projects (sort-by #(.-name %) fetched-projects)
                                                         project-id-to-use (or selected-project-id 
                                                                                (.-id (first sorted-projects)))]
                                                     (when (nil? selected-project-id)
                                                       (set-selected-project-id project-id-to-use))
                                                     (-> (fetch-cases email password project-id-to-use)
                                                         (.then (fn [cases-result]
                                                                  (set-cases cases-result)
                                                                  (set-cases-loading false)))))
                                                   (do
                                                     (set-cases [])
                                                     (set-cases-loading false))))))))}  
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
                 "Projects" ($ projects-table {:projects projects
                                                       :email email
                                                       :password password
                                                       :on-project-deleted (fn []
                                                                             (-> (fetch-projects email password)
                                                                                 (.then #(set-projects %))))})
                 "Cases" ($ cases-table {:cases cases
                                         :projects projects
                                         :email email
                                         :password password
                                         :loading cases-loading
                                         :selected-project-id selected-project-id
                                         :on-project-changed (fn [project-id]
                                                               (set-selected-project-id project-id)
                                                               (set-cases-loading true)
                                                               (-> (fetch-cases email password project-id)
                                                                   (.then (fn [cases-result]
                                                                            (set-cases cases-result)
                                                                            (set-cases-loading false)))))
                                         :on-case-deleted (fn []
                                                            (when selected-project-id
                                                              (set-cases-loading true)
                                                              (-> (fetch-cases email password selected-project-id)
                                                                  (.then (fn [cases-result]
                                                                           (set-cases cases-result)
                                                                           (set-cases-loading false))))))})
                 (d/p (str "You clicked: " selected-link))))))))

(defnc xtest-app
  []
  (let [[is-logged-in set-is-logged-in] (hooks/use-state false)
        [email set-email] (hooks/use-state "")
        [password set-password] (hooks/use-state "")
        [logged-in-email set-logged-in-email] (hooks/use-state "")
        [logged-in-password set-logged-in-password] (hooks/use-state "")
        [login-error set-login-error] (hooks/use-state nil)]
    
    (if is-logged-in
      ;; Show main app if logged in
      ($ main-app {:is-logged-in is-logged-in 
                   :set-is-logged-in set-is-logged-in
                   :email logged-in-email
                   :password logged-in-password})
      
      ;; Show login form if not logged in
      ($ login-form {:email email
                     :password password
                     :error-message login-error
                     :on-email-change (fn [new-email]
                                        (set-email new-email)
                                        (set-login-error nil))
                     :on-password-change (fn [new-password]
                                           (set-password new-password)
                                           (set-login-error nil))
                     :on-login (fn []
                                 (set-login-error nil)
                                 (if (and (not= email "") (not= password ""))
                                   (-> (login-user email password)
                                       (.then (fn [result]
                                                (if (.-error result)
                                                  (set-login-error (.-error result))
                                                  (do
                                                    (set-logged-in-email email)
                                                    (set-logged-in-password password)
                                                    (set-is-logged-in true)
                                                    (set-email "")
                                                    (set-password ""))))))
                                   (set-login-error "Please enter both email and password.")))}))))

(defn ^:export init []
  (let [root (rdom/createRoot (js/document.getElementById "app"))]
    (.render root ($ xtest-app))))