(ns main
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :as test]))

(defn response [status body & {:as headers}]
  {:status status :body body :headers headers})

(def ok (partial response 200))

(def created (partial response 201))

(def accepted (partial response 202))

(defonce database (atom {}))

;;; if ctx.tx-data exists it should be a [fn & args] form
;;; fn is a function will be called with args [database  ...args]
(def db-interceptor
  {:name :database-interceptor
   :enter
   (fn [ctx]
     ; injects into ctx.request.database the current value of the database
     (update ctx :request assoc :database @database))
   :leave
   (fn [ctx]
     (if-let [[op & args] (:tx-data ctx)]
       (do
         (apply swap! database op args)
         (assoc-in ctx [:request :database] @database))
       ctx))})

(defn make-list [nm]
  {:name nm :items {}})

(defn make-list-item [nm]
  {:name nm :done? false})

(def list-create
  {:name :list-create
   :enter
   (fn [ctx]
     (let [nm (get-in ctx [:request :query-params :name] "Unnamed List")
           new-list (make-list nm)
           db-id (str (gensym "l"))
           url (route/url-for :list-view :params {:list-id db-id})]
       (assoc ctx
              :response (created new-list "Location" url)
              :tx-data [assoc db-id new-list])))})

(defn find-list-by-id [dbval db-id]
  (get dbval db-id))

(def list-view
  {:name :list-view
   :enter
   (fn [ctx]
     (if-let [db-id (get-in ctx [:request :path-params :list-id])]
       (if-let [the-list (find-list-by-id (get-in ctx [:request :database]) db-id)]
         (assoc ctx :result the-list)
         ctx)
       ctx))})

;;; An object placedat ctx.result will be rendered with an HTTP OK
(def entity-render
  {:name :entity-render
   :leave
   (fn [ctx]
     (if-let [item (:result ctx)]
       (assoc ctx :response (ok item))
       ctx))})

(defn find-list-item-by-ids [dbval list-id item-id]
  (get-in dbval [list-id :items item-id] nil))

(defn from-path-params
  ([ctx k]
   (get-in ctx [:request :path-params k]))
  ([ctx k default]
   (get-in ctx [:request :path-params k] default)))

(defn from-query-params
  ([ctx k]
   (get-in ctx [:request :query-params k]))
  ([ctx k default]
   (get-in ctx [:request :query-params k] default)))


(def list-item-view
  {:name :list-item-view
   :leave
   (fn [ctx]
     ;;; Extracts out of ctx.request.path-params the values of list-id
     ;;; and item-id. If (list-id, item-id) can be located on the
     ;;; database, adds the corresponding item to ctx.result
     (if-let [list-id (from-path-params ctx :list-id)]
       (if-let [item-id (from-path-params ctx :item-id)]
         (if-let [item (find-list-item-by-ids
                        (get-in ctx [:request :database])
                        list-id
                        item-id)]
           (assoc ctx :result item)
           ctx)
         ctx)
       ctx))})

(defn list-item-add
  [dbval list-id item-id new-item]
  (if (contains? dbval list-id)
    (assoc-in dbval [list-id :items item-id] new-item)
    dbval))

(def list-item-create
  {:name :list-item-create
   :enter
   (fn [ctx]
     (if-let [list-id (from-path-params ctx :list-id)]
       (let [nm (from-query-params ctx :name "Unnamed Item")
             new-item (make-list-item nm)
             item-id (str (gensym "i"))]
         (-> ctx
             (assoc :tx-data [list-item-add list-id item-id new-item])
             (assoc-in [:request :path-params :item-id] item-id)))
       ctx))})

(def echo
  {:name :echo
   :enter
   (fn [ctx]
     (let [request (:request ctx)
           response (ok ctx)]
       (assoc ctx :response response)))})

(def routes
  (route/expand-routes
   #{["/todo" :post [db-interceptor list-create]]
     ["/todo" :get echo :route-name :list-query-form]
     ["/todo/:list-id" :get [entity-render db-interceptor list-view]]
     ["/todo/:list-id" :post [entity-render list-item-view db-interceptor db-interceptor list-item-create]]
     ["/todo/:list-id/:item-id" :get [entity-render list-item-view db-interceptor]]
     ["/todo/:list-id/:item-id" :put echo :route-name :list-item-update]
     ["/todo/:list-id/:item-id" :delete echo :route-name :list-item-delete]}))

(def service-map
  {::http/routes routes
   ::http/type :jetty
   ::http/port 8088})

(defn start []
  (http/start (http/create-server service-map)))

(defonce server (atom nil))

(defn start-dev []
  (reset! server
          (http/start (http/create-server
                       (assoc service-map
                              ::http/join? false)))))

(defn stop-dev []
  (http/stop @server))

(defn restart []
  (stop-dev)
  (start-dev))

;;; Tests

(defn test-request [verb url]
  (io.pedestal.test/response-for (::http/service-fn @server) verb url))
