(ns xtest.api.db
  "Database configuration and functions for storing and retrieving user data via XTDB SQL."
  (:require
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]
    [clojure.data.json]))

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

(defn update-user!
  "Updates a user in the user table. Expects keys :id, :first-name, :last-name, :email, :password.
   Returns the updated user map."
  [user]
  (jdbc/execute! ds
    ["UPDATE user SET first_name = ?, last_name = ?, email = ?, password = ? WHERE _id = ?"
     (:first-name user)
     (:last-name user)
     (:email user)
     (:password user)
     (:id user)])
  user)

(defn get-users
  "Retrieves all users. Returns a vector of maps with kebab-case keys."
  []
  (jdbc/execute! ds
    ["SELECT _id, first_name, last_name, email, password FROM user ORDER BY _id"]
    {:builder-fn rs/as-unqualified-kebab-maps}))

(defn get-users-by-emails
  "Retrieves users by a collection of emails. Returns a vector of maps with kebab-case keys."
  [emails]
  (if (empty? emails)
    []
    (let [placeholders (clojure.string/join ", " (repeat (count emails) "?"))]
      (jdbc/execute! ds
        (into [(str "SELECT _id, first_name, last_name, email, password FROM user WHERE email IN (" placeholders ") ORDER BY _id")] emails)
        {:builder-fn rs/as-unqualified-kebab-maps}))))

(defn delete-user!
  "Deletes a user by id. Returns the number of rows affected."
  [id]
  (let [result (jdbc/execute! ds ["DELETE FROM user WHERE _id = ?" id])]
    (first result)))

(defn insert-project!
  "Inserts a project into the project table. Expects keys :_id, :name.
   Returns the inserted project map."
  [project]
  (jdbc/execute! ds
    ["INSERT INTO project (_id, name) VALUES (?, ?)"
     (:_id project)
     (:name project)])
  project)

(defn get-project-by-id
  "Retrieves a project by _id. Returns a map with kebab-case keys or nil if not found."
  [id]
  (first
    (jdbc/execute! ds
      ["SELECT _id, name FROM project WHERE _id = ?" id]
      {:builder-fn rs/as-unqualified-kebab-maps})))

(defn get-project-by-name
  "Retrieves a project by name. Returns a map with kebab-case keys or nil if not found."
  [name]
  (first
    (jdbc/execute! ds
      ["SELECT _id, name FROM project WHERE name = ?" name]
      {:builder-fn rs/as-unqualified-kebab-maps})))

(defn get-projects
  "Retrieves all projects. Returns a vector of maps with kebab-case keys."
  []
  (jdbc/execute! ds
    ["SELECT _id, name FROM project ORDER BY _id"]
    {:builder-fn rs/as-unqualified-kebab-maps}))

(defn get-projects-by-ids
  "Retrieves projects by a collection of _ids. Returns a vector of maps with kebab-case keys."
  [ids]
  (if (empty? ids)
    []
    (let [placeholders (clojure.string/join ", " (repeat (count ids) "?"))]
      (jdbc/execute! ds
        (into [(str "SELECT _id, name FROM project WHERE _id IN (" placeholders ") ORDER BY _id")] ids)
        {:builder-fn rs/as-unqualified-kebab-maps}))))

(defn get-projects-by-names
  "Retrieves projects by a collection of names. Returns a vector of maps with kebab-case keys."
  [names]
  (if (empty? names)
    []
    (let [placeholders (clojure.string/join ", " (repeat (count names) "?"))]
      (jdbc/execute! ds
        (into [(str "SELECT _id, name FROM project WHERE name IN (" placeholders ") ORDER BY _id")] names)
        {:builder-fn rs/as-unqualified-kebab-maps}))))

(defn delete-project!
  "Deletes a project by _id. Returns the number of rows affected."
  [id]
  (let [result (jdbc/execute! ds ["DELETE FROM project WHERE _id = ?" id])]
    (first result)))

(defn insert-case!
  "Inserts a case into the case table. Expects keys :_id, :name, :project-id, :description, :steps, :tags.
   Steps and tags are stored as JSON. Returns the inserted case map."
  [case-data]
  (jdbc/execute! ds
    ["INSERT INTO \"case\" (_id, name, project_id, description, steps, tags) VALUES (?, ?, ?, ?, ?, ?)"
     (:_id case-data)
     (:name case-data)
     (:project-id case-data)
     (:description case-data)
     (clojure.data.json/write-str (:steps case-data))
     (clojure.data.json/write-str (:tags case-data))])
  case-data)

