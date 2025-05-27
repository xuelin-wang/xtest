(ns xtest.api.user
  (:require [xtest.api.db :as db])
  (:import (com.password4j Password)))

;; Allowed special symbols for passwords
(def ^:private special-chars
  #{\! \@ \# \$ \% \^ \& \* \( \) \- \_ \= \+ \[ \] \{ \} \| \/ \\ \< \> \, \. \: \; \" \'})

(defn- valid-password?
  "Return true if password is at least 10 characters long, contains at least one lowercase ASCII letter, one uppercase ASCII letter, one digit, and one special symbol."
  [password]
  (and (string? password)
       (>= (count password) 10)
       (re-find #"[a-z]" password)
       (re-find #"[A-Z]" password)
       (re-find #"[0-9]" password)
       (some special-chars password)))

 (defn create-user
   "Creates a new user with provided first-name, last-name, email, and password.
   Expects a Ring request map with JSON body parsed as keywords.
   Returns a Ring response map with status 201 and created user in the body,
   or status 400 with an error message if the password does not meet complexity requirements."
   [{:keys [body]}]
   (let [{:keys [first-name last-name email password]} body]
     (if-not (valid-password? password)
       {:status 400
        :body {:error "Password must be at least 10 characters long, have at least one lowercase ASCII letter, one uppercase ASCII letter, one digit, and one special symbol (e.g. ! @ # $ % ^ & * ( ) - _ = + [ ] { } | / \\ < > , . : ; \" ')."}}
       (let [id (str (java.util.UUID/randomUUID))
             hash (-> (Password/hash password)
                      .withArgon2
                      .getResult)
             user {:id id
                   :first-name first-name
                   :last-name last-name
                   :email email
                   :password hash}
             inserted (db/insert-user! user)]
        {:status 201
         :body inserted}))))

(defn get-users
  "Retrieves users by emails via URL query parameters.
  If 'emails' parameter is provided as comma-separated list, returns users with those emails.
  If no 'emails' parameter is provided, returns all users.
  Returns 200 with the user maps or empty list if none found."
  [{:keys [query-params]}]
  (let [emails-param (get query-params "emails")
        emails (when emails-param (clojure.string/split emails-param #","))]
    (if emails
      (let [users (db/get-users-by-emails emails)]
        {:status 200
         :body users})
      (let [users (db/get-users)]
        {:status 200
         :body users}))))

(defn update-user
  "Updates a user's password with provided email, original password, and new password.
  Expects a Ring request map with JSON body parsed as keywords.
  Returns a Ring response map with status 200 and updated user in the body,
  or status 400 with an error message if the new password does not meet complexity requirements,
  or status 401 if the original password is incorrect,
  or status 404 if the user is not found."
  [{:keys [body]}]
  (let [{:keys [email original-password new-password]} body]
    (if-let [user (db/get-user-by-email email)]
      (if (-> (Password/check original-password (:password user))
              .withArgon2)
        (if (valid-password? new-password)
          (let [new-hash (-> (Password/hash new-password)
                             .withArgon2
                             .getResult)
                updated-user (assoc user :password new-hash)
                result (db/update-user! updated-user)]
            {:status 200
             :body result})
          {:status 400
           :body {:error "Password must be at least 10 characters long, have at least one lowercase ASCII letter, one uppercase ASCII letter, one digit, and one special symbol (e.g. ! @ # $ % ^ & * ( ) - _ = + [ ] { } | / \\ < > , . : ; \" ')."}})
        {:status 401
         :body {:error "Original password is incorrect"}})
      {:status 404
       :body {:error (format "No user found for email %s" email)}})))

(defn delete-user
  "Deletes a user by id via JSON body.
   Expects a Ring request map with JSON body containing :id.
   Returns a Ring response map with status 200 if deleted successfully,
   or status 404 if the user is not found."
  [{:keys [body]}]
  (let [{:keys [id]} body]
    (cond
      (clojure.string/blank? id)
      {:status 400
       :body {:error "User id is required"}}
      
      (not (db/get-user id))
      {:status 404
       :body {:error (format "No user found with id %s" id)}}
      
      :else
      (let [result (db/delete-user! id)]
        {:status 200
         :body {:message (format "User with id %s deleted successfully" id)
                :rows-affected (:next.jdbc/update-count result)}}))))

(defn login
  "Validates user credentials via JSON body with email and password.
   Returns 200 with success message if credentials are valid,
   or 400/401 with error message if invalid."
  [{:keys [body]}]
  (let [{:keys [email password]} body]
    (cond
      (clojure.string/blank? email)
      {:status 400
       :body {:error "Email parameter is required"}}
      
      (clojure.string/blank? password)
      {:status 400
       :body {:error "Password parameter is required"}}
      
      :else
      (if-let [user (db/get-user-by-email email)]
        (if (-> (Password/check password (:password user))
                .withArgon2)
          {:status 200
           :body {:message "Login successful"
                  :user {:id (:id user)
                         :first-name (:first-name user)
                         :last-name (:last-name user)
                         :email (:email user)}}}
          {:status 401
           :body {:error "Invalid password"}})
        {:status 401
         :body {:error "User not found"}}))))