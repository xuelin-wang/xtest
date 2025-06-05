(ns xtest.api.case
  (:require [xtest.api.db :as db]
            [clojure.string :as str]))

(defn- valid-step?
  "Validates that a step has required description field. Precondition and postcondition are optional."
  [step]
  (and (map? step)
       (not (str/blank? (:description step)))
       (contains? step :precondition)
       (contains? step :postcondition)))

(defn- valid-steps?
  "Validates that steps is a vector of valid step maps"
  [steps]
  (and (vector? steps)
       (every? valid-step? steps)))

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
  [{:keys [body]}]
  (let [{:keys [id name project-id description steps tags]} body]
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
         :body inserted}))))

(defn get-cases
  "Retrieves cases by ids, names, or project-ids via URL query parameters.
   Multiple filters are combined with AND condition.
   If no parameters specified, returns all cases.
   Returns 200 with the case maps or empty list if none found."
  [{:keys [query-params]}]
  (let [ids-param (get query-params "ids")
        names-param (get query-params "names")
        project-ids-param (get query-params "project-ids")
        ids (when ids-param (str/split ids-param #","))
        names (when names-param (str/split names-param #","))
        project-ids (when project-ids-param (str/split project-ids-param #","))]
    
    (cond
      ;; If any filters are provided, use filtered query
      (or ids names project-ids)
      (let [cases (db/get-cases-filtered {:ids ids :names names :project-ids project-ids})]
        {:status 200
         :body cases})
      
      ;; No filters, return all cases
      :else
      (let [cases (db/get-cases)]
        {:status 200
         :body cases}))))

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