(defn get-case-by-id
  "Retrieves a case by _id. Returns a map with kebab-case keys or nil if not found."
  [id]
  (when-let [result (first
                     (jdbc/execute! ds
                       ["SELECT _id, name, project_id, description, steps, tags FROM \"case\" WHERE _id = ?" id]
                       {:builder-fn rs/as-unqualified-kebab-maps}))]
    (-> result
        (update :steps #(when % (clojure.data.json/read-str % :key-fn keyword)))
        (update :tags #(when % (clojure.data.json/read-str % :key-fn keyword))))))

(defn get-case-by-name
  "Retrieves a case by name. Returns a map with kebab-case keys or nil if not found."
  [name]
  (when-let [result (first
                     (jdbc/execute! ds
                       ["SELECT _id, name, project_id, description, steps, tags FROM \"case\" WHERE name = ?" name]
                       {:builder-fn rs/as-unqualified-kebab-maps}))]
    (-> result
        (update :steps #(when % (clojure.data.json/read-str % :key-fn keyword)))
        (update :tags #(when % (clojure.data.json/read-str % :key-fn keyword))))))

(defn get-cases
  "Retrieves all cases. Returns a vector of maps with kebab-case keys."
  []
  (let [results (jdbc/execute! ds
                  ["SELECT _id, name, project_id, description, steps, tags FROM \"case\" ORDER BY _id"]
                  {:builder-fn rs/as-unqualified-kebab-maps})]
    (map (fn [case-data]
           (-> case-data
               (update :steps #(when % (clojure.data.json/read-str % :key-fn keyword)))
               (update :tags #(when % (clojure.data.json/read-str % :key-fn keyword)))))
         results)))

(defn get-cases-by-ids
  "Retrieves cases by a collection of _ids. Returns a vector of maps with kebab-case keys."
  [ids]
  (if (empty? ids)
    []
    (let [placeholders (clojure.string/join ", " (repeat (count ids) "?"))
          results (jdbc/execute! ds
                    (into [(str "SELECT _id, name, project_id, description, steps, tags FROM \"case\" WHERE _id IN (" placeholders ") ORDER BY _id")] ids)
                    {:builder-fn rs/as-unqualified-kebab-maps})]
      (map (fn [case-data]
             (-> case-data
                 (update :steps #(when % (clojure.data.json/read-str % :key-fn keyword)))
                 (update :tags #(when % (clojure.data.json/read-str % :key-fn keyword)))))
           results))))

(defn get-cases-by-names
  "Retrieves cases by a collection of names. Returns a vector of maps with kebab-case keys."
  [names]
  (if (empty? names)
    []
    (let [placeholders (clojure.string/join ", " (repeat (count names) "?"))
          results (jdbc/execute! ds
                    (into [(str "SELECT _id, name, project_id, description, steps, tags FROM \"case\" WHERE name IN (" placeholders ") ORDER BY _id")] names)
                    {:builder-fn rs/as-unqualified-kebab-maps})]
      (map (fn [case-data]
             (-> case-data
                 (update :steps #(when % (clojure.data.json/read-str % :key-fn keyword)))
                 (update :tags #(when % (clojure.data.json/read-str % :key-fn keyword)))))
           results))))

(defn get-cases-by-project-ids
  "Retrieves cases by a collection of project_ids. Returns a vector of maps with kebab-case keys."
  [project-ids]
  (if (empty? project-ids)
    []
    (let [placeholders (clojure.string/join ", " (repeat (count project-ids) "?"))
          results (jdbc/execute! ds
                    (into [(str "SELECT _id, name, project_id, description, steps, tags FROM \"case\" WHERE project_id IN (" placeholders ") ORDER BY _id")] project-ids)
                    {:builder-fn rs/as-unqualified-kebab-maps})]
      (map (fn [case-data]
             (-> case-data
                 (update :steps #(when % (clojure.data.json/read-str % :key-fn keyword)))
                 (update :tags #(when % (clojure.data.json/read-str % :key-fn keyword)))))
           results))))

(defn get-cases-filtered
  "Retrieves cases by multiple filter criteria with AND condition. 
   Accepts maps with :ids, :names, and :project-ids keys.
   Returns a vector of maps with kebab-case keys."
  [{:keys [ids names project-ids]}]
  (let [conditions []
        params []
        
        ;; Build WHERE conditions
        conditions (cond-> conditions
                     (seq ids)
                     (conj (str "_id IN (" (clojure.string/join ", " (repeat (count ids) "?")) ")"))
                     
                     (seq names)
                     (conj (str "name IN (" (clojure.string/join ", " (repeat (count names) "?")) ")"))
                     
                     (seq project-ids)
                     (conj (str "project_id IN (" (clojure.string/join ", " (repeat (count project-ids) "?")) ")")))
        
        ;; Build parameters list
        params (cond-> params
                 (seq ids) (concat ids)
                 (seq names) (concat names)
                 (seq project-ids) (concat project-ids))
        
        ;; Build final query
        where-clause (if (seq conditions)
                       (str " WHERE " (clojure.string/join " AND " conditions))
                       "")
        
        query (str "SELECT _id, name, project_id, description, steps, tags FROM \"case\"" where-clause " ORDER BY _id")
        
        results (jdbc/execute! ds
                  (into [query] params)
                  {:builder-fn rs/as-unqualified-kebab-maps})]
    
    (map (fn [case-data]
           (-> case-data
               (update :steps #(when % (clojure.data.json/read-str % :key-fn keyword)))
               (update :tags #(when % (clojure.data.json/read-str % :key-fn keyword)))))
         results)))

(defn delete-case!
  "Deletes a case by _id. Returns the number of rows affected."
  [id]
  (let [result (jdbc/execute! ds ["DELETE FROM \"case\" WHERE _id = ?" id])]
    (first result)))