(ns xtest.case-test
  (:require [clojure.test :refer :all]
            [xtest.api.case :as case]
            [xtest.api.db :as db]
            [clojure.string :as str]))

;; Test data
(def valid-steps [{:description "Step 1 description"
                   :precondition "Step 1 precondition"
                   :postcondition "Step 1 postcondition"}
                  {:description "Step 2 description"
                   :precondition ""
                   :postcondition "Step 2 postcondition"}])

(def valid-tags ["smoke" "regression" "critical"])

(def test-case-1 {:_id "case-1"
                  :name "Test Case One"
                  :project-id "project-1"
                  :description "First test case description"
                  :steps valid-steps
                  :tags valid-tags})

(def test-case-2 {:_id "case-2"
                  :name "Test Case Two"
                  :project-id "project-1"
                  :description "Second test case description"
                  :steps valid-steps
                  :tags ["integration"]})

(def test-case-3 {:_id "case-3"
                  :name "Test Case Three"
                  :project-id "project-2"
                  :description "Third test case description"
                  :steps valid-steps
                  :tags []})

;; Helper functions
(defn mock-db-insert-case [case-data]
  case-data)

(defn mock-db-get-case-by-id [id]
  (case id
    "case-1" test-case-1
    "case-2" test-case-2
    "case-3" test-case-3
    nil))

(defn mock-db-get-case-by-name [name]
  (case name
    "Test Case One" test-case-1
    "Test Case Two" test-case-2
    "Test Case Three" test-case-3
    nil))

(defn mock-db-get-project-by-id [id]
  (case id
    "project-1" {:_id "project-1" :name "Project One"}
    "project-2" {:_id "project-2" :name "Project Two"}
    nil))

(defn mock-db-get-cases []
  [test-case-1 test-case-2 test-case-3])

