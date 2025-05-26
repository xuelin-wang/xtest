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

(defn get-user-by-email
  "Retrieves a user by email via URL query parameters.
  Returns 400 if missing 'email', 404 if not found, or 200 with the user map."
  [{:keys [query-params]}]
  (let [email (get query-params "email")]
    (if-not (seq email)
      {:status 400
       :body   {:error "Missing query parameter 'email'"}}
      (if-let [u (db/get-user-by-email email)]
        {:status 200
         :body   u}
        {:status 404
         :body   {:error (format "No user found for email %s" email)}}))))

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