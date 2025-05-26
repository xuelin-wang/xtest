(ns xtest.api.user-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [xtest.api.user :as user]
            [xtest.api.db :as db]))

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
          (is (= pw (:password body))))))))

(deftest get-user-by-email-tests
  (testing "returns 400 when email param is missing"
    (is (= {:status 400 :body {:error "Missing query parameter 'email'"}}
           (user/get-user-by-email {:query-params {}}))))
  (testing "returns 404 when user not found"
    (with-redefs [db/get-user-by-email (constantly nil)]
      (is (= {:status 404 :body {:error "No user found for email test@example.com"}}
             (user/get-user-by-email {:query-params {"email" "test@example.com"}}))))
  (testing "returns 200 with user when user found"
    (let [u {:id "1" :first-name "Alice" :last-name "Smith" :email "alice@example.com" :password "Xyz123!@#"}]
      (with-redefs [db/get-user-by-email (constantly u)]
        (is (= {:status 200 :body u}
               (user/get-user-by-email {:query-params {"email" "alice@example.com"}}))))))))