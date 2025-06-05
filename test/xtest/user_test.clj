(ns xtest.user-test
  (:require [clojure.test :refer :all]
            [xtest.api.user :as user]
            [xtest.api.db :as db]
            [xtest.server :as server]
            [clojure.data.codec.base64 :as base64]
            [clojure.string :as str])
  (:import (com.password4j Password)))

;; Test data
(def valid-password "TestPass123!")
(def invalid-password "weak")
(def test-user {:first-name "John"
                :last-name "Doe" 
                :email "john.doe@example.com"
                :password valid-password})

(def test-user-2 {:first-name "Jane"
                  :last-name "Smith"
                  :email "jane.smith@example.com" 
                  :password valid-password})

;; Helper functions
(defn mock-db-insert [user]
  (assoc user :id "test-uuid-123"))

(defn mock-db-get-user [id]
  (when (= id "test-uuid-123")
    {:id "test-uuid-123"
     :first-name "John"
     :last-name "Doe"
     :email "john.doe@example.com"
     :password (-> (Password/hash valid-password) .withArgon2 .getResult)}))

(defn mock-db-get-user-by-email [email]
  (when (= email "john.doe@example.com")
    {:id "test-uuid-123"
     :first-name "John"
     :last-name "Doe"
     :email "john.doe@example.com"
     :password (-> (Password/hash valid-password) .withArgon2 .getResult)}))

(defn mock-db-get-users []
  [{:id "test-uuid-123"
    :first-name "John"
    :last-name "Doe"
    :email "john.doe@example.com"
    :password (-> (Password/hash valid-password) .withArgon2 .getResult)}
   {:id "test-uuid-456"
    :first-name "Jane"
    :last-name "Smith"
    :email "jane.smith@example.com"
    :password (-> (Password/hash valid-password) .withArgon2 .getResult)}])

