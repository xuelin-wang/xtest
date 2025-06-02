(ns xtest.api.project
  (:require [xtest.api.db :as db]
            [clojure.string :as str]))

(defn create-project
  "Creates a new project with provided name and _id.
   Expects a Ring request map with JSON body parsed as keywords.
   Returns a Ring response map with status 201 and created project in the body,
   or status 400 with an error message if _id or name already exists."
  [{:keys [body]}]
  (let [{:keys [name _id]} body]
    (cond
      (str/blank? name)
      {:status 400
       :body {:error "Project name is required"}}
      
      (str/blank? _id)
      {:status 400
       :body {:error "Project _id is required"}}
      
      (not (re-matches #"project-\d+" _id))
      {:status 400
       :body {:error "Project _id must be in format 'project-<num>' where <num> is an integer"}}
      
      (db/get-project-by-id _id)
      {:status 400
       :body {:error (format "Project with _id '%s' already exists" _id)}}
      
      (db/get-project-by-name name)
      {:status 400
       :body {:error (format "Project with name '%s' already exists" name)}}
      
      :else
      (let [project {:_id _id :name name}
            inserted (db/insert-project! project)]
        {:status 201
         :body inserted}))))

(defn get-projects
  "Retrieves projects by names or ids via URL query parameters.
   If no parameters specified, returns all projects.
   Returns 200 with the project maps or empty list if none found."
  [{:keys [query-params]}]
  (let [names-param (get query-params "names")
        ids-param (get query-params "ids")
        names (when names-param (str/split names-param #","))
        ids (when ids-param (str/split ids-param #","))]
    (cond
      (and names ids)
      {:status 400
       :body {:error "Cannot specify both 'names' and 'ids' parameters"}}
      
      names
      (let [projects (db/get-projects-by-names names)]
        {:status 200
         :body projects})
      
      ids
      (let [projects (db/get-projects-by-ids ids)]
        {:status 200
         :body projects})
      
      :else
      (let [projects (db/get-projects)]
        {:status 200
         :body projects}))))

(defn delete-project
  "Deletes a project by _id via JSON body.
   Expects a Ring request map with JSON body containing :_id.
   Returns a Ring response map with status 200 if deleted successfully,
   or status 404 if the project is not found."
  [{:keys [body]}]
  (let [{:keys [_id]} body]
    (cond
      (str/blank? _id)
      {:status 400
       :body {:error "Project _id is required"}}
      
      (not (db/get-project-by-id _id))
      {:status 404
       :body {:error (format "No project found with _id %s" _id)}}
      
      :else
      (let [result (db/delete-project! _id)]
        {:status 200
         :body {:message (format "Project with _id %s deleted successfully" _id)
                :rows-affected (:next.jdbc/update-count result)}}))))