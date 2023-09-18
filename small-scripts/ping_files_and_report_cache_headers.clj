(ns ping-files-and-report-on-cache-headers
  "The tooling below is used to understand the behavior of the CDN cache.

  Around September 2023, someone noticed that we were getting more
  cache misses than expected. We configure the CDN's cache to hold
  files for one year, but we often notice that the browser receives
  video files that indicate a cache miss.

  On this project I will:

  - configure a pooling loop
  - at each iteration I will ping a file from CDN and:
     - inspect the response headers
     - take note of caches hit and miss

  The study above will be done for the diffent kids of files
  downloaded when reproducing a video: video, audio, manifest, and
  subtitle files."
  (:require [clj-http.client :as client]
            [clojure.string]
            [java-time.api :as jt]))

(def base-media-url "https://stream.brasilparalelo.com.br/gdi_ep01_chesterton_2160p_3840x2160_h264-j4tnt6q3n3-cache-version-2/mpd/")

(def files ["audio/en/mp4a/init.mp4"
            "video/avc1/3/init.mp4"
            "audio/en/mp4a/seg-697.m4s"
            "video/avc1/3/seg-697.m4s"])

(defn extract-measurement [{:keys [request-time headers status] :as http-response}]
  (let [interesting-headers (select-keys headers ["X-Cache" "Age" "CF-Cache-Status" "Cache-Control"])
        data (merge interesting-headers {"status" status "request-time" request-time})]
    (sort-by first (into [] data))))


(defn resource-url [resource] (str base-media-url resource))

(defn string-vec-as-csv-string [xs]
  (->> xs
       (map #(or % ""))
       (map #(clojure.string/replace % "," ""))
       (clojure.string/join ",")))

(defn print-data [data]
  (let [row (->> data
                 (map second)
                 string-vec-as-csv-string)]
    (print row)))

(defn report []
  (doseq [f files]
    (let [url (resource-url f)
          res (client/get url)
          data (extract-measurement res)]
      (print-data data)
      (print ",")))
  (print "\n"))

(defn millis-since! [start-time]
  (jt/as (jt/duration start-time (jt/instant)) :seconds))

(def base-header-0 files)

(def base-header-1 (->> (client/get (resource-url (first files)))
                        extract-measurement
                        (map first)))

(def header-0 (->> base-header-0
                   (mapcat (fn [h] (cons h (repeat (- (count base-header-1) 1) ""))))))

(def header-1 (->> base-header-0
                   (mapcat (fn [_] base-header-1))))

(defn print-headers []
  (print "Time,")
  (print (string-vec-as-csv-string header-0))
  (print "\n")
  (print ",")
  (print (string-vec-as-csv-string header-1))
  (print "\n"))

(defn run-report []
  (print-headers)
  (let [start-time (jt/instant)]
    (while true
      (print (millis-since! start-time) ",")
      (report)
      (flush)
      (Thread/sleep 5000))))

(run-report)
