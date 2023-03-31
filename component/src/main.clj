(ns main
  (:require [com.stuartsierra.component :as component]))

(defrecord InMemoryDB [filename]
  component/Lifecycle

  (start [component]
    (prn "Starting in memory database")
    (assoc component :database {:-config filename}))
  (stop [component]
    (prn "Stopping in memory database")
    (assoc component :database nil))
  )

(defn new-inmemory-db [filename]
  (map->InMemoryDB {:filename filename}))

(defn controller [db action params]
  (println "State of the database:" (:database db)))

(def config-options {:db-file "/tmp/fobar.db"})

(defn app-system [options]
  (let [{db-file :db-file} options]
    (component/system-map
     :db (new-inmemory-db db-file))))

;;; In prod you could start the app like this:
;;;; (component/start (main/app-system {:db-file "/tmp/foobar.db"}))


;;; In dev, you would do this:
(def system (app-system {:host "dbhost.com" :port 123}))
(alter-var-root #'system component/start)
(alter-var-root #'system component/stop)
;;; On the rpl you can inspect ~system~ to find what is going on
;;; You could also execute the 2 alter-var-root commands when you want
;;; restart the server
