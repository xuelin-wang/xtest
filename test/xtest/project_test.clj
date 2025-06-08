(ns xtest.project-test
  (:require [clojure.test :refer :all]
            [xtest.api.project :as project]
            [xtest.api.db :as db]
            [clojure.string :as str]))

;; Test data
(def test-project-1 {:_id "project-1"
                     :name "Test Project One"})

(def test-project-2 {:_id "project-2"
                     :name "Test Project Two"})

(def test-project-3 {:_id "project-3"
                     :name "Test Project Three"})

;; Helper functions
(defn mock-db-insert-project [project]
  project)

(defn mock-db-get-project-by-id [id]
  (case id
    "project-1" test-project-1
    "project-2" test-project-2
    "project-3" test-project-3
    nil))

(defn mock-db-get-project-by-name [name]
  (case name
    "Test Project One" test-project-1
    "Test Project Two" test-project-2
    "Test Project Three" test-project-3
    nil))

(defn mock-db-get-projects []
  [test-project-1 test-project-2 test-project-3])

(defn mock-db-get-projects-by-names [names]
  (let [name-set (set names)
        all-projects (mock-db-get-projects)]
    (vec (filter #(contains? name-set (:name %)) all-projects))))

(defn mock-db-get-projects-by-ids [ids]
  (let [id-set (set ids)
        all-projects (mock-db-get-projects)]
    (vec (filter #(contains? id-set (:_id %)) all-projects))))

(defn mock-db-delete-project [id]
  {:next.jdbc/update-count 1})

;; Tests for create-project
(deftest test-create-project-valid
  (testing "Creating a project with valid data should succeed"
    (with-redefs [db/get-project-by-id (constantly nil)
                  db/get-project-by-name (constantly nil)
                  db/insert-project! mock-db-insert-project]
      (let [request {:body-params {:id "project-123" :name "New Project"}}
            response (project/create-project request)]
        (is (= 201 (:status response)))
        (is (= "project-123" (get-in response [:body :id])))
        (is (= "New Project" (get-in response [:body :name])))))))

(deftest test-create-project-missing-name
  (testing "Creating a project without name should fail"
    (let [request {:body-params {:id "project-123"}}
          response (project/create-project request)]
      (is (= 400 (:status response)))
      (is (= "Project name is required" (get-in response [:body :error]))))))

(deftest test-create-project-blank-name
  (testing "Creating a project with blank name should fail"
    (let [request {:body-params {:id "project-123" :name ""}}
          response (project/create-project request)]
      (is (= 400 (:status response)))
      (is (= "Project name is required" (get-in response [:body :error]))))))

(deftest test-create-project-whitespace-name
  (testing "Creating a project with whitespace-only name should fail"
    (let [request {:body-params {:id "project-123" :name "   "}}
          response (project/create-project request)]
      (is (= 400 (:status response)))
      (is (= "Project name is required" (get-in response [:body :error]))))))

(deftest test-create-project-missing-id
  (testing "Creating a project without id should fail"
    (let [request {:body-params {:name "New Project"}}
          response (project/create-project request)]
      (is (= 400 (:status response)))
      (is (= "Project id is required" (get-in response [:body :error]))))))

(deftest test-create-project-blank-id
  (testing "Creating a project with blank id should fail"
    (let [request {:body-params {:id "" :name "New Project"}}
          response (project/create-project request)]
      (is (= 400 (:status response)))
      (is (= "Project id is required" (get-in response [:body :error]))))))

(deftest test-create-project-invalid-id-format
  (testing "Creating a project with invalid id format should fail"
    (let [invalid-ids ["project" "project-" "project-abc" "proj-123" "123" "project-123-extra"]]
      (doseq [invalid-id invalid-ids]
        (let [request {:body-params {:id invalid-id :name "New Project"}}
              response (project/create-project request)]
          (is (= 400 (:status response))
              (str "ID '" invalid-id "' should be invalid"))
          (is (str/includes? (get-in response [:body :error]) "Project id must be in format 'project-<num>'")))))))

(deftest test-create-project-valid-id-formats
  (testing "Creating a project with various valid id formats should succeed"
    (with-redefs [db/get-project-by-id (constantly nil)
                  db/get-project-by-name (constantly nil)
                  db/insert-project! mock-db-insert-project]
      (let [valid-ids ["project-1" "project-123" "project-0" "project-999999"]]
        (doseq [valid-id valid-ids]
          (let [request {:body-params {:id valid-id :name "New Project"}}
                response (project/create-project request)]
            (is (= 201 (:status response))
                (str "ID '" valid-id "' should be valid"))
            (is (= valid-id (get-in response [:body :id])))))))))

(deftest test-create-project-duplicate-id
  (testing "Creating a project with existing id should fail"
    (with-redefs [db/get-project-by-id mock-db-get-project-by-id
                  db/get-project-by-name (constantly nil)]
      (let [request {:body-params {:id "project-1" :name "Different Name"}}
            response (project/create-project request)]
        (is (= 400 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "Project with id 'project-1' already exists"))))))

(deftest test-create-project-duplicate-name
  (testing "Creating a project with existing name should fail"
    (with-redefs [db/get-project-by-id (constantly nil)
                  db/get-project-by-name mock-db-get-project-by-name]
      (let [request {:body-params {:id "project-999" :name "Test Project One"}}
            response (project/create-project request)]
        (is (= 400 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "Project with name 'Test Project One' already exists"))))))

;; Tests for get-projects
(deftest test-get-projects-all
  (testing "Getting all projects should return all projects"
    (with-redefs [db/get-projects mock-db-get-projects]
      (let [request {:params {}}
            response (project/get-projects request)]
        (is (= 200 (:status response)))
        (is (= 3 (count (:body response))))
        (is (= "project-1" (get-in response [:body 0 :_id])))
        (is (= "project-2" (get-in response [:body 1 :_id])))
        (is (= "project-3" (get-in response [:body 2 :_id])))))))

(deftest test-get-projects-by-names
  (testing "Getting projects by specific names should return filtered projects"
    (with-redefs [db/get-projects-by-names mock-db-get-projects-by-names]
      (let [request {:params {:names "Test Project One,Test Project Three"}}
            response (project/get-projects request)]
        (is (= 200 (:status response)))
        (is (= 2 (count (:body response))))
        (is (= "project-1" (get-in response [:body 0 :_id])))
        (is (= "project-3" (get-in response [:body 1 :_id])))))))

(deftest test-get-projects-by-single-name
  (testing "Getting projects by single name should work"
    (with-redefs [db/get-projects-by-names mock-db-get-projects-by-names]
      (let [request {:params {:names "Test Project Two"}}
            response (project/get-projects request)]
        (is (= 200 (:status response)))
        (is (= 1 (count (:body response))))
        (is (= "project-2" (get-in response [:body 0 :_id])))
        (is (= "Test Project Two" (get-in response [:body 0 :name])))))))

(deftest test-get-projects-by-nonexistent-names
  (testing "Getting projects by nonexistent names should return empty list"
    (with-redefs [db/get-projects-by-names mock-db-get-projects-by-names]
      (let [request {:params {:names "Nonexistent Project,Another Missing"}}
            response (project/get-projects request)]
        (is (= 200 (:status response)))
        (is (= 0 (count (:body response))))))))

(deftest test-get-projects-by-ids
  (testing "Getting projects by specific ids should return filtered projects"
    (with-redefs [db/get-projects-by-ids mock-db-get-projects-by-ids]
      (let [request {:params {:ids "project-1,project-3"}}
            response (project/get-projects request)]
        (is (= 200 (:status response)))
        (is (= 2 (count (:body response))))
        (is (= "project-1" (get-in response [:body 0 :_id])))
        (is (= "project-3" (get-in response [:body 1 :_id])))))))

(deftest test-get-projects-by-single-id
  (testing "Getting projects by single id should work"
    (with-redefs [db/get-projects-by-ids mock-db-get-projects-by-ids]
      (let [request {:params {:ids "project-2"}}
            response (project/get-projects request)]
        (is (= 200 (:status response)))
        (is (= 1 (count (:body response))))
        (is (= "project-2" (get-in response [:body 0 :_id])))
        (is (= "Test Project Two" (get-in response [:body 0 :name])))))))

(deftest test-get-projects-by-nonexistent-ids
  (testing "Getting projects by nonexistent ids should return empty list"
    (with-redefs [db/get-projects-by-ids mock-db-get-projects-by-ids]
      (let [request {:params {:ids "project-999,project-888"}}
            response (project/get-projects request)]
        (is (= 200 (:status response)))
        (is (= 0 (count (:body response))))))))

(deftest test-get-projects-both-names-and-ids
  (testing "Getting projects with both names and ids parameters should fail"
    (let [request {:params {:names "Test Project One" :ids "project-1"}}
          response (project/get-projects request)]
      (is (= 400 (:status response)))
      (is (= "Cannot specify both 'names' and 'ids' parameters" (get-in response [:body :error]))))))

;; Tests for delete-project
(deftest test-delete-project-valid
  (testing "Deleting existing project should succeed"
    (with-redefs [db/get-project-by-id mock-db-get-project-by-id
                  db/delete-project! mock-db-delete-project]
      (let [request {:params {:id "project-1"}}
            response (project/delete-project request)]
        (is (= 200 (:status response)))
        (is (str/includes? (get-in response [:body :message]) "Project with id project-1 deleted successfully"))
        (is (= 1 (get-in response [:body :rows-affected])))))))

(deftest test-delete-project-missing-id
  (testing "Deleting project without id should fail"
    (let [request {:params {}}
          response (project/delete-project request)]
      (is (= 400 (:status response)))
      (is (= "Project id is required" (get-in response [:body :error]))))))

(deftest test-delete-project-blank-id
  (testing "Deleting project with blank id should fail"
    (let [request {:params {:id ""}}
          response (project/delete-project request)]
      (is (= 400 (:status response)))
      (is (= "Project id is required" (get-in response [:body :error]))))))

(deftest test-delete-project-whitespace-id
  (testing "Deleting project with whitespace-only id should fail"
    (let [request {:params {:id "   "}}
          response (project/delete-project request)]
      (is (= 400 (:status response)))
      (is (= "Project id is required" (get-in response [:body :error]))))))

(deftest test-delete-project-not-found
  (testing "Deleting non-existent project should fail"
    (with-redefs [db/get-project-by-id (constantly nil)]
      (let [request {:params {:id "project-999"}}
            response (project/delete-project request)]
        (is (= 404 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "No project found with id project-999"))))))

;; Edge case and integration tests
(deftest test-project-name-edge-cases
  (testing "Project names with various characters should be handled correctly"
    (with-redefs [db/get-project-by-id (constantly nil)
                  db/get-project-by-name (constantly nil)
                  db/insert-project! mock-db-insert-project]
      (let [edge-case-names ["Project with spaces"
                             "Project-with-dashes"
                             "Project_with_underscores"
                             "Project123"
                             "Project (with parentheses)"
                             "Project.with.dots"
                             "Project/with/slashes"
                             "Very Long Project Name That Might Test Character Limits And Edge Cases"
                             "Spëcîål Çhärãctërs"]]
        (doseq [name edge-case-names]
          (let [request {:body-params {:id "project-123" :name name}}
                response (project/create-project request)]
            (is (= 201 (:status response))
                (str "Project name '" name "' should be valid"))
            (is (= name (get-in response [:body :name])))))))))

(deftest test-project-id-boundary-values
  (testing "Project IDs with boundary numeric values should work"
    (with-redefs [db/get-project-by-id (constantly nil)
                  db/get-project-by-name (constantly nil)
                  db/insert-project! mock-db-insert-project]
      (let [boundary-ids ["project-0" "project-1" "project-2147483647" "project-999999999"]]
        (doseq [id boundary-ids]
          (let [request {:body-params {:id id :name "Test Project"}}
                response (project/create-project request)]
            (is (= 201 (:status response))
                (str "Project ID '" id "' should be valid"))
            (is (= id (get-in response [:body :id])))))))))

(deftest test-mixed-parameter-scenarios
  (testing "Various parameter combinations in get-projects"
    (with-redefs [db/get-projects mock-db-get-projects
                  db/get-projects-by-names mock-db-get-projects-by-names
                  db/get-projects-by-ids mock-db-get-projects-by-ids]
      ;; Empty string parameters should not crash
      (let [request1 {:params {:names ""}}
            response1 (project/get-projects request1)]
        (is (= 200 (:status response1)))
        (is (= 0 (count (:body response1)))))
      
      (let [request2 {:params {:ids ""}}
            response2 (project/get-projects request2)]
        (is (= 200 (:status response2)))
        (is (= 0 (count (:body response2)))))
      
      ;; Mixed valid and invalid names/ids
      (let [request3 {:params {:names "Test Project One,Nonexistent Project"}}
            response3 (project/get-projects request3)]
        (is (= 200 (:status response3)))
        (is (= 1 (count (:body response3))))
        (is (= "Test Project One" (get-in response3 [:body 0 :name])))))))

(deftest test-case-sensitivity
  (testing "Project operations should be case sensitive"
    (with-redefs [db/get-project-by-name mock-db-get-project-by-name
                  db/get-projects-by-names mock-db-get-projects-by-names]
      ;; Case-sensitive name lookup should not find projects
      (let [request {:params {:names "test project one"}}  ; lowercase
            response (project/get-projects request)]
        (is (= 200 (:status response)))
        (is (= 0 (count (:body response))))))))