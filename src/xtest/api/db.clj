(ns xtest.api.db
  "Database configuration and functions for storing and retrieving user data via XTDB SQL."
  (:require
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]))

(def ^:private db-spec
  {:jdbcUrl (or (System/getenv "XTDB_JDBC_URL")
                "jdbc:postgresql://localhost:5432/xtdb")})

(def ^:private ds
  (jdbc/get-datasource db-spec))

(defn init-db!
  "No-op for XTDB v2: SQL tables are created implicitly."
  []
  nil)

(defn insert-user!
  "Inserts a user into the user table. Expects keys :id, :first-name, :last-name, :email, :password.
   Returns the inserted user map."
  [user]
  (jdbc/execute! ds
    ["INSERT INTO user (_id, first_name, last_name, email, password) VALUES (?, ?, ?, ?, ?)"
     (:id user)
     (:first-name user)
     (:last-name user)
     (:email user)
     (:password user)])
  user)

(defn get-user
  "Retrieves a user by id. Returns a map with kebab-case keys or nil if not found."
  [id]
  (first
    (jdbc/execute! ds
      ["SELECT _id, first_name, last_name, email, password
         FROM user WHERE _id = ?" id]
      {:builder-fn rs/as-unqualified-kebab-maps})))

(defn get-user-by-email
  "Retrieves a user by email. Returns a map with kebab-case keys or nil if not found."
  [email]
  (first
    (jdbc/execute! ds
      ["SELECT _id, first_name, last_name, email, password
         FROM user WHERE email = ?" email]
      {:builder-fn rs/as-unqualified-kebab-maps})))