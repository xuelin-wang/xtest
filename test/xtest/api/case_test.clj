(ns xtest.api.case-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clj-http.client :as http]
            [next.jdbc :as jdbc]
            [xtest.api.case :as case]
            [xtest.api.user :as user]
            [xtest.api.project :as project]
            [xtest.api.db :as db]
            [xtest.api.core :as core]
            [ring.adapter.jetty :refer [run-jetty]]))

(def sample-steps
  [{:description "Step 1 description"
    :precondition "Step 1 precondition"
    :postcondition "Step 1 postcondition"}
   {:description "Step 2 description"
    :precondition ""
    :postcondition ""}
   {:description "Step 3 description"
    :precondition "Step 3 precondition"
    :postcondition ""}])

(def sample-tags ["tag1" "tag2" "integration"])

(deftest create-case-validation
  (testing "returns 400 when _id is blank"
    (let [response (case/create-case {:body {:_id "" :name "Test Case" :project-id "project-1" 
                                            :description "Test description" :steps sample-steps :tags sample-tags}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Case _id is required"))))
  
  (testing "returns 400 when _id format is invalid"
    (let [response (case/create-case {:body {:_id "invalid-format" :name "Test Case" :project-id "project-1"
                                            :description "Test description" :steps sample-steps :tags sample-tags}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Case _id must be in format 'case-<num>'"))))
  
  (testing "returns 400 when name is blank"
    (let [response (case/create-case {:body {:_id "case-1" :name "" :project-id "project-1"
                                            :description "Test description" :steps sample-steps :tags sample-tags}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Case name is required"))))
  
  (testing "returns 400 when project-id is blank"
    (let [response (case/create-case {:body {:_id "case-1" :name "Test Case" :project-id ""
                                            :description "Test description" :steps sample-steps :tags sample-tags}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Case project-id is required"))))
  
  (testing "returns 400 when description is blank"
    (let [response (case/create-case {:body {:_id "case-1" :name "Test Case" :project-id "project-1"
                                            :description "" :steps sample-steps :tags sample-tags}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Case description is required"))))
  
  (testing "returns 400 when steps are invalid - missing required fields"
    (let [invalid-steps [{:description "Valid description"}]  ; missing precondition and postcondition keys
          response (case/create-case {:body {:_id "case-1" :name "Test Case" :project-id "project-1"
                                            :description "Test description" :steps invalid-steps :tags sample-tags}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Steps must be a vector of maps"))))
  
  (testing "returns 400 when steps have empty description"
    (let [invalid-steps [{:description "" :precondition "pre" :postcondition "post"}]
          response (case/create-case {:body {:_id "case-1" :name "Test Case" :project-id "project-1"
                                            :description "Test description" :steps invalid-steps :tags sample-tags}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Steps must be a vector of maps"))))
  
  (testing "returns 400 when tags are invalid"
    (let [invalid-tags ["valid" 123 "invalid-number"]
          response (case/create-case {:body {:_id "case-1" :name "Test Case" :project-id "project-1"
                                            :description "Test description" :steps sample-steps :tags invalid-tags}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Tags must be a vector of strings"))))
  
  (testing "returns 400 when _id already exists"
    (with-redefs [db/get-case-by-id (constantly {:_id "case-1" :name "Existing"})]
      (let [response (case/create-case {:body {:_id "case-1" :name "Test Case" :project-id "project-1"
                                              :description "Test description" :steps sample-steps :tags sample-tags}})]
        (is (= 400 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "Case with _id 'case-1' already exists")))))
  
  (testing "returns 400 when name already exists"
    (with-redefs [db/get-case-by-id (constantly nil)
                  db/get-case-by-name (constantly {:_id "case-2" :name "Test Case"})]
      (let [response (case/create-case {:body {:_id "case-1" :name "Test Case" :project-id "project-1"
                                              :description "Test description" :steps sample-steps :tags sample-tags}})]
        (is (= 400 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "Case with name 'Test Case' already exists")))))
  
  (testing "returns 400 when project does not exist"
    (with-redefs [db/get-case-by-id (constantly nil)
                  db/get-case-by-name (constantly nil)
                  db/get-project-by-id (constantly nil)]
      (let [response (case/create-case {:body {:_id "case-1" :name "Test Case" :project-id "project-999"
                                              :description "Test description" :steps sample-steps :tags sample-tags}})]
        (is (= 400 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "Project with _id 'project-999' does not exist"))))))

(deftest create-case-success
  (testing "returns 201 and creates case when valid"
    (let [case-data {:_id "case-123" :name "Valid Case" :project-id "project-1"
                     :description "Valid description" :steps sample-steps :tags sample-tags}
          inserted-case (atom nil)]
      (with-redefs [db/get-case-by-id (constantly nil)
                    db/get-case-by-name (constantly nil)
                    db/get-project-by-id (constantly {:_id "project-1" :name "Test Project"})
                    db/insert-case! (fn [c] (reset! inserted-case c) c)]
        (let [response (case/create-case {:body case-data})]
          (is (= 201 (:status response)))
          (is (= @inserted-case (:body response)))
          (is (= "Valid Case" (:name (:body response))))
          (is (= "case-123" (:_id (:body response))))
          (is (= sample-steps (:steps (:body response))))
          (is (= sample-tags (:tags (:body response))))))))
  
  (testing "returns 201 with empty precondition and postcondition"
    (let [steps-with-empty [{:description "Step with empty conditions" :precondition "" :postcondition ""}]
          case-data {:_id "case-124" :name "Case With Empty Conditions" :project-id "project-1"
                     :description "Valid description" :steps steps-with-empty :tags sample-tags}
          inserted-case (atom nil)]
      (with-redefs [db/get-case-by-id (constantly nil)
                    db/get-case-by-name (constantly nil)
                    db/get-project-by-id (constantly {:_id "project-1" :name "Test Project"})
                    db/insert-case! (fn [c] (reset! inserted-case c) c)]
        (let [response (case/create-case {:body case-data})]
          (is (= 201 (:status response)))
          (is (= steps-with-empty (:steps (:body response)))))))))

(deftest get-cases-tests
  (testing "returns all cases when no params provided"
    (let [cases [{:_id "case-1" :name "Case One" :project-id "project-1" :description "Desc 1" :steps sample-steps :tags sample-tags}
                 {:_id "case-2" :name "Case Two" :project-id "project-2" :description "Desc 2" :steps sample-steps :tags sample-tags}]]
      (with-redefs [db/get-cases (constantly cases)]
        (let [response (case/get-cases {:query-params {}})]
          (is (= 200 (:status response)))
          (is (= cases (:body response)))))))
  
  (testing "returns filtered cases when params provided"
    (let [filtered-cases [{:_id "case-1" :name "Case One" :project-id "project-1" :description "Desc 1" :steps sample-steps :tags sample-tags}]]
      (with-redefs [db/get-cases-filtered (constantly filtered-cases)]
        (let [response (case/get-cases {:query-params {"ids" "case-1,case-3" "names" "Case One" "project-ids" "project-1"}})]
          (is (= 200 (:status response)))
          (is (= filtered-cases (:body response)))))))
  
  (testing "returns empty list when no cases found"
    (with-redefs [db/get-cases-filtered (constantly [])]
      (let [response (case/get-cases {:query-params {"ids" "nonexistent"}})]
        (is (= 200 (:status response)))
        (is (= [] (:body response)))))))

(deftest delete-case-validation
  (testing "returns 400 when _id is blank"
    (let [response (case/delete-case {:body {:_id ""}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Case _id is required"))))
  
  (testing "returns 404 when case not found"
    (with-redefs [db/get-case-by-id (constantly nil)]
      (let [response (case/delete-case {:body {:_id "case-999"}})]
        (is (= 404 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "No case found with _id case-999"))))))

(deftest delete-case-success
  (testing "returns 200 and deletes case when valid"
    (let [case-id "case-123"
          deleted-result (atom nil)]
      (with-redefs [db/get-case-by-id (constantly {:_id case-id :name "Test Case"})
                    db/delete-case! (fn [id] (reset! deleted-result {:next.jdbc/update-count 1}) @deleted-result)]
        (let [response (case/delete-case {:body {:_id case-id}})]
          (is (= 200 (:status response)))
          (is (str/includes? (get-in response [:body :message]) "deleted successfully"))
          (is (= 1 (get-in response [:body :rows-affected]))))))))

(deftest case-integration-test
  (testing "creates, retrieves, and deletes cases via REST API"
    (let [port 3104
          server-atom (atom nil)
          test-user {:first-name "Case"
                     :last-name "Tester"
                     :email "case@test.com"
                     :password "CasePass123!"}
          test-project {:name "Test Project" :_id "project-1"}
          test-cases [{:_id "case-1" :name "Case Alpha" :project-id "project-1"
                       :description "Alpha case description" :steps sample-steps :tags ["alpha" "test"]}
                      {:_id "case-2" :name "Case Beta" :project-id "project-1"
                       :description "Beta case description" :steps sample-steps :tags ["beta" "test"]}
                      {:_id "case-3" :name "Case Gamma" :project-id "project-1"
                       :description "Gamma case description" :steps sample-steps :tags ["gamma" "integration"]}]]
      (try
        (db/init-db!)
        (try
          (let [db-spec {:jdbcUrl (or (System/getenv "XTDB_JDBC_URL")
                                      "jdbc:postgresql://localhost:5432/xtdb")}
                ds (jdbc/get-datasource db-spec)]
            (jdbc/execute! ds ["DELETE FROM user"])
            (jdbc/execute! ds ["DELETE FROM project"])
            (jdbc/execute! ds ["DELETE FROM \"case\""]))
          (catch Exception _))
        (reset! server-atom (run-jetty core/app {:port port :join? false}))
        (Thread/sleep 1000)
        
        ;; Create user and project first
        (let [create-user-response (http/post (str "http://localhost:" port "/users/create")
                                            {:headers {"Content-Type" "application/json"}
                                             :body (json/write-str test-user)
                                             :as :json})
              create-project-response (http/post (str "http://localhost:" port "/projects/create")
                                               {:headers {"Content-Type" "application/json"}
                                                :basic-auth [(:email test-user) (:password test-user)]
                                                :body (json/write-str test-project)
                                                :as :json})]
          (is (= 201 (:status create-user-response)))
          (is (= 201 (:status create-project-response)))
          
          ;; Create cases
          (doseq [test-case test-cases]
            (let [create-response (http/post (str "http://localhost:" port "/cases/create")
                                           {:headers {"Content-Type" "application/json"}
                                            :basic-auth [(:email test-user) (:password test-user)]
                                            :body (json/write-str test-case)
                                            :as :json})]
              (is (= 201 (:status create-response)))
              (is (= (:name test-case) (get-in create-response [:body :name])))
              (is (= (:_id test-case) (get-in create-response [:body :_id])))
              (is (= (:steps test-case) (get-in create-response [:body :steps])))
              (is (= (:tags test-case) (get-in create-response [:body :tags])))))
          
          ;; Get all cases
          (let [get-all-response (http/get (str "http://localhost:" port "/cases/get")
                                         {:basic-auth [(:email test-user) (:password test-user)]
                                          :as :json})]
            (is (= 200 (:status get-all-response)))
            (is (= 3 (count (:body get-all-response)))))
          
          ;; Get cases by ids
          (let [get-by-ids-response (http/get (str "http://localhost:" port "/cases/get")
                                            {:query-params {"ids" "case-1,case-3"}
                                             :basic-auth [(:email test-user) (:password test-user)]
                                             :as :json})]
            (is (= 200 (:status get-by-ids-response)))
            (is (= 2 (count (:body get-by-ids-response)))))
          
          ;; Get cases by names
          (let [get-by-names-response (http/get (str "http://localhost:" port "/cases/get")
                                              {:query-params {"names" "Case Alpha,Case Beta"}
                                               :basic-auth [(:email test-user) (:password test-user)]
                                               :as :json})]
            (is (= 200 (:status get-by-names-response)))
            (is (= 2 (count (:body get-by-names-response)))))
          
          ;; Get cases by project-ids
          (let [get-by-project-response (http/get (str "http://localhost:" port "/cases/get")
                                                {:query-params {"project-ids" "project-1"}
                                                 :basic-auth [(:email test-user) (:password test-user)]
                                                 :as :json})]
            (is (= 200 (:status get-by-project-response)))
            (is (= 3 (count (:body get-by-project-response)))))
          
          ;; Test AND filtering with multiple criteria
          (let [get-filtered-response (http/get (str "http://localhost:" port "/cases/get")
                                              {:query-params {"ids" "case-1,case-2,case-3" "names" "Case Alpha,Case Beta"}
                                               :basic-auth [(:email test-user) (:password test-user)]
                                               :as :json})]
            (is (= 200 (:status get-filtered-response)))
            (is (= 2 (count (:body get-filtered-response)))))
          
          ;; Delete a case
          (let [delete-response (http/post (str "http://localhost:" port "/cases/delete")
                                         {:headers {"Content-Type" "application/json"}
                                          :basic-auth [(:email test-user) (:password test-user)]
                                          :body (json/write-str {:_id "case-1"})
                                          :as :json})]
            (is (= 200 (:status delete-response)))
            (is (str/includes? (get-in delete-response [:body :message]) "deleted successfully"))))
        
        (finally
          (when @server-atom
            (.stop @server-atom)))))))