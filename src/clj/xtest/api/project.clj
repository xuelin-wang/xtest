(ns xtest.api.project
  (:require [xtest.api.db :as db]
            [clojure.string :as str]))

(defn create-project
  "Creates a new project with provided name and id.
   Expects a Ring request map with JSON body parsed as keywords.
   Returns a Ring response map with status 201 and created project in the body,
   or status 400 with an error message if id or name already exists."
  [{:keys [body-params]}]
  (let [{:keys [name id]} body-params]
    (cond
      (str/blank? name)
      {:status 400
       :body {:error "Project name is required"}}
      
      (str/blank? id)
      {:status 400
       :body {:error "Project id is required"}}
      
      (not (re-matches #"project-\d+" id))
      {:status 400
       :body {:error "Project id must be in format 'project-<num>' where <num> is an integer"}}
      
      (db/get-project-by-id id)
      {:status 400
       :body {:error (format "Project with id '%s' already exists" id)}}
      
      (db/get-project-by-name name)
      {:status 400
       :body {:error (format "Project with name '%s' already exists" name)}}
      
      :else
      (let [project {:_id id :name name}
            inserted (db/insert-project! project)]
        {:status 201
         :body inserted}))))

(defn get-projects
  "Retrieves projects by names or ids via URL query parameters.
   If no parameters specified, returns all projects.
   Returns 200 with the project maps or empty list if none found."
  [{:keys [params]}]
  (let [names-param (get params "names")
        ids-param (get params "ids")
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
  "Deletes a project by id.
   Expects a Ring request parameter id.
   Returns a Ring response map with status 200 if deleted successfully,
   or status 404 if the project is not found."
  [{:keys [params]}]
  (let [{:keys [id]} params]
    (cond
      (str/blank? id)
      {:status 400
       :body {:error "Project id is required"}}
      
      (not (db/get-project-by-id id))
      {:status 404
       :body {:error (format "No project found with id %s" id)}}
      
      :else
      (let [result (db/delete-project! id)]
        {:status 200
         :body {:message (format "Project with id %s deleted successfully" id)
                :rows-affected (:next.jdbc/update-count result)}}))))