(ns compojure.api.meta
  (:require [clojure.walk :refer [keywordize-keys]]
            [compojure.api.common :refer :all]
            [compojure.core :refer [routes]]
            [plumbing.core :refer :all]
            [plumbing.fnk.impl :as fnk-impl]
            [ring.swagger.common :refer :all]
            [ring.swagger.schema :as schema]
            [ring.util.http-response :refer [internal-server-error]]
            [schema.core :as s]))

;;
;; Meta Evil
;;

(def +compojure-api-request+
  "lexically bound ring-request for handlers."
  '+compojure-api-request+)

(def +compojure-api-meta+
  "lexically bound meta-data for handlers. EXPERIMENTAL."
  '+compojure-api-meta+)

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

(defn strict [schema]
  (dissoc schema 'schema.core/Keyword))

(defn fnk-schema [bind]
  (:input-schema
   (fnk-impl/letk-input-schema-and-body-form
     nil (with-meta bind {:schema s/Any}) [] nil)))

(defn body-coercer-middleware [handler responses]
  (fn [request]
    (if-let [{:keys [status] :as response} (handler request)]
      (if-let [schema (responses status)]
        (let [body (schema/coerce schema (:body response))]
          (if (schema/error? body)
            (internal-server-error {:errors (:error body)})
            (assoc response
              ::serializable? true
              :body body)))
        response))))

(defn src-coerce!
  "Return source code for coerce! for a schema with coercer type,
  extracted from a key in a ring request."
  [schema key type]
  `(schema/coerce!
     ~schema
     (keywordize-keys
       (~key ~+compojure-api-request+))
     ~type))

#_(defn src-coerce-param!
  "Return source code for coerce! for a schema with coercer type,
   extracted from a key in a ring request."
  [param key type]
  `(schema/coerce!
     (get-in ~+compojure-api-meta+ [:parameters ~param])
     (keywordize-keys
       (~key ~+compojure-api-request+))
     ~type))

;;
;; Response messages mangling
;;

(defn- responses->messages [responses]
  (for [[code schema] responses
        :when (not= code 200)]
    {:code code
     :message (or (some-> schema meta :message) "")
     :responseModel (eval schema)}))

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

(defmethod restructure-param :notes [k v acc]
  (update-in acc [:parameters] assoc k v))

(defmethod restructure-param :nickname [k v acc]
  (update-in acc [:parameters] assoc k v))

;;
;; Smart restructurings
;;

; Tags for api categorization. Ignores duplicates.
; Examples:
; :tags [:admin]
(defmethod restructure-param :tags [_ tags acc]
  (update-in acc [:parameters :tags] (comp set into) tags))

; Defines a return type and coerced the return value of a body against it.
; Examples:
; :return MySchema
; :return {:value String}
; :return #{{:key (s/maybe Long)}}
(defmethod restructure-param :return [k schema acc]
  (-> acc
      (update-in [:parameters] assoc k schema)
      (update-in [:responses] assoc 200 schema)))

; value is a map of http-response-code -> Schema. Translates to both swagger
; parameters and return schema coercion. Schemas can be decorated with meta-data.
; Examples:
; :responses {403 ErrorEnvelope}
; :responses {403 ^{:message \"Underflow\"} ErrorEnvelope}
(defmethod restructure-param :responses [_ responses acc]
  (let [messages (responses->messages responses)]
    (-> acc
        (update-in [:parameters :responseMessages] (comp distinct concat) messages)
        (update-in [:responses] merge responses))))

; reads body-params into a enchanced let. First parameter is the let symbol,
; second is the Schema to coerced! against.
; Examples:
; :body [user User]
(defmethod restructure-param :body [_ [value schema] acc]
  (-> acc
      (update-in [:lets] into [value (src-coerce! schema :body-params :json)])
      (assoc-in [:parameters :parameters :body] schema)))

; reads query-params into a enchanced let. First parameter is the let symbol,
; second is the Schema to coerced! against.
; Examples:
; :query [user User]
(defmethod restructure-param :query [_ [value schema] acc]
  (-> acc
      (update-in [:lets] into [value (src-coerce! schema :query-params :query)])
      (assoc-in [:parameters :parameters :query] schema)))

; reads header-params into a enchanced let. First parameter is the let symbol,
; second is the Schema to coerced! against.
; Examples:
; :headers [headers Headers]
(defmethod restructure-param :headers [_ [value schema] acc]
  (-> acc
      (update-in [:lets] into [value (src-coerce! schema :headers :query)])
      (assoc-in [:parameters :parameters :header] schema)))

; restructures body-params with plumbing letk notation. Example:
; :body-params [id :- Long name :- String]
(defmethod restructure-param :body-params [_ body-params acc]
  (let [schema (strict (fnk-schema body-params))]
    (-> acc
        (update-in [:letks] into [body-params (src-coerce! schema :body-params :json)])
        (assoc-in [:parameters :parameters :body] schema))))

; restructures form-params with plumbing letk notation. Example:
; :form-params [id :- Long name :- String]
(defmethod restructure-param :form-params [_ form-params acc]
  (let [schema (strict (fnk-schema form-params))]
    (-> acc
        (update-in [:letks] into [form-params (src-coerce! schema :form-params :query)])
        (assoc-in [:parameters :parameters :form] schema))))

; restructures header-params with plumbing letk notation. Example:
; :header-params [id :- Long name :- String]
(defmethod restructure-param :header-params [_ header-params acc]
  (let [schema (fnk-schema header-params)]
    (-> acc
        (update-in [:letks] into [header-params (src-coerce! schema :headers :query)])
        (assoc-in [:parameters :parameters :header] schema))))

; restructures query-params with plumbing letk notation. Example:
; :query-params [id :- Long name :- String]
(defmethod restructure-param :query-params [_ query-params acc]
  (let [schema (fnk-schema query-params)]
    (-> acc
        (update-in [:letks] into [query-params (src-coerce! schema :query-params :query)])
        (assoc-in [:parameters :parameters :query] schema))))

; restructures path-params by plumbing letk notation. Example:
; :path-params [id :- Long name :- String]
(defmethod restructure-param :path-params [_ path-params acc]
  (let [schema (fnk-schema path-params)]
    (-> acc
        (update-in [:letks] into [path-params (src-coerce! schema :route-params :query)])
        (assoc-in [:parameters :parameters :path] schema))))

; Applies the given vector of middlewares for the route from left to right
(defmethod restructure-param :middlewares [_ middlewares acc]
  (assert (and (vector? middlewares) (every? (comp ifn? eval) middlewares)))
  (update-in acc [:middlewares] into (reverse middlewares)))

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

(defn restructure [method [path arg & args]]
  (let [method-symbol (symbol (str (-> method meta :ns) "/" (-> method meta :name)))
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

        body `(do ~@body)
        body (if (seq letks) `(letk ~letks ~body) body)
        body (if (seq lets) `(let ~lets ~body) body)
        body (if (seq middlewares) `(route-middlewares ~middlewares ~body ~arg) body)
        body (if (seq parameters) `(meta-container ~parameters ~body) body)
        body `(~method-symbol ~path ~arg-with-request ~body)
        body (if responses `(body-coercer-middleware ~body  ~responses) body)]
    body))
