(ns compojure.api.meta
  (:require [clojure.walk :refer [keywordize-keys]]
            [compojure.api.common :refer :all]
            [compojure.api.middleware :as mw]
            [compojure.api.exception :as ex]
            [compojure.core :refer [routes]]
            [plumbing.core :refer :all]
            [plumbing.fnk.impl :as fnk-impl]
            [ring.swagger.common :refer :all]
            [ring.swagger.json-schema :as js]
            [ring.util.http-response :refer [internal-server-error]]
            [schema.core :as s]
            [schema.coerce :as sc]
            [schema.utils :as su]
            [schema-tools.core :as st]
            [linked.core :as linked]
            [compojure.api.impl.logging :as logging]))

;;
;; Meta Evil
;;

(def +compojure-api-request+
  "lexically bound ring-request for handlers."
  '+compojure-api-request+)

(def +compojure-api-meta+
  "lexically bound meta-data for handlers."
  '+compojure-api-meta+)

(def +compojure-api-coercer+
  "lexically bound (caching) coercer for handlers."
  '+compojure-api-coercer+)

(defmacro meta-container [meta & form]
  `(let [accumulated-meta# (get-local +compojure-api-meta+)
         ~'+compojure-api-meta+ (deep-merge accumulated-meta# ~meta)]
     ~@form))

(defn unwrap-meta-container [container]
  {:post [(map? %)]}
  (or
    (if (sequential? container)
      (let [[sym meta-data] container]
        (if (and (symbol? sym) (= #'meta-container (resolve sym)))
          meta-data)))
    {}))

(def meta-container? #{#'meta-container})

;;
;; Schema
;;

(defn memoized-coercer
  "Returns a memoized version of a referentially transparent coercer fn. The
  memoized version of the function keeps a cache of the mapping from arguments
  to results and, when calls with the same arguments are repeated often, has
  higher performance at the expense of higher memory use. FIFO with 100 entries.
  Cache will be filled if anonymous coercers are used (does not match the cache)"
  []
  (let [cache (atom (linked/map))
        cache-size 100]
    (fn [& args]
      (or (@cache args)
          (let [coercer (apply sc/coercer args)]
            (swap! cache (fn [mem]
                           (let [mem (assoc mem args coercer)]
                             (if (>= (count mem) cache-size)
                               (dissoc mem (-> mem first first))
                               mem))))
            coercer)))))

(defn strict [schema]
  (dissoc schema 'schema.core/Keyword))

(defn fnk-schema [bind]
  (:input-schema
    (fnk-impl/letk-input-schema-and-body-form
      nil (with-meta bind {:schema s/Any}) [] nil)))

(defn body-coercer-middleware [handler coercer responses]
  (fn [request]
    (if-let [{:keys [status] :as response} (handler request)]
      (if-let [schema (:schema (responses status))]
        (if-let [matcher (:response (mw/get-coercion-matcher-provider request))]
          (let [coerce (coercer (value-of schema) matcher)
                body (coerce (:body response))]
            (if (su/error? body)
              (throw (ex-info "Response validation error"
                              (assoc body :type ::ex/response-validation)))
              (assoc response
                ::serializable? true
                :body body)))
          response)
        response))))

(s/defn src-coerce!
  "Return source code for coerce! for a schema with coercion type,
   extracted from a key in a ring request."
  [schema, key, type :- mw/CoercionType]
  (assert (not (#{:query :json} type)) (str type " is DEPRECATED since 0.22.0. Use :body or :string instead."))
  `(let [value# (keywordize-keys (~key ~+compojure-api-request+))]
     (if-let [matcher# (~type (mw/get-coercion-matcher-provider ~+compojure-api-request+))]
       (let [coerce# (~+compojure-api-coercer+ ~schema matcher#)
             result# (coerce# value#)]
         (if (su/error? result#)
           (throw (ex-info "Request validation failed" (assoc result# :type ::ex/request-validation)))
           result#))
       value#)))

(defn- convert-return [schema]
  {200 {:schema schema
        :description (or (js/json-schema-meta schema) "")}})

(defn ensure-new-format! [responses]
  (doseq [v (vals responses)
          :let [deprecated? (cond
                              (nil? v) false
                              (not (map? v)) true
                              (or (:schema v)
                                  (:description v)
                                  (:headers v)) false
                              :else true)]
          :when deprecated?]
    (throw
      (IllegalArgumentException.
        (str
          "You are using an old format with :responses. Since Compojure-api 0.21.0, "
          "plain ring-swagger 2.0 models are used. Example:\n\n"
          ":responses {400 nil}\n"
          ":responses {400 {:schema ErrorSchema}}\n"
          ":responses {400 {:schema ErrorSchema, :description \"Error\"}}\n\n"
          "You had:\n\n:responses " responses "\n\n")))))

;;
;; Extension point
;;

(defmulti restructure-param
  "Restructures a key value pair in smart routes. By default the key
  is consumed form the :parameters map in acc. k = given key, v = value."
  (fn [k v acc] k))

;;
;; Pass-through swagger metadata
;;

(defmethod restructure-param :summary [k v acc]
  (update-in acc [:parameters] assoc k v))

(defmethod restructure-param :description [k v acc]
  (update-in acc [:parameters] assoc k v))

(defmethod restructure-param :operationId [k v acc]
  (update-in acc [:parameters] assoc k v))

(defmethod restructure-param :consumes [k v acc]
  (update-in acc [:parameters] assoc k v))

(defmethod restructure-param :produces [k v acc]
  (update-in acc [:parameters] assoc k v))

;;
;; Smart restructurings
;;

; Boolean to discard the route out from api documentation
; Example:
; :no-doc true
(defmethod restructure-param :no-doc [_ v acc]
  (update-in acc [:parameters] assoc :x-no-doc v))

; publishes the data as swagger-parameters without any side-effects / coercion.
; Examples:
; :swagger {:responses {200 {:schema User}
;                       404 {:schema Error
;                            :description "Not Found"} }
;           :paramerers {:query {:q s/Str}
;                        :body NewUser}}}
(defmethod restructure-param :swagger [_ swagger acc]
  (update-in acc [:parameters] deep-merge swagger))

; Route name, used with path-for
; Example:
; :name :user-route
(defmethod restructure-param :name [_ v acc]
  (update-in acc [:parameters] assoc :x-name v))

; Tags for api categorization. Ignores duplicates.
; Examples:
; :tags [:admin]
(defmethod restructure-param :tags [_ tags acc]
  (update-in acc [:parameters :tags] (comp set into) tags))

; Defines a return type and coerces the return value of a body against it.
; Examples:
; :return MySchema
; :return {:value String}
; :return #{{:key (s/maybe Long)}}
(defmethod restructure-param :return [_ schema acc]
  (let [response (convert-return schema)]
    (-> acc
        (update-in [:parameters :responses] merge response)
        (update-in [:responses] merge response))))

; value is a map of http-response-code -> Schema. Translates to both swagger
; parameters and return schema coercion. Schemas can be decorated with meta-data.
; Examples:
; :responses {403 nil}
; :responses {403 {:schema ErrorEnvelope}}
; :responses {403 {:schema ErrorEnvelope, :description \"Underflow\"}}

(defmethod restructure-param :responses [_ responses acc]
  (ensure-new-format! responses)
  (-> acc
      (update-in [:parameters :responses] merge responses)
      (update-in [:responses] merge responses)))

; reads body-params into a enchanced let. First parameter is the let symbol,
; second is the Schema to coerced! against.
; Examples:
; :body [user User]
(defmethod restructure-param :body [_ [value schema] acc]
  (-> acc
      (update-in [:lets] into [value (src-coerce! schema :body-params :body)])
      (assoc-in [:parameters :parameters :body] schema)))

; reads query-params into a enchanced let. First parameter is the let symbol,
; second is the Schema to coerced! against.
; Examples:
; :query [user User]
(defmethod restructure-param :query [_ [value schema] acc]
  (-> acc
      (update-in [:lets] into [value (src-coerce! schema :query-params :string)])
      (assoc-in [:parameters :parameters :query] schema)))

; reads header-params into a enchanced let. First parameter is the let symbol,
; second is the Schema to coerced! against.
; Examples:
; :headers [headers Headers]
(defmethod restructure-param :headers [_ [value schema] acc]
  (-> acc
      (update-in [:lets] into [value (src-coerce! schema :headers :string)])
      (assoc-in [:parameters :parameters :header] schema)))

; restructures body-params with plumbing letk notation. Example:
; :body-params [id :- Long name :- String]
(defmethod restructure-param :body-params [_ body-params acc]
  (let [schema (strict (fnk-schema body-params))]
    (-> acc
        (update-in [:letks] into [body-params (src-coerce! schema :body-params :body)])
        (assoc-in [:parameters :parameters :body] schema))))

; restructures form-params with plumbing letk notation. Example:
; :form-params [id :- Long name :- String]
(defmethod restructure-param :form-params [_ form-params acc]
  (let [schema (strict (fnk-schema form-params))]
    (-> acc
        (update-in [:letks] into [form-params (src-coerce! schema :form-params :string)])
        (update-in [:parameters :parameters :formData] st/merge schema)
        (assoc-in [:parameters :consumes] ["application/x-www-form-urlencoded"]))))

(defmethod restructure-param :multipart-params [_ params acc]
  (let [schema (strict (fnk-schema params))]
    (-> acc
        (update-in [:letks] into [params (src-coerce! schema :multipart-params :string)])
        (update-in [:parameters :parameters :formData] st/merge schema)
        (assoc-in [:parameters :consumes] ["multipart/form-data"]))))

; restructures header-params with plumbing letk notation. Example:
; :header-params [id :- Long name :- String]
(defmethod restructure-param :header-params [_ header-params acc]
  (let [schema (fnk-schema header-params)]
    (-> acc
        (update-in [:letks] into [header-params (src-coerce! schema :headers :string)])
        (assoc-in [:parameters :parameters :header] schema))))

; restructures query-params with plumbing letk notation. Example:
; :query-params [id :- Long name :- String]
(defmethod restructure-param :query-params [_ query-params acc]
  (let [schema (fnk-schema query-params)]
    (-> acc
        (update-in [:letks] into [query-params (src-coerce! schema :query-params :string)])
        (assoc-in [:parameters :parameters :query] schema))))

; restructures path-params by plumbing letk notation. Example:
; :path-params [id :- Long name :- String]
(defmethod restructure-param :path-params [_ path-params acc]
  (let [schema (fnk-schema path-params)]
    (-> acc
        (update-in [:letks] into [path-params (src-coerce! schema :route-params :string)])
        (assoc-in [:parameters :parameters :path] schema))))

; Applies the given vector of middlewares for the route from left to right
(defmethod restructure-param :middlewares [_ middlewares acc]
  (assert (and (vector? middlewares) (every? (comp ifn? eval) middlewares)))
  (update-in acc [:middlewares] into (reverse middlewares)))

; Bind to stuff in request components using letk syntax
(defmethod restructure-param :components [_ components acc]
  (update-in acc [:letks] into [components `(mw/get-components ~+compojure-api-request+)]))

; route-spesific override for coercers
(defmethod restructure-param :coercion [_ coercion acc]
  (update-in acc [:middlewares] conj `(mw/wrap-coercion ~coercion)))

;;
;; Api
;;

(defmacro middlewares
  "Wraps routes with given middlewares using thread-first macro."
  [middlewares & body]
  (let [middlewares (reverse middlewares)]
    `(-> (routes ~@body)
         ~@middlewares)))

(defmacro route-middlewares
  "Wraps route body in mock-handler and middlewares."
  [middlewares body arg]
  (let [middlewares (reverse middlewares)]
    `((-> (fn [~arg] ~body) ~@middlewares) ~arg)))

(defn- destructure-compojure-api-request
  "Returns a vector of three elements:
   - new lets list
   - bindings form for compojure route
   - symbol to which request will be bound"
  [lets arg]
  (cond
    ;; GET "/route" []
    (vector? arg) [lets (into arg [:as +compojure-api-request+]) +compojure-api-request+]
    ;; GET "/route" {:as req}
    (map? arg) (if-let [as (:as arg)]
                 [(conj lets +compojure-api-request+ as) arg as]
                 [lets (merge arg [:as +compojure-api-request+]) +compojure-api-request+])
    ;; GET "/route" req
    (symbol? arg) [(conj lets +compojure-api-request+ arg) arg arg]
    :else (throw
            (RuntimeException.
              (str "unknown compojure destruction syntax: " arg)))))

(defn restructure [method [path arg & args] & [{:keys [body-wrap]}]]
  (let [body-wrap (or body-wrap 'do)
        method-symbol (symbol (str (-> method meta :ns) "/" (-> method meta :name)))
        [parameters body] (extract-parameters args)
        [lets letks responses middlewares] [[] [] nil nil]
        [lets arg-with-request arg] (destructure-compojure-api-request lets arg)

        {:keys [lets
                letks
                responses
                middlewares
                parameters
                body]}
        (reduce
          (fn [acc [k v]]
            (restructure-param k v (update-in acc [:parameters] dissoc k)))
          (map-of lets letks responses middlewares parameters body)
          parameters)

        pre-lets [+compojure-api-coercer+ `(memoized-coercer)]

        body `(~body-wrap ~@body)
        body (if (seq letks) `(letk ~letks ~body) body)
        body (if (seq lets) `(let ~lets ~body) body)
        body (if (seq middlewares) `(route-middlewares ~middlewares ~body ~arg) body)
        body (if (seq parameters) `(meta-container ~parameters ~body) body)
        body `(~method-symbol ~path ~arg-with-request ~body)
        body (if responses `(body-coercer-middleware ~body ~+compojure-api-coercer+ ~responses) body)
        body (if (seq pre-lets) `(let ~pre-lets ~body) body)]
    body))
