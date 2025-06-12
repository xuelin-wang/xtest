(ns xtest.api.case
  (:require [xtest.api.db :as db]
            [xtest.api.util :as util]
            [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]
            [clojure.pprint :refer [pprint]]))

(def s-non-empty-string
  [:and string? [:fn {:error/message "must be non-empty"} (complement clojure.string/blank?)]])

(def s-step
  [:map {:closed true}
   [:description s-non-empty-string]
   [:precondition {:optional true} [:maybe string?]]
   [:postcondition {:optional true} [:maybe string?]]])

(defn- valid-step?
  "Validates that a step has required description field. Precondition and postcondition are optional."
  [step]
  (if (m/validate s-step step)
    {:value true}
    (let [error (me/humanize (m/explain s-step step))]
      (println "Invalid step:" error)
      {:value false
       :error "Invalid step format"
       :details error})))

(defn- valid-steps?
  "Validates that steps is a vector of valid step maps"
  [steps]
  (if (vector? steps)
    (let [valid-steps (map valid-step? steps)]
      (every? :value valid-steps)
      )
    false
    ))

(defn- valid-tags?
  "Validates that tags is a vector of strings"
  [tags]
  (and (vector? tags)
       (every? string? tags)))

(defn create-case
  "Creates a new case with provided id, name, project-id, description, steps, and tags.
   Expects a Ring request map with JSON body parsed as keywords.
   Returns a Ring response map with status 201 and created case in the body,
   or status 400 with an error message if validation fails."
  [{:keys [body-params]}]
  (let [{:keys [id name project-id description steps tags]} body-params]
    (cond
      (str/blank? id)
      {:status 400
       :body {:error "Case id is required"}}
      
      (not (re-matches #"case-\d+" id))
      {:status 400
       :body {:error "Case id must be in format 'case-<num>' where <num> is an integer"}}
      
      (str/blank? name)
      {:status 400
       :body {:error "Case name is required"}}
      
      (str/blank? project-id)
      {:status 400
       :body {:error "Case project-id is required"}}
      
      (str/blank? description)
      {:status 400
       :body {:error "Case description is required"}}
      
      (not (valid-steps? steps))
      {:status 400
       :body {:error "Steps must be a vector of maps with required description field and optional precondition/postcondition fields"}}
      
      (not (valid-tags? tags))
      {:status 400
       :body {:error "Tags must be a vector of strings"}}
      
      (db/get-case-by-id id)
      {:status 400
       :body {:error (format "Case with id '%s' already exists" id)}}
      
      (db/get-case-by-name name)
      {:status 400
       :body {:error (format "Case with name '%s' already exists" name)}}
      
      (not (db/get-project-by-id project-id))
      {:status 400
       :body {:error (format "Project with id '%s' does not exist" project-id)}}
      
      :else
      (let [case-data {:_id id 
                       :name name 
                       :project-id project-id 
                       :description description 
                       :steps steps 
                       :tags tags}
            inserted (db/insert-case! case-data)]
        {:status 201
         :body (util/replace_uderscore_id inserted)}))))

(defn get-cases
  "Retrieves cases by ids, names, or project-ids via URL query parameters.
   Multiple filters are combined with AND condition.
   If no parameters specified, returns all cases.
   Returns 200 with the case maps or empty list if none found."
  [{:keys [query-params]}]
  (let [ids-param (:ids query-params)
        names-param (:names query-params)
        project-ids-param (:project-ids query-params)]
    
    (cond
      ;; If any parameter is provided but is an empty string, return empty list
      (or (= ids-param "") (= names-param "") (= project-ids-param ""))
      {:status 200
       :body []}
       
      ;; If any filters are provided with values, use filtered query
      (or ids-param names-param project-ids-param)
      (let [ids (when ids-param (str/split ids-param #","))
            names (when names-param (str/split names-param #","))
            project-ids (when project-ids-param (str/split project-ids-param #","))
            cases (db/get-cases-filtered {:ids ids :names names :project-ids project-ids})]
        {:status 200
         :body (map util/replace_uderscore_id cases)})
      
      ;; No filters provided, return all cases
      :else
      (let [cases (db/get-cases)]
        {:status 200
         :body (map util/replace_uderscore_id cases)}))))

(defn delete-case
  "Deletes a case by id via JSON body.
   Expects a requiest query parameter: id.
   Returns a Ring response map with status 200 if deleted successfully,
   or status 404 if the case is not found."
  [{:keys [params]}]
  (let [{:keys [id]} params]
    (cond
      (str/blank? id)
      {:status 400
       :body {:error "Case id is required"}}
      
      (not (db/get-case-by-id id))
      {:status 404
       :body {:error (format "No case found with id %s" id)}}
      
      :else
      (let [result (db/delete-case! id)]
        {:status 200
         :body {:message (format "Case with id %s deleted successfully" id)
                ;:rows-affected (:next.jdbc/update-count result)
                }}))))