(ns xtest.api.user-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clj-http.client :as http]
            [next.jdbc :as jdbc]
            [xtest.api.user :as user]
            [xtest.api.db :as db]
            [xtest.api.core :as core]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import (com.password4j Password)))

(deftest create-user-invalid-password
  (testing "returns 400 when password is invalid"
    (let [response (user/create-user {:body {:first-name "Jane"
                                             :last-name "Doe"
                                             :email "jane@example.com"
                                             :password "short"}})
          body (:body response)
          err-msg (:error body)]
      (is (= 400 (:status response)))
      (is (str/includes? err-msg "Password must be at least 10 characters long")))))

(deftest create-user-valid-password
  (testing "returns 201 and inserts user when password is valid"
    (let [pw "ValidPass1!"
          input {:first-name "John"
                 :last-name "Doe"
                 :email "john.doe@example.com"
                 :password pw}
          inserted-user (atom nil)]
      (with-redefs [db/insert-user! (fn [u] (reset! inserted-user u) u)]
        (let [response (user/create-user {:body input})
              body (:body response)]
          (is (= 201 (:status response)))
          (is (= @inserted-user body))
          (is (string? (:id body)))
          (is (string? (:password body)))
          (is (not= pw (:password body))))))))

(deftest get-users-tests
  (testing "returns all users when no emails param provided"
    (let [users [{:id "1" :first-name "Alice" :last-name "Smith" :email "alice@example.com" :password "hash1"}
                 {:id "2" :first-name "Bob" :last-name "Jones" :email "bob@example.com" :password "hash2"}]]
      (with-redefs [db/get-users (constantly users)]
        (is (= {:status 200 :body users}
               (user/get-users {:query-params {}}))))))
  (testing "returns users by emails when emails param provided"
    (let [users [{:id "1" :first-name "Alice" :last-name "Smith" :email "alice@example.com" :password "hash1"}
                 {:id "3" :first-name "Carol" :last-name "White" :email "carol@example.com" :password "hash3"}]]
      (with-redefs [db/get-users-by-emails (constantly users)]
        (is (= {:status 200 :body users}
               (user/get-users {:query-params {"emails" "alice@example.com,carol@example.com"}}))))))
  (testing "returns empty list when no users found for emails"
    (with-redefs [db/get-users-by-emails (constantly [])]
      (is (= {:status 200 :body []}
             (user/get-users {:query-params {"emails" "nonexistent@example.com"}}))))))

(deftest create-user-and-verify-password-integration
  (testing "creates user via REST API and verifies password matches when fetched by email"
    (let [port 3101
          server-atom (atom nil)
          test-user {:first-name "Integration"
                     :last-name "Test"
                     :email "integration@test.com"
                     :password "TestPass123!"}]
      (try
        ;; Start test server
        (db/init-db!)
        ;; Clear any existing users to ensure clean state
        (try
          (let [db-spec {:jdbcUrl (or (System/getenv "XTDB_JDBC_URL")
                                      "jdbc:postgresql://localhost:5432/xtdb")}
                ds (jdbc/get-datasource db-spec)]
            (jdbc/execute! ds ["DELETE FROM user"]))
          (catch Exception _))
        (reset! server-atom (run-jetty core/app {:port port :join? false}))
        (Thread/sleep 1000) ; Give server time to start
        
        ;; Create user via REST POST
        (let [create-response (http/post (str "http://localhost:" port "/users/create")
                                       {:headers {"Content-Type" "application/json"}
                                        :body (json/write-str test-user)
                                        :as :json})
              created-user (:body create-response)]
          
          ;; Verify user was created successfully
          (is (= 201 (:status create-response)))
          (is (string? (:id created-user)))
          (is (= (:first-name test-user) (:first-name created-user)))
          (is (= (:last-name test-user) (:last-name created-user)))
          (is (= (:email test-user) (:email created-user)))
          (is (not= (:password test-user) (:password created-user))) ; Password should be hashed
          
          ;; Fetch user by email via REST GET
          (let [get-response (http/get (str "http://localhost:" port "/users/get")
                                     {:query-params {"emails" (:email test-user)}
                                      :basic-auth [(:email test-user) (:password test-user)]
                                      :as :json})
                fetched-users (:body get-response)
                fetched-user (first fetched-users)]
            
            ;; Verify user was fetched successfully
            (is (= 200 (:status get-response)))
            (is (= (:_id created-user) (:_id fetched-user)))
            (is (= (:email test-user) (:email fetched-user)))
            
            ;; Verify the stored password hash matches the original password
            (is (-> (Password/check (:password test-user) (:password fetched-user))
                    .withArgon2))))
        
        (finally
          ;; Clean up server
          (when @server-atom
            (.stop @server-atom)))))))