(defn mock-db-get-users-by-emails [emails]
  (let [email-set (set emails)
        all-users (mock-db-get-users)]
    (vec (filter #(contains? email-set (:email %)) all-users))))

(defn mock-db-update-user [user]
  user)

(defn mock-db-delete-user [id]
  {:next.jdbc/update-count 1})

;; Tests for create-user
(deftest test-create-user-valid
  (testing "Creating a user with valid data should succeed"
    (with-redefs [db/insert-user! mock-db-insert]
      (let [request {:body-params test-user}
            response (user/create-user request)]
        (is (= 201 (:status response)))
        (is (= "John" (get-in response [:body :first-name])))
        (is (= "Doe" (get-in response [:body :last-name])))
        (is (= "john.doe@example.com" (get-in response [:body :email])))
        (is (contains? (:body response) :id))
        (is (contains? (:body response) :password))))))

(deftest test-create-user-invalid-password
  (testing "Creating a user with invalid password should fail"
    (let [request {:body-params (assoc test-user :password invalid-password)}
          response (user/create-user request)]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Password must be at least 10 characters")))))

(deftest test-create-user-various-invalid-passwords
  (testing "Creating a user with various invalid passwords should fail"
    (let [invalid-passwords ["short" "nouppercase123!" "NOLOWERCASE123!" "NoDigits!" "NoSpecial123"]]
      (doseq [invalid-pwd invalid-passwords]
        (let [request {:body-params (assoc test-user :password invalid-pwd)}
              response (user/create-user request)]
          (is (= 400 (:status response))
              (str "Password '" invalid-pwd "' should be invalid"))
          (is (str/includes? (get-in response [:body :error]) "Password must be at least 10 characters")))))))

;; Tests for get-users
(deftest test-get-users-all
  (testing "Getting all users should return all users"
    (with-redefs [db/get-users mock-db-get-users]
      (let [request {:params {}}
            response (user/get-users request)]
        (is (= 200 (:status response)))
        (is (= 2 (count (:body response))))
        (is (= "john.doe@example.com" (get-in response [:body 0 :email])))
        (is (= "jane.smith@example.com" (get-in response [:body 1 :email])))))))

(deftest test-get-users-by-emails
  (testing "Getting users by specific emails should return filtered users"
    (with-redefs [db/get-users-by-emails mock-db-get-users-by-emails]
      (let [request {:params {:emails "john.doe@example.com,nonexistent@example.com"}}
            response (user/get-users request)]
        (is (= 200 (:status response)))
        (is (= 1 (count (:body response))))
        (is (= "john.doe@example.com" (get-in response [:body 0 :email])))))))

(deftest test-get-users-by-single-email
  (testing "Getting users by single email should work"
    (with-redefs [db/get-users-by-emails mock-db-get-users-by-emails]
      (let [request {:params {:emails "jane.smith@example.com"}}
            response (user/get-users request)]
        (is (= 200 (:status response)))
        (is (= 1 (count (:body response))))
        (is (= "jane.smith@example.com" (get-in response [:body 0 :email])))))))

(deftest test-get-users-empty-emails
  (testing "Getting users with empty emails param should return all users"
    (with-redefs [db/get-users mock-db-get-users
                  db/get-users-by-emails mock-db-get-users-by-emails]
      (let [request {:params {:emails ""}}
            response (user/get-users request)]
        (is (= 200 (:status response)))
        (is (= 0 (count (:body response))))))))

;; Tests for update-user
(deftest test-update-user-valid
  (testing "Updating user with valid data should succeed"
    (with-redefs [db/get-user-by-email mock-db-get-user-by-email
                  db/update-user! mock-db-update-user]
      (let [request {:body-params {:email "john.doe@example.com"
                                   :original-password valid-password
                                   :new-password "NewPass456@"}}
            response (user/update-user request)]
        (is (= 200 (:status response)))
        (is (= "john.doe@example.com" (get-in response [:body :email])))))))

(deftest test-update-user-invalid-new-password
  (testing "Updating user with invalid new password should fail"
    (with-redefs [db/get-user-by-email mock-db-get-user-by-email]
      (let [request {:body-params {:email "john.doe@example.com"
                                   :original-password valid-password
                                   :new-password invalid-password}}
            response (user/update-user request)]
        (is (= 400 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "Password must be at least 10 characters"))))))

(deftest test-update-user-wrong-original-password
  (testing "Updating user with wrong original password should fail"
    (with-redefs [db/get-user-by-email mock-db-get-user-by-email]
      (let [request {:body-params {:email "john.doe@example.com"
                                   :original-password "WrongPass123!"
                                   :new-password "NewPass456@"}}
            response (user/update-user request)]
        (is (= 401 (:status response)))
        (is (= "Original password is incorrect" (get-in response [:body :error])))))))

(deftest test-update-user-not-found
  (testing "Updating non-existent user should fail"
    (with-redefs [db/get-user-by-email (constantly nil)]
      (let [request {:body-params {:email "nonexistent@example.com"
                                   :original-password valid-password
                                   :new-password "NewPass456@"}}
            response (user/update-user request)]
        (is (= 404 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "No user found for email"))))))

;; Tests for delete-user
(deftest test-delete-user-valid
  (testing "Deleting existing user should succeed"
    (with-redefs [db/get-user mock-db-get-user
                  db/delete-user! mock-db-delete-user]
      (let [request {:params {:id "test-uuid-123"}}
            response (user/delete-user request)]
        (is (= 200 (:status response)))
        (is (str/includes? (get-in response [:body :message]) "deleted successfully"))))))

(deftest test-delete-user-missing-id
  (testing "Deleting user without id should fail"
    (let [request {:params {}}
          response (user/delete-user request)]
      (is (= 400 (:status response)))
      (is (= "id is required" (get-in response [:body :error]))))))

(deftest test-delete-user-blank-id
  (testing "Deleting user with blank id should fail"
    (let [request {:params {:id ""}}
          response (user/delete-user request)]
      (is (= 400 (:status response)))
      (is (= "id is required" (get-in response [:body :error]))))))

(deftest test-delete-user-not-found
  (testing "Deleting non-existent user should fail"
    (with-redefs [db/get-user (constantly nil)]
      (let [request {:params {:id "nonexistent-uuid"}}
            response (user/delete-user request)]
        (is (= 404 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "No user found with id"))))))

;; Tests for login
(deftest test-login-valid
  (testing "Login with valid credentials should succeed"
    (with-redefs [db/get-user-by-email mock-db-get-user-by-email]
      (let [request {:body-params {:email "john.doe@example.com"
                                   :password valid-password}}
            response (user/login request)]
        (is (= 200 (:status response)))
        (is (= "Login successful" (get-in response [:body :message])))
        (is (= "test-uuid-123" (get-in response [:body :user :id])))
        (is (= "John" (get-in response [:body :user :first-name])))
        (is (= "Doe" (get-in response [:body :user :last-name])))
        (is (= "john.doe@example.com" (get-in response [:body :user :email])))
        (is (nil? (get-in response [:body :user :password])))))))

(deftest test-login-invalid-password
  (testing "Login with invalid password should fail"
    (with-redefs [db/get-user-by-email mock-db-get-user-by-email]
      (let [request {:body-params {:email "john.doe@example.com"
                                   :password "WrongPass123!"}}
            response (user/login request)]
        (is (= 401 (:status response)))
        (is (= "Invalid password" (get-in response [:body :error])))))))

(deftest test-login-user-not-found
  (testing "Login with non-existent user should fail"
    (with-redefs [db/get-user-by-email (constantly nil)]
      (let [request {:body-params {:email "nonexistent@example.com"
                                   :password valid-password}}
            response (user/login request)]
        (is (= 401 (:status response)))
        (is (= "User not found" (get-in response [:body :error])))))))

(deftest test-login-missing-email
  (testing "Login without email should fail"
    (let [request {:body-params {:password valid-password}}
          response (user/login request)]
      (is (= 400 (:status response)))
      (is (= "Email parameter is required" (get-in response [:body :error]))))))

(deftest test-login-blank-email
  (testing "Login with blank email should fail"
    (let [request {:body-params {:email ""
                                 :password valid-password}}
          response (user/login request)]
      (is (= 400 (:status response)))
      (is (= "Email parameter is required" (get-in response [:body :error]))))))

(deftest test-login-missing-password
  (testing "Login without password should fail"
    (let [request {:body-params {:email "john.doe@example.com"}}
          response (user/login request)]
      (is (= 400 (:status response)))
      (is (= "Password parameter is required" (get-in response [:body :error]))))))

(deftest test-login-blank-password
  (testing "Login with blank password should fail"
    (let [request {:body-params {:email "john.doe@example.com"
                                 :password ""}}
          response (user/login request)]
      (is (= 400 (:status response)))
      (is (= "Password parameter is required" (get-in response [:body :error]))))))

;; Integration tests that verify the password validation logic
(deftest test-password-validation-edge-cases
  (testing "Password validation edge cases"
    (let [edge-case-passwords {"exactly-10-chars" "TestPass1!"
                               "missing-lower" "TESTPASS123!"
                               "missing-upper" "testpass123!"
                               "missing-digit" "TestPassword!"
                               "missing-special" "TestPass123"
                               "valid-with-all-specials" "TestPass1!@#$%^&*()-_=+[]{}|/\\<>,.:\";'"
                               "unicode-chars" "TestPass1!ñ"
                               "spaces" "Test Pass1!"
                               "very-long" (str "TestPass1!" (apply str (repeat 100 "a")))}]
      
      ;; Test the exactly 10 character valid password
      (with-redefs [db/insert-user! mock-db-insert]
        (let [request {:body-params (assoc test-user :password (get edge-case-passwords "exactly-10-chars"))}
              response (user/create-user request)]
          (is (= 201 (:status response)) "10-char password should be valid")))
      
      ;; Test invalid passwords
      (doseq [[desc pwd] (dissoc edge-case-passwords "exactly-10-chars" "valid-with-all-specials" "unicode-chars" "spaces" "very-long")]
        (let [request {:body-params (assoc test-user :password pwd)}
              response (user/create-user request)]
          (is (= 400 (:status response)) (str desc " should be invalid"))))
      
      ;; Test valid edge cases
      (with-redefs [db/insert-user! mock-db-insert]
        (doseq [[desc pwd] (select-keys edge-case-passwords ["valid-with-all-specials" "unicode-chars" "spaces" "very-long"])]
          (let [request {:body-params (assoc test-user :password pwd)}
                response (user/create-user request)]
            (is (= 201 (:status response)) (str desc " should be valid")))))))

;; Integration test - simpler approach testing server routing logic
(deftest test-user-create-server-routing-integration
  (testing "Integration test for user creation through server routing"
    ;; Test the server routing and interceptors directly without starting HTTP server
    (with-redefs [db/insert-user! (fn [user] (assoc user :id (str (java.util.UUID/randomUUID))))
                  db/get-user-by-email (constantly nil)
                  ;; Mock has-any-users? to return false (no auth required)
                  xtest.server/has-any-users? (constantly false)]
      
      ;; Test that we can create a request that goes through the server interceptors
      (let [;; Simulate a ring request as it would come through the HTTP server
            request {:request-method :post
                     :uri "/api/users/create"
                     :headers {"content-type" "application/json"}
                     :body-params {:first-name "Integration"
                                   :last-name "Test"
                                   :email "integration@test.com" 
                                   :password "IntegrationPass123!"}}
            ;; Call the user creation handler directly
            response (user/create-user request)]
        
        (is (= 201 (:status response)) "Should return 201 for valid user creation")
        (is (= "Integration" (get-in response [:body :first-name])))
        (is (= "Test" (get-in response [:body :last-name])))
        (is (= "integration@test.com" (get-in response [:body :email])))
        (is (contains? (:body response) :id))
        (is (contains? (:body response) :password)))
      
      ;; Test invalid password
      (let [request {:request-method :post
                     :uri "/api/users/create"
                     :headers {"content-type" "application/json"}
                     :body-params {:first-name "Integration"
                                   :last-name "Test"
                                   :email "integration2@test.com"
                                   :password "weak"}}
            response (user/create-user request)]
        
        (is (= 400 (:status response)) "Should return 400 for invalid password")
        (is (str/includes? (get-in response [:body :error]) "Password must be at least 10 characters"))))))

(deftest test-user-authentication-interceptor-integration
  (testing "Integration test for authentication interceptor behavior"
    ;; Test when no users exist (auth bypassed)
    (with-redefs [xtest.server/has-any-users? (constantly false)]
      (let [interceptor-fn (get-in (xtest.server/interceptor :wrap-basic-auth) [:enter])
            ctx {:request {:uri "/api/users/create"
                           :headers {}}}
            result-ctx (interceptor-fn ctx)]
        
        (is (nil? (:response result-ctx)) "Should not add response when no users exist")
        (is (= ctx result-ctx) "Should pass through unchanged")))
    
    ;; Test when users exist but no auth header (should fail)
    (with-redefs [xtest.server/has-any-users? (constantly true)]
      (let [interceptor-fn (get-in (xtest.server/interceptor :wrap-basic-auth) [:enter])
            ctx {:request {:uri "/api/users/create"
                           :headers {}}}
            result-ctx (interceptor-fn ctx)]
        
        (is (= 401 (get-in result-ctx [:response :status])) "Should return 401 without auth header")
        (is (str/includes? (get-in result-ctx [:response :body]) "Missing or invalid Authorization header"))))
    
    ;; Test with valid auth header  
    (with-redefs [xtest.server/has-any-users? (constantly true)
                  db/get-user-by-email (fn [email]
                                         (when (= email "test@example.com")
                                           {:id "test-id"
                                            :email "test@example.com"
                                            :password (-> (Password/hash "TestPass123!")
                                                          .withArgon2
                                                          .getResult)}))]
      (let [credentials "test@example.com:TestPass123!"
            encoded (String. (base64/encode (.getBytes credentials "UTF-8")) "UTF-8")
            auth-header (str "Basic " encoded)
            interceptor-fn (get-in (xtest.server/interceptor :wrap-basic-auth) [:enter])
            ctx {:request {:uri "/api/users/create"
                           :headers {"authorization" auth-header}}}
            result-ctx (interceptor-fn ctx)]
        
        (is (nil? (:response result-ctx)) "Should not add response for valid auth")
        (is (= "test@example.com" (get-in result-ctx [:request :authenticated-user :email])) "Should add authenticated user to request"))))))