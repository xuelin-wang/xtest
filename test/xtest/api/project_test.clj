(ns xtest.api.project-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clj-http.client :as http]
            [next.jdbc :as jdbc]
            [xtest.api.project :as project]
            [xtest.api.user :as user]
            [xtest.api.db :as db]
            [xtest.api.core :as core]
            [ring.adapter.jetty :refer [run-jetty]]))

(deftest create-project-validation
  (testing "returns 400 when name is blank"
    (let [response (project/create-project {:body {:name "" :_id "project-1"}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Project name is required"))))
  
  (testing "returns 400 when _id is blank"
    (let [response (project/create-project {:body {:name "Test Project" :_id ""}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Project _id is required"))))
  
  (testing "returns 400 when _id format is invalid"
    (let [response (project/create-project {:body {:name "Test Project" :_id "invalid-format"}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Project _id must be in format 'project-<num>'"))))
  
  (testing "returns 400 when _id already exists"
    (with-redefs [db/get-project-by-id (constantly {:_id "project-1" :name "Existing"})]
      (let [response (project/create-project {:body {:name "Test Project" :_id "project-1"}})]
        (is (= 400 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "Project with _id 'project-1' already exists")))))
  
  (testing "returns 400 when name already exists"
    (with-redefs [db/get-project-by-id (constantly nil)
                  db/get-project-by-name (constantly {:_id "project-2" :name "Test Project"})]
      (let [response (project/create-project {:body {:name "Test Project" :_id "project-1"}})]
        (is (= 400 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "Project with name 'Test Project' already exists"))))))

(deftest create-project-success
  (testing "returns 201 and creates project when valid"
    (let [project-data {:name "Valid Project" :_id "project-123"}
          inserted-project (atom nil)]
      (with-redefs [db/get-project-by-id (constantly nil)
                    db/get-project-by-name (constantly nil)
                    db/insert-project! (fn [p] (reset! inserted-project p) p)]
        (let [response (project/create-project {:body project-data})]
          (is (= 201 (:status response)))
          (is (= @inserted-project (:body response)))
          (is (= "Valid Project" (:name (:body response))))
          (is (= "project-123" (:_id (:body response)))))))))

(deftest get-projects-tests
  (testing "returns 400 when both names and ids are specified"
    (let [response (project/get-projects {:query-params {"names" "proj1,proj2" "ids" "project-1,project-2"}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Cannot specify both 'names' and 'ids' parameters"))))
  
  (testing "returns projects by names when names param is provided"
    (let [projects [{:_id "project-1" :name "Project One"} {:_id "project-2" :name "Project Two"}]]
      (with-redefs [db/get-projects-by-names (constantly projects)]
        (let [response (project/get-projects {:query-params {"names" "Project One,Project Two"}})]
          (is (= 200 (:status response)))
          (is (= projects (:body response)))))))
  
  (testing "returns projects by ids when ids param is provided"
    (let [projects [{:_id "project-1" :name "Project One"} {:_id "project-3" :name "Project Three"}]]
      (with-redefs [db/get-projects-by-ids (constantly projects)]
        (let [response (project/get-projects {:query-params {"ids" "project-1,project-3"}})]
          (is (= 200 (:status response)))
          (is (= projects (:body response)))))))
  
  (testing "returns all projects when no params are provided"
    (let [projects [{:_id "project-1" :name "Project One"} {:_id "project-2" :name "Project Two"} {:_id "project-3" :name "Project Three"}]]
      (with-redefs [db/get-projects (constantly projects)]
        (let [response (project/get-projects {:query-params {}})]
          (is (= 200 (:status response)))
          (is (= projects (:body response))))))))

(deftest project-integration-test
  (testing "creates and retrieves projects via REST API"
    (let [port 3103
          server-atom (atom nil)
          test-user {:first-name "Project"
                     :last-name "Tester"
                     :email "project@test.com"
                     :password "ProjectPass123!"}
          projects [{:name "Project Alpha" :_id "project-1"}
                   {:name "Project Beta" :_id "project-2"}
                   {:name "Project Gamma" :_id "project-3"}]]
      (try
        (db/init-db!)
        (try
          (let [db-spec {:jdbcUrl (or (System/getenv "XTDB_JDBC_URL")
                                      "jdbc:postgresql://localhost:5432/xtdb")}
                ds (jdbc/get-datasource db-spec)]
            (jdbc/execute! ds ["DELETE FROM user"])
            (jdbc/execute! ds ["DELETE FROM project"]))
          (catch Exception _))
        (reset! server-atom (run-jetty core/app {:port port :join? false}))
        (Thread/sleep 1000)
        
        (let [create-user-response (http/post (str "http://localhost:" port "/users/create")
                                            {:headers {"Content-Type" "application/json"}
                                             :body (json/write-str test-user)
                                             :as :json})]
          (is (= 201 (:status create-user-response)))
          
          (doseq [proj projects]
            (let [create-response (http/post (str "http://localhost:" port "/projects/create")
                                           {:headers {"Content-Type" "application/json"}
                                            :basic-auth [(:email test-user) (:password test-user)]
                                            :body (json/write-str proj)
                                            :as :json})]
              (is (= 201 (:status create-response)))
              (is (= (:name proj) (get-in create-response [:body :name])))
              (is (= (:_id proj) (get-in create-response [:body :_id])))))
          
          (let [get-all-response (http/get (str "http://localhost:" port "/projects/get")
                                         {:basic-auth [(:email test-user) (:password test-user)]
                                          :as :json})]
            (is (= 200 (:status get-all-response)))
            (is (= 3 (count (:body get-all-response)))))
          
          (let [get-by-names-response (http/get (str "http://localhost:" port "/projects/get")
                                              {:query-params {"names" "Project Alpha,Project Gamma"}
                                               :basic-auth [(:email test-user) (:password test-user)]
                                               :as :json})]
            (is (= 200 (:status get-by-names-response)))
            (is (= 2 (count (:body get-by-names-response)))))
          
          (let [get-by-ids-response (http/get (str "http://localhost:" port "/projects/get")
                                            {:query-params {"ids" "project-1,project-2"}
                                             :basic-auth [(:email test-user) (:password test-user)]
                                             :as :json})]
            (is (= 200 (:status get-by-ids-response)))
            (is (= 2 (count (:body get-by-ids-response))))))
        
        (finally
          (when @server-atom
            (.stop @server-atom)))))))

(deftest delete-project-validation
  (testing "returns 400 when _id is blank"
    (let [response (project/delete-project {:body {:_id ""}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Project _id is required"))))
  
  (testing "returns 404 when project not found"
    (with-redefs [db/get-project-by-id (constantly nil)]
      (let [response (project/delete-project {:body {:_id "project-999"}})]
        (is (= 404 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "No project found with _id project-999"))))))

(deftest delete-project-success
  (testing "returns 200 and deletes project when valid"
    (let [project-id "project-123"
          deleted-result (atom nil)]
      (with-redefs [db/get-project-by-id (constantly {:_id project-id :name "Test Project"})
                    db/delete-project! (fn [id] (reset! deleted-result {:next.jdbc/update-count 1}) @deleted-result)]
        (let [response (project/delete-project {:body {:_id project-id}})]
          (is (= 200 (:status response)))
          (is (str/includes? (get-in response [:body :message]) "deleted successfully"))
          (is (= 1 (get-in response [:body :rows-affected]))))))))