(deftest update-user-integration
  (testing "updates user password via REST API"
    (let [port 3102
          server-atom (atom nil)
          test-user {:first-name "Update"
                     :last-name "Test"
                     :email "update@test.com"
                     :password "OriginalPass123!"}
          new-password "NewPassword456@"]
      (try
        ;; Start test server
        (db/init-db!)
        ;; Clear any existing users to ensure clean state
        (try
          (let [db-spec {:jdbcUrl (or (System/getenv "XTDB_JDBC_URL")
                                      "jdbc:postgresql://localhost:5432/xtdb")}
                ds (jdbc/get-datasource db-spec)]
            (jdbc/execute! ds ["DELETE FROM user"]))
          (catch Exception _))
        (reset! server-atom (run-jetty core/app {:port port :join? false}))
        (Thread/sleep 1000)
        
        ;; Create user first
        (let [create-response (http/post (str "http://localhost:" port "/users/create")
                                       {:headers {"Content-Type" "application/json"}
                                        :body (json/write-str test-user)
                                        :as :json})]
          (is (= 201 (:status create-response)))
          
          ;; Update user password
          (let [update-response (http/post (str "http://localhost:" port "/users/update")
                                         {:headers {"Content-Type" "application/json"}
                                          :basic-auth [(:email test-user) (:password test-user)]
                                          :body (json/write-str {:email (:email test-user)
                                                               :original-password (:password test-user)
                                                               :new-password new-password})
                                          :as :json})]
            (is (= 200 (:status update-response)))
            
            ;; Verify updated password works
            (let [get-response (http/get (str "http://localhost:" port "/users/get")
                                       {:query-params {"emails" (:email test-user)}
                                        :basic-auth [(:email test-user) new-password]
                                        :as :json})
                  fetched-users (:body get-response)
                  fetched-user (first fetched-users)]
              (is (= 200 (:status get-response)))
              (is (-> (Password/check new-password (:password fetched-user))
                      .withArgon2))
              (is (not (-> (Password/check (:password test-user) (:password fetched-user))
                           .withArgon2))))))
        
        (finally
          (when @server-atom
            (.stop @server-atom)))))))

