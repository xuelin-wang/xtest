(ns xtest.api.util)

(defn replace_uderscore_id [m]
  "Replaces '_id' key in the map with 'id' key.
   Returns a new map with 'id' instead of '_id'."
  (if (contains? m :_id)
    (-> m
        (assoc :id (:_id m))
        (dissoc :_id))
    m))