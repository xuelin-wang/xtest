(ns xtest.api.user
  (:require [xtest.api.db :as db]))

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
             user {:id id
                   :first-name first-name
                   :last-name last-name
                   :email email
                   :password password}
             inserted (db/insert-user! user)]
         {:status 201
          :body inserted}))))