(defn mock-db-get-cases-filtered [{:keys [ids names project-ids]}]
  (let [all-cases (mock-db-get-cases)
        filtered-cases (cond-> all-cases
                         (seq ids)
                         (->> (filter #(contains? (set ids) (:_id %))))
                         
                         (seq names)
                         (->> (filter #(contains? (set names) (:name %))))
                         
                         (seq project-ids)
                         (->> (filter #(contains? (set project-ids) (:project-id %)))))]
    (vec filtered-cases)))

(defn mock-db-delete-case [id]
  {:next.jdbc/update-count 1})

;; Tests for create-case
(deftest test-create-case-valid
  (testing "Creating a case with valid data should succeed"
    (with-redefs [db/get-case-by-id (constantly nil)
                  db/get-case-by-name (constantly nil)
                  db/get-project-by-id mock-db-get-project-by-id
                  db/insert-case! mock-db-insert-case]
      (let [request {:body-params {:id "case-123"
                            :name "New Test Case"
                            :project-id "project-1"
                            :description "A new test case description"
                            :steps valid-steps
                            :tags valid-tags}}
            response (case/create-case request)]
        (is (= 201 (:status response)))
        (is (= "case-123" (get-in response [:body :id])))
        (is (= "New Test Case" (get-in response [:body :name])))
        (is (= "project-1" (get-in response [:body :project-id])))
        (is (= "A new test case description" (get-in response [:body :description])))
        (is (= valid-steps (get-in response [:body :steps])))
        (is (= valid-tags (get-in response [:body :tags])))))))

(deftest test-create-case-missing-id
  (testing "Creating a case without id should fail"
    (let [request {:body-params {:name "New Test Case"
                          :project-id "project-1"
                          :description "A description"
                          :steps valid-steps
                          :tags valid-tags}}
          response (case/create-case request)]
      (is (= 400 (:status response)))
      (is (= "Case id is required" (get-in response [:body :error]))))))

(deftest test-create-case-blank-id
  (testing "Creating a case with blank id should fail"
    (let [request {:body-params {:id ""
                          :name "New Test Case"
                          :project-id "project-1"
                          :description "A description"
                          :steps valid-steps
                          :tags valid-tags}}
          response (case/create-case request)]
      (is (= 400 (:status response)))
      (is (= "Case id is required" (get-in response [:body :error]))))))

(deftest test-create-case-invalid-id-format
  (testing "Creating a case with invalid id format should fail"
    (let [invalid-ids ["case" "case-" "case-abc" "test-123" "123" "case-123-extra"]]
      (doseq [invalid-id invalid-ids]
        (let [request {:body-params {:id invalid-id
                              :name "New Test Case"
                              :project-id "project-1"
                              :description "A description"
                              :steps valid-steps
                              :tags valid-tags}}
              response (case/create-case request)]
          (is (= 400 (:status response))
              (str "ID '" invalid-id "' should be invalid"))
          (is (str/includes? (get-in response [:body :error]) "Case id must be in format 'case-<num>'")))))))

(deftest test-create-case-valid-id-formats
  (testing "Creating a case with various valid id formats should succeed"
    (with-redefs [db/get-case-by-id (constantly nil)
                  db/get-case-by-name (constantly nil)
                  db/get-project-by-id mock-db-get-project-by-id
                  db/insert-case! mock-db-insert-case]
      (let [valid-ids ["case-1" "case-123" "case-0" "case-999999"]]
        (doseq [valid-id valid-ids]
          (let [request {:body-params {:id valid-id
                                :name "New Test Case"
                                :project-id "project-1"
                                :description "A description"
                                :steps valid-steps
                                :tags valid-tags}}
                response (case/create-case request)]
            (is (= 201 (:status response))
                (str "ID '" valid-id "' should be valid"))
            (is (= valid-id (get-in response [:body :id])))))))))

(deftest test-create-case-missing-name
  (testing "Creating a case without name should fail"
    (let [request {:body-params {:id "case-123"
                          :project-id "project-1"
                          :description "A description"
                          :steps valid-steps
                          :tags valid-tags}}
          response (case/create-case request)]
      (is (= 400 (:status response)))
      (is (= "Case name is required" (get-in response [:body :error]))))))

(deftest test-create-case-blank-name
  (testing "Creating a case with blank name should fail"
    (let [request {:body-params {:id "case-123"
                          :name ""
                          :project-id "project-1"
                          :description "A description"
                          :steps valid-steps
                          :tags valid-tags}}
          response (case/create-case request)]
      (is (= 400 (:status response)))
      (is (= "Case name is required" (get-in response [:body :error]))))))

(deftest test-create-case-missing-project-id
  (testing "Creating a case without project-id should fail"
    (let [request {:body-params {:id "case-123"
                          :name "New Test Case"
                          :description "A description"
                          :steps valid-steps
                          :tags valid-tags}}
          response (case/create-case request)]
      (is (= 400 (:status response)))
      (is (= "Case project-id is required" (get-in response [:body :error]))))))

(deftest test-create-case-blank-project-id
  (testing "Creating a case with blank project-id should fail"
    (let [request {:body-params {:id "case-123"
                          :name "New Test Case"
                          :project-id ""
                          :description "A description"
                          :steps valid-steps
                          :tags valid-tags}}
          response (case/create-case request)]
      (is (= 400 (:status response)))
      (is (= "Case project-id is required" (get-in response [:body :error]))))))

(deftest test-create-case-missing-description
  (testing "Creating a case without description should fail"
    (let [request {:body-params {:id "case-123"
                          :name "New Test Case"
                          :project-id "project-1"
                          :steps valid-steps
                          :tags valid-tags}}
          response (case/create-case request)]
      (is (= 400 (:status response)))
      (is (= "Case description is required" (get-in response [:body :error]))))))

(deftest test-create-case-blank-description
  (testing "Creating a case with blank description should fail"
    (let [request {:body-params {:id "case-123"
                          :name "New Test Case"
                          :project-id "project-1"
                          :description ""
                          :steps valid-steps
                          :tags valid-tags}}
          response (case/create-case request)]
      (is (= 400 (:status response)))
      (is (= "Case description is required" (get-in response [:body :error]))))))

(deftest test-create-case-invalid-steps
  (testing "Creating a case with invalid steps should fail"
    (let [invalid-steps-cases [
                               ;; Not a vector
                               {:steps "not a vector"}
                               ;; Not maps
                               {:steps ["string step"]}
                               ;; Missing description
                               {:steps [{:precondition "pre" :postcondition "post"}]}
                               ;; Blank description
                               {:steps [{:description "" :precondition "pre" :postcondition "post"}]}
                               ;; Missing precondition key
                               {:steps [{:description "desc" :postcondition "post"}]}
                               ;; Missing postcondition key
                               {:steps [{:description "desc" :precondition "pre"}]}]]
      (doseq [{:keys [steps]} invalid-steps-cases]
        (let [request {:body-params {:id "case-123"
                              :name "New Test Case"
                              :project-id "project-1"
                              :description "A description"
                              :steps steps
                              :tags valid-tags}}
              response (case/create-case request)]
          (is (= 400 (:status response))
              (str "Steps " steps " should be invalid"))
          (is (str/includes? (get-in response [:body :error]) "Steps must be a vector of maps")))))))

(deftest test-create-case-valid-steps-variations
  (testing "Creating a case with various valid steps should succeed"
    (with-redefs [db/get-case-by-id (constantly nil)
                  db/get-case-by-name (constantly nil)
                  db/get-project-by-id mock-db-get-project-by-id
                  db/insert-case! mock-db-insert-case]
      (let [valid-steps-variations [
                                    ;; Empty precondition/postcondition
                                    [{:description "desc" :precondition "" :postcondition ""}]
                                    ;; Nil precondition/postcondition
                                    [{:description "desc" :precondition nil :postcondition nil}]
                                    ;; Multiple steps
                                    [{:description "step1" :precondition "pre1" :postcondition "post1"}
                                     {:description "step2" :precondition "pre2" :postcondition "post2"}]
                                    ;; Empty steps vector
                                    []]]
        (doseq [steps valid-steps-variations]
          (let [request {:body-params {:id "case-123"
                                :name "New Test Case"
                                :project-id "project-1"
                                :description "A description"
                                :steps steps
                                :tags valid-tags}}
                response (case/create-case request)]
            (is (= 201 (:status response))
                (str "Steps " steps " should be valid"))
            (is (= steps (get-in response [:body :steps])))))))))

(deftest test-create-case-invalid-tags
  (testing "Creating a case with invalid tags should fail"
    (let [invalid-tags-cases [
                              ;; Not a vector
                              {:tags "not a vector"}
                              ;; Contains non-strings
                              {:tags ["valid" 123 "tag"]}
                              {:tags [true false]}
                              {:tags [{:not "string"}]}]]
      (doseq [{:keys [tags]} invalid-tags-cases]
        (let [request {:body-params {:id "case-123"
                              :name "New Test Case"
                              :project-id "project-1"
                              :description "A description"
                              :steps valid-steps
                              :tags tags}}
              response (case/create-case request)]
          (is (= 400 (:status response))
              (str "Tags " tags " should be invalid"))
          (is (str/includes? (get-in response [:body :error]) "Tags must be a vector of strings")))))))

(deftest test-create-case-valid-tags-variations
  (testing "Creating a case with various valid tags should succeed"
    (with-redefs [db/get-case-by-id (constantly nil)
                  db/get-case-by-name (constantly nil)
                  db/get-project-by-id mock-db-get-project-by-id
                  db/insert-case! mock-db-insert-case]
      (let [valid-tags-variations [
                                   ;; Empty tags
                                   []
                                   ;; Single tag
                                   ["smoke"]
                                   ;; Multiple tags
                                   ["smoke" "regression" "critical"]
                                   ;; Tags with special characters
                                   ["tag-with-dash" "tag_with_underscore" "tag.with.dots"]]]
        (doseq [tags valid-tags-variations]
          (let [request {:body-params {:id "case-123"
                                :name "New Test Case"
                                :project-id "project-1"
                                :description "A description"
                                :steps valid-steps
                                :tags tags}}
                response (case/create-case request)]
            (is (= 201 (:status response))
                (str "Tags " tags " should be valid"))
            (is (= tags (get-in response [:body :tags])))))))))

(deftest test-create-case-duplicate-id
  (testing "Creating a case with existing id should fail"
    (with-redefs [db/get-case-by-id mock-db-get-case-by-id
                  db/get-case-by-name (constantly nil)
                  db/get-project-by-id mock-db-get-project-by-id]
      (let [request {:body-params {:id "case-1"
                            :name "Different Name"
                            :project-id "project-1"
                            :description "A description"
                            :steps valid-steps
                            :tags valid-tags}}
            response (case/create-case request)]
        (is (= 400 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "Case with id 'case-1' already exists"))))))

(deftest test-create-case-duplicate-name
  (testing "Creating a case with existing name should fail"
    (with-redefs [db/get-case-by-id (constantly nil)
                  db/get-case-by-name mock-db-get-case-by-name
                  db/get-project-by-id mock-db-get-project-by-id]
      (let [request {:body-params {:id "case-999"
                            :name "Test Case One"
                            :project-id "project-1"
                            :description "A description"
                            :steps valid-steps
                            :tags valid-tags}}
            response (case/create-case request)]
        (is (= 400 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "Case with name 'Test Case One' already exists"))))))

(deftest test-create-case-nonexistent-project
  (testing "Creating a case with non-existent project should fail"
    (with-redefs [db/get-case-by-id (constantly nil)
                  db/get-case-by-name (constantly nil)
                  db/get-project-by-id mock-db-get-project-by-id]
      (let [request {:body-params {:id "case-123"
                            :name "New Test Case"
                            :project-id "project-999"
                            :description "A description"
                            :steps valid-steps
                            :tags valid-tags}}
            response (case/create-case request)]
        (is (= 400 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "Project with id 'project-999' does not exist"))))))

;; Tests for get-cases
(deftest test-get-cases-all
  (testing "Getting all cases should return all cases"
    (with-redefs [db/get-cases mock-db-get-cases]
      (let [request {:query-params {}}
            response (case/get-cases request)]
        (is (= 200 (:status response)))
        (is (= 3 (count (:body response))))
        (is (= "case-1" (-> response :body first :id)))
        (is (= "case-2" (-> response :body second :id)))
        (is (= "case-3"  (:id (nth (:body response) 2))))))))

(deftest test-get-cases-by-ids
  (testing "Getting cases by specific ids should return filtered cases"
    (with-redefs [db/get-cases-filtered mock-db-get-cases-filtered
                  db/get-cases mock-db-get-cases]
      (let [request {:query-params {:ids "case-1,case-3"}}
            response (case/get-cases request)]
        (is (= 200 (:status response)))
        (is (= 2 (count (:body response))))
        (is (= "case-1" (-> response :body first :id)))
        (is (= "case-3" (-> response :body second :id)))))))

(deftest test-get-cases-by-single-id
  (testing "Getting cases by single id should work"
    (with-redefs [db/get-cases-filtered mock-db-get-cases-filtered]
      (let [request {:query-params {:ids "case-2"}}
            response (case/get-cases request)]
        (is (= 200 (:status response)))
        (is (= 1 (count (:body response))))
        (is (= "case-2" (-> response :body first :id)))
        (is (= "Test Case Two" (-> response :body first :name)))))))

(deftest test-get-cases-by-names
  (testing "Getting cases by specific names should return filtered cases"
    (with-redefs [db/get-cases-filtered mock-db-get-cases-filtered]
      (let [request {:query-params {:names "Test Case One,Test Case Three"}}
            response (case/get-cases request)]
        (is (= 200 (:status response)))
        (is (= 2 (count (:body response))))
        (is (= "Test Case One" (-> response :body first :name)))
        (is (= "Test Case Three" (-> response :body second :name)))))))

(deftest test-get-cases-by-project-ids
  (testing "Getting cases by project ids should return filtered cases"
    (with-redefs [db/get-cases-filtered mock-db-get-cases-filtered]
      (let [request {:query-params {:project-ids "project-1"}}
            response (case/get-cases request)]
        (is (= 200 (:status response)))
        (is (= 2 (count (:body response))))
        (is (= "project-1" (-> response :body first :project-id)))
        (is (= "project-1" (-> response :body second :project-id)))))))

(deftest test-get-cases-multiple-filters
  (testing "Getting cases with multiple filters should apply AND condition"
    (with-redefs [db/get-cases-filtered mock-db-get-cases-filtered]
      (let [request {:query-params {:ids "case-1,case-2,case-3"
                                    :project-ids "project-1"}}
            response (case/get-cases request)]
        (is (= 200 (:status response)))
        (is (= 2 (count (:body response))))
        ;; Should return only case-1 and case-2 (both have project-1)
        (is (every? #(= "project-1" (:project-id %)) (:body response)))))))

(deftest test-get-cases-nonexistent-filters
  (testing "Getting cases with nonexistent filters should return empty list"
    (with-redefs [db/get-cases-filtered mock-db-get-cases-filtered]
      (let [request {:query-params {:ids "case-999,case-888"}}
            response (case/get-cases request)]
        (is (= 200 (:status response)))
        (is (= 0 (count (:body response))))))))

(deftest test-get-cases-empty-filters
  (testing "Getting cases with empty filter parameters should return empty list"
    (with-redefs [db/get-cases-filtered mock-db-get-cases-filtered]
      (let [request1 {:query-params {:ids ""}}
            response1 (case/get-cases request1)]
        (is (= 200 (:status response1)))
        (is (= 0 (count (:body response1)))))
      
      (let [request2 {:query-params {:names ""}}
            response2 (case/get-cases request2)]
        (is (= 200 (:status response2)))
        (is (= 0 (count (:body response2)))))
      
      (let [request3 {:query-params {:project-ids ""}}
            response3 (case/get-cases request3)]
        (is (= 200 (:status response3)))
        (is (= 0 (count (:body response3))))))))

;; Tests for delete-case
(deftest test-delete-case-valid
  (testing "Deleting existing case should succeed"
    (with-redefs [db/get-case-by-id mock-db-get-case-by-id
                  db/delete-case! mock-db-delete-case]
      (let [request {:params {:id "case-1"}}
            response (case/delete-case request)]
        (is (= 200 (:status response)))
        (is (str/includes? (get-in response [:body :message]) "Case with id case-1 deleted successfully"))))))

(deftest test-delete-case-missing-id
  (testing "Deleting case without id should fail"
    (let [request {:params {}}
          response (case/delete-case request)]
      (is (= 400 (:status response)))
      (is (= "Case id is required" (get-in response [:body :error]))))))

(deftest test-delete-case-blank-id
  (testing "Deleting case with blank id should fail"
    (let [request {:params {:id ""}}
          response (case/delete-case request)]
      (is (= 400 (:status response)))
      (is (= "Case id is required" (get-in response [:body :error]))))))

(deftest test-delete-case-whitespace-id
  (testing "Deleting case with whitespace-only id should fail"
    (let [request {:params {:id "   "}}
          response (case/delete-case request)]
      (is (= 400 (:status response)))
      (is (= "Case id is required" (get-in response [:body :error]))))))

(deftest test-delete-case-not-found
  (testing "Deleting non-existent case should fail"
    (with-redefs [db/get-case-by-id (constantly nil)]
      (let [request {:params {:id "case-999"}}
            response (case/delete-case request)]
        (is (= 404 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "No case found with id case-999"))))))

;; Edge case and integration tests
(deftest test-case-complex-data-structures
  (testing "Cases with complex steps and tags should be handled correctly"
    (with-redefs [db/get-case-by-id (constantly nil)
                  db/get-case-by-name (constantly nil)
                  db/get-project-by-id mock-db-get-project-by-id
                  db/insert-case! mock-db-insert-case]
      (let [complex-steps [{:description "Complex step with special chars: !@#$%^&*()"
                            :precondition "Precondition with\nnewlines and\ttabs"
                            :postcondition "Post condition with unicode: ñáéíóú"}
                           {:description "Very long description that might test character limits and see how the system handles extended text that goes on and on and on..."
                            :precondition ""
                            :postcondition nil}]
            complex-tags ["tag-with-special-chars!@#"
                          "very-long-tag-name-that-might-test-character-limits-and-boundaries"
                          "unicode-tag-ñáéíóú"
                          "tag with spaces"]
            request {:body-params {:id "case-123"
                            :name "Complex Test Case"
                            :project-id "project-1"
                            :description "Description with special chars and unicode: ñáéíóú !@#$%"
                            :steps complex-steps
                            :tags complex-tags}}
            response (case/create-case request)]
        (is (= 201 (:status response)))
        (is (= complex-steps (get-in response [:body :steps])))
        (is (= complex-tags (get-in response [:body :tags])))))))

(deftest test-case-boundary-values
  (testing "Cases with boundary values should work"
    (with-redefs [db/get-case-by-id (constantly nil)
                  db/get-case-by-name (constantly nil)
                  db/get-project-by-id mock-db-get-project-by-id
                  db/insert-case! mock-db-insert-case]
      ;; Empty arrays
      (let [request1 {:body-params {:id "case-123"
                             :name "Empty Arrays Case"
                             :project-id "project-1"
                             :description "Testing empty arrays"
                             :steps []
                             :tags []}}
            response1 (case/create-case request1)]
        (is (= 201 (:status response1)))
        (is (= [] (get-in response1 [:body :steps])))
        (is (= [] (get-in response1 [:body :tags]))))
      
      ;; Single item arrays
      (let [request2 {:body-params {:id "case-124"
                             :name "Single Item Case"
                             :project-id "project-1"
                             :description "Testing single items"
                             :steps [{:description "Single step" :precondition "" :postcondition ""}]
                             :tags ["single-tag"]}}
            response2 (case/create-case request2)]
        (is (= 201 (:status response2)))
        (is (= 1 (count (get-in response2 [:body :steps]))))
        (is (= 1 (count (get-in response2 [:body :tags]))))))))

(deftest test-case-filter-combinations
  (testing "Various filter combinations should work correctly"
    (with-redefs [db/get-cases-filtered mock-db-get-cases-filtered]
      ;; All three filters
      (let [request1 {:query-params {:ids "case-1,case-2"
                                     :names "Test Case One,Test Case Two"
                                     :project-ids "project-1"}}
            response1 (case/get-cases request1)]
        (is (= 200 (:status response1)))
        (is (= 2 (count (:body response1)))))
      
      ;; Two filters that should intersect
      (let [request2 {:query-params {:ids "case-1,case-3"
                                     :project-ids "project-1"}}
            response2 (case/get-cases request2)]
        (is (= 200 (:status response2)))
        (is (= 1 (count (:body response2))))  ;; Only case-1 matches both
        (is (= "case-1" (-> response2 :body first :id))))
      
      ;; Filters with no intersection
      (let [request3 {:query-params {:ids "case-3"
                                     :project-ids "project-1"}}
            response3 (case/get-cases request3)]
        (is (= 200 (:status response3)))
        (is (= 0 (count (:body response3))))))))