(deftest delete-user-validation
  (testing "returns 400 when id is blank"
    (let [response (user/delete-user {:body {:id ""}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "User id is required"))))
  
  (testing "returns 404 when user not found"
    (with-redefs [db/get-user (constantly nil)]
      (let [response (user/delete-user {:body {:id "nonexistent-id"}})]
        (is (= 404 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "No user found with id nonexistent-id"))))))

(deftest delete-user-success
  (testing "returns 200 and deletes user when valid"
    (let [user-id "test-user-123"
          deleted-result (atom nil)]
      (with-redefs [db/get-user (constantly {:id user-id :email "test@example.com"})
                    db/delete-user! (fn [id] (reset! deleted-result {:next.jdbc/update-count 1}) @deleted-result)]
        (let [response (user/delete-user {:body {:id user-id}})]
          (is (= 200 (:status response)))
          (is (str/includes? (get-in response [:body :message]) "deleted successfully"))
          (is (= 1 (get-in response [:body :rows-affected]))))))))

(deftest login-validation
  (testing "returns 400 when email parameter is missing"
    (let [response (user/login {:body {:password "test123"}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Email parameter is required"))))
  
  (testing "returns 400 when password parameter is missing"
    (let [response (user/login {:body {:email "test@example.com"}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Password parameter is required"))))
  
  (testing "returns 400 when email parameter is blank"
    (let [response (user/login {:body {:email "" :password "test123"}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Email parameter is required"))))
  
  (testing "returns 400 when password parameter is blank"
    (let [response (user/login {:body {:email "test@example.com" :password ""}})]
      (is (= 400 (:status response)))
      (is (str/includes? (get-in response [:body :error]) "Password parameter is required")))))

(deftest login-authentication
  (testing "returns 401 when user not found"
    (with-redefs [db/get-user-by-email (constantly nil)]
      (let [response (user/login {:body {:email "nonexistent@example.com" :password "test123"}})]
        (is (= 401 (:status response)))
        (is (str/includes? (get-in response [:body :error]) "User not found")))))
  
  (testing "returns 401 when password is incorrect"
    (let [valid-hash (-> (Password/hash "correctpassword") .withArgon2 .getResult)
          user {:id "1" :first-name "John" :last-name "Doe" :email "john@example.com" :password valid-hash}]
      (with-redefs [db/get-user-by-email (constantly user)]
        (let [response (user/login {:body {:email "john@example.com" :password "wrongpassword"}})]
          (is (= 401 (:status response)))
          (is (str/includes? (get-in response [:body :error]) "Invalid password"))))))
  
  (testing "returns 200 when credentials are valid - using actual password hashing"
    (let [plain-password "TestPassword123!"
          hashed-password (-> (Password/hash plain-password) .withArgon2 .getResult)
          user {:id "1" :first-name "John" :last-name "Doe" :email "john@example.com" :password hashed-password}]
      (with-redefs [db/get-user-by-email (constantly user)]
        (let [response (user/login {:body {:email "john@example.com" :password plain-password}})]
          (is (= 200 (:status response)))
          (is (str/includes? (get-in response [:body :message]) "Login successful"))
          (is (= "1" (get-in response [:body :user :id])))
          (is (= "John" (get-in response [:body :user :first-name])))
          (is (= "Doe" (get-in response [:body :user :last-name])))
          (is (= "john@example.com" (get-in response [:body :user :email])))
          (is (nil? (get-in response [:body :user :password]))))))))

(deftest login-integration-test
  (testing "login via REST API"
    (let [port 3105
          server-atom (atom nil)
          test-user {:first-name "Login"
                     :last-name "Test"
                     :email "login@test.com"
                     :password "LoginPass123!"}]
      (try
        (db/init-db!)
        (try
          (let [db-spec {:jdbcUrl (or (System/getenv "XTDB_JDBC_URL")
                                      "jdbc:postgresql://localhost:5432/xtdb")}
                ds (jdbc/get-datasource db-spec)]
            (jdbc/execute! ds ["DELETE FROM user"]))
          (catch Exception _))
        (reset! server-atom (run-jetty core/app {:port port :join? false}))
        (Thread/sleep 1000)
        
        ;; Create user first
        (let [create-response (http/post (str "http://localhost:" port "/users/create")
                                       {:headers {"Content-Type" "application/json"}
                                        :body (json/write-str test-user)
                                        :as :json})]
          (is (= 201 (:status create-response)))
          
          ;; Test successful login
          (let [login-response (http/post (str "http://localhost:" port "/users/login")
                                        {:headers {"Content-Type" "application/json"}
                                         :body (json/write-str {:email (:email test-user) 
                                                               :password (:password test-user)})
                                         :as :json})]
            (is (= 200 (:status login-response)))
            (is (str/includes? (get-in login-response [:body :message]) "Login successful"))
            (is (= (:email test-user) (get-in login-response [:body :user :email]))))
          
          ;; Test failed login with wrong password
          (let [failed-response (http/post (str "http://localhost:" port "/users/login")
                                         {:headers {"Content-Type" "application/json"}
                                          :body (json/write-str {:email (:email test-user) 
                                                                :password "wrongpassword"})
                                          :throw-exceptions false})
                parsed-body (if (string? (:body failed-response))
                              (json/read-str (:body failed-response) :key-fn keyword)
                              (:body failed-response))]
            (is (= 401 (:status failed-response)))
            (is (map? parsed-body))
            (is (string? (:error parsed-body)))
            (is (str/includes? (:error parsed-body) "Invalid password")))
          
          ;; Test failed login with nonexistent user
          (let [failed-response (http/post (str "http://localhost:" port "/users/login")
                                         {:headers {"Content-Type" "application/json"}
                                          :body (json/write-str {:email "nonexistent@test.com" 
                                                                :password "anypassword"})
                                          :throw-exceptions false})
                parsed-body (if (string? (:body failed-response))
                              (json/read-str (:body failed-response) :key-fn keyword)
                              (:body failed-response))]
            (is (= 401 (:status failed-response)))
            (is (map? parsed-body))
            (is (string? (:error parsed-body)))
            (is (str/includes? (:error parsed-body) "User not found"))))
        
        (finally
          (when @server-atom
            (.stop @server-atom)))))))