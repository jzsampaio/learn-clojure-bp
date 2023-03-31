(ns hello-component.core
  "This module was written to be loaded part by part on a repl"
  (:require [com.stuartsierra.component :as component]))

(defrecord MyFirstRecord [name last-name]
  MyFirstProtocol
  (hitme [this] (str "I punch you in the face " (:name this)))
  (hitme-again [this n] (map (constantly (hitme this)) (range 2))))

(comment
  "Following is how you can call methods of the record:"
  (hitme (map->MyFirstRecord {:name "Juarez" :last-name "Filho"}))
  (hitme-again (map->MyFirstRecord {:name "Juarez" :last-name "Filho"}) 2))

(defprotocol Printer
  (print [this s]))

(defrecord ConsolePrinter [config]
  Printer
  (print [this s]
    (prn (str (get-in this [:config :prefix]) s)))

  component/Lifecycle
  (start [component]
    (prn "Initializing ConsolePrinter")
    component)

  (stop [component]
    (prn "Finishing ConsolePrinter")
    component))

(comment
  "Example of how to call my console printer"
  (print (ConsolePrinter. {:prefix "[jz] "}) "Ola mundo"))

(defn sleep-print-loop [sleep-ms {:keys [printer] :as component}]
  (while true
    (print printer "Ola mundo")
    (Thread/sleep sleep-ms)))

(defrecord BackgroundLoop [config printer]
  component/Lifecycle
  (start [component]
    (prn "Initializing background loop")
    (let [th (doto (Thread. #(sleep-print-loop (:sleep-ms config) component))
               (.setDaemon true)
               (.start))]
      (assoc component
             :backgroud-thread th)))
  (stop [component]
    (prn "Stopping background thread")
    (.stop (:backgroud-thread component))
    (assoc component :backgroud-thread nil)))

;; This is a global variable which will store a reference to system components
(defonce local-system nil)

(defn init []
  (let [config {:prefix "[jz] "
                :sleep-ms 500}
        system (component/system-map
                :printer (ConsolePrinter. config)
                :background-loop (component/using (map->BackgroundLoop {:config config}) [:printer]))]
    (alter-var-root #'local-system (constantly system))))

(defn- start []
  (alter-var-root #'local-system component/start))

(defn- stop []
  (when local-system
    (alter-var-root #'local-system component/stop)))

(comment
  (init)
  "This is how local-system looks like after running init:"
  {:printer {:config {:prefix "[jz] ", :sleep-ms 500}},
   :background-loop
   {:config {:prefix "[jz] ", :sleep-ms 500}, :printer nil}}

  (start)
  "This is how the system map loooks like after running start"
  {:printer {:config {:prefix "[jz] ", :sleep-ms 500}},
   :background-loop
   {:config  {:prefix "[jz] ", :sleep-ms 500},
    :printer {:config {:prefix "[jz] ", :sleep-ms 500}},
    :backgroud-thread
    #object[java.lang.Thread 0x21546600 "Thread[Thread-182,5,main]"]}}
  
  (stop)
  "This is how the system looks like after running stop"
  {:printer {:config {:prefix "[jz] ", :sleep-ms 500}},
   :background-loop
   {:config           {:prefix "[jz] ", :sleep-ms 500},
    :printer          {:config {:prefix "[jz] ", :sleep-ms 500}},
    :backgroud-thread nil}})
