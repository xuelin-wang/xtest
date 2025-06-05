(ns xtest.app
  (:require [helix.core :refer [defnc $ <>]]
            [helix.hooks :as hooks]
            [helix.dom :as d]
            ["react-dom/client" :as rdom]
            [helix.part1 :as p1]
            [helix.part2 :as p2]
            [helix.part3 :as p3]
            [helix.part4 :as p4]
            [helix.part5 :as p5]
            [helix.part6 :as p6]))

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

(defnc main-app
  [{:keys [is-logged-in set-is-logged-in]}]
  (let [[selected-link set-selected-link] (hooks/use-state nil)]
    (<>
      ;; Top bar with clickable links
      (d/div {:style {:padding "10px"
                      :border-bottom "1px solid #ccc"
                      :margin-bottom "20px"}}
       (let [links
             (mapv
               (fn [title]
                 (d/a {:href "#"
                       :style {:margin-right "20px"
                               :text-decoration "none"
                               :color "#007bff"
                               :cursor "pointer"}
                       :onClick (fn [e]
                                  (.preventDefault e)
                                  (set-selected-link title))}
                      title)
                 )
               ["Projects" "Cases" "Users"])
             logout #(set-is-logged-in false)
             logout-button
             (d/button {:on-click logout}
                       "Logout")]
         (conj links logout-button)
         )
        )
      
      ;; Main content
      ;; Display selected link text below
      (when selected-link
        (d/div {:style {:margin-top "20px"
                        :padding "10px"
                        :background-color "#f8f9fa"
                        :border "1px solid #dee2e6"
                        :border-radius "4px"}}
               (d/p (str "You clicked: " selected-link)))))))

(defnc xtest-app
  []
  (let [[is-logged-in set-is-logged-in] (hooks/use-state false)
        [email set-email] (hooks/use-state "")
        [password set-password] (hooks/use-state "")]
    
    (if is-logged-in
      ;; Show main app if logged in
      ($ main-app {:is-logged-in is-logged-in :set-is-logged-in set-is-logged-in})
      
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
