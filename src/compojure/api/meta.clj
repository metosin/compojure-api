(ns compojure.api.meta
  (:require [clojure.edn :as edn]
            [compojure.api.common :refer [extract-parameters]]
            [compojure.api.middleware :as mw]
            [compojure.api.routes :as routes]
            [plumbing.core :as p]
            [plumbing.fnk.impl :as fnk-impl]
            [ring.swagger.common :as rsc]
            [ring.swagger.json-schema :as js]
            [schema.core :as s]
            [schema-tools.core :as st]
            [compojure.api.coercion :as coercion]
            [compojure.api.help :as help]
            compojure.core
            compojure.api.compojure-compat
            [compojure.api.common :as common]))

(def +compojure-api-request+
  "lexically bound ring-request for handlers."
  '+compojure-api-request+)

;;
;; Schema
;;

(defn strict [schema]
  (dissoc schema 'schema.core/Keyword))

(defn fnk-schema [bind]
  (try
    (->>
      (:input-schema
        (fnk-impl/letk-input-schema-and-body-form
          nil (with-meta bind {:schema s/Any}) [] nil))
      reverse
      (into {}))
    (catch Exception _
      (let [hint (cond
                   ;; [a ::a]
                   (and (= 2 (count bind)) (keyword? (second bind)))
                   (str "[" (first bind) " :- " (second bind) "]")

                   :else nil)]
        (throw (IllegalArgumentException.
                 (str "Binding is not valid, please refer to "
                      "https://github.com/plumatic/plumbing/tree/master/src/plumbing/fnk#fnk-syntax\n"
                      " for more information.\n\n"
                      "      binding: " bind "\n\n"
                      (if hint (str " did you mean: " hint "\n\n")))))))))

(s/defn src-coerce!
  "Return source code for coerce! for a schema with coercion type,
  extracted from a key in a ring request."
  ([schema, key, type]
    (src-coerce! schema, key, type, true))
  ([schema, key, type, keywordize?]
    `(coercion/coerce-request! ~schema ~key ~type ~keywordize? false ~+compojure-api-request+)))

(defn- convert-return [schema]
  {200 {:schema schema
        :description (or (js/json-schema-meta schema) "")}})

;;
;; Extension point
;;

(defmulti restructure-param
  "Restructures a key value pair in smart routes. By default the key
  is consumed form the :parameters map in acc. k = given key, v = value."
  (fn [k v acc] k))

;;
;; dynamic
;;

(defmethod help/help-for [:meta :dynamic] [_ _]
  (help/text
    "If set to to `true`, makes a `context` dynamic,"
    "which wraps the body in a closure that is evaluated on each request."
    "This is the default behavior in vanilla compojure. In compojure-api,"
    "this is also the usual behavior, except:"
    "If the context does not bind any variables and its body contains"
    "just top-level calls to compojure.api endpoint macros like GET,"
    "then the body will be cached for each request."

    (help/code
      "(context \"/static\" []"
      "  :static true"
      "  (when (= 0 (random-int 2))"
      "     (println 'ping!) ;; never printed during request"
      "     ;; mounting decided once"
      "     (GET \"/ping\" [] (ok \"pong\"))))"
      ""
      "(context \"/dynamic\" []"
      "  :dynamic true"
      "  (when (= 0 (random-int 2))"
      "     (println 'ping!) ;; printed 50% of requests"
      "     ;; mounted for 50% of requests"
      "     (GET \"/ping\" [] (ok \"pong\"))))")))

(defmethod restructure-param :dynamic [k v acc]
  (update-in acc [:info :public] assoc k v))

(defmethod help/help-for [:meta :static] [_ _]
  (help/text
    "If set to to `true`, makes a `context` static,"
    "which resolves the body before processing requests."
    "This is much faster than :dynamic contexts at the"
    "cost of expressivity: routes under a static context"
    "cannot change based on the request."

    (help/code
      "(context \"/static\" []"
      "  :static true"
      "  (when (= 0 (random-int 2))"
      "     (println 'ping!) ;; never printed during request"
      "     ;; mounting decided once"
      "     (GET \"/ping\" [] (ok \"pong\"))))"
      ""
      "(context \"/dynamic\" []"
      "  :dynamic true"
      "  (when (= 0 (random-int 2))"
      "     (println 'ping!) ;; printed 50% of requests"
      "     ;; mounted for 50% of requests"
      "     (GET \"/ping\" [] (ok \"pong\")))")))

(defmethod restructure-param :static [k v acc]
  (update-in acc [:info :public] assoc k v))

;;
;; summary
;;

(defmethod help/help-for [:meta :summary] [_ _]
  (help/text
    "A short summary of what the operation does. For maximum"
    "readability in the swagger-ui, this field SHOULD be less"
    "than 120 characters.\n"
    (help/code
      "(GET \"/ok\" []"
      "  :summary \"this endpoint alreays returns 200\""
      "  (ok))")))

(defmethod restructure-param :summary [k v acc]
  (update-in acc [:info :public] assoc k v))

;;
;; description
;;

(defmethod help/help-for [:meta :description] [_ _]
  (help/text
    "A verbose explanation of the operation behavior."
    "GFM syntax can be used for rich text representation."
    (help/code
      "(GET \"/ok\" []"
      "  :description \"this is a `GET`.\""
      "  (ok))")))

(defmethod restructure-param :description [k v acc]
  (update-in acc [:info :public] assoc k v))

;;
;; OperationId
;;

(defmethod help/help-for [:meta :operationId] [_ _]
  (help/text
    "Unique string used to identify the operation. The id MUST be"
    "unique among all operations described in the API. Tools and"
    "libraries MAY use the operationId to uniquely identify an operation,"
    "therefore, it is recommended to follow common programming naming conventions.\n"
    (help/code
      "(GET \"/pets\" []"
      "  :operationId \"get-pets\""
      "  (ok))")))

(defmethod restructure-param :operationId [k v acc]
  (update-in acc [:info :public] assoc k v))

;;
;; Consumes
;;

(defmethod help/help-for [:meta :consumes] [_ _]
  (help/text
    "Swagger-ui hint about mime-types the endpoints can consume."
    "Takes a vector or set. Just for docs.\n"
    (help/code
      "(GET \"/edn-endpoint\" []"
      "  :consumes #{\"application/edn\"}"
      "  :produces #(\"application/edn\"}"
      "  (ok))")))

(defmethod restructure-param :consumes [k v acc]
  (update-in acc [:info :public] assoc k v))

;;
;; Provides
;;

(defmethod help/help-for [:meta :produces] [_ _]
  (help/text
    "Swagger-ui hint about mime-types the endpoints produces."
    "Takes a vector or set. Just for docs.\n"
    (help/code
      "(GET \"/edn-endpoint\" []"
      "  :consumes #{\"application/edn\"}"
      "  :produces #{\"application/edn\"}"
      "  (ok))")))

(defmethod restructure-param :produces [k v acc]
  (update-in acc [:info :public] assoc k v))

;;
;; no-doc
;;

(defmethod help/help-for [:meta :no-doc] [_ _]
  (help/text
    "Boolean to discard the route out from api documentation\n"
    (help/code
      "(GET \"/secret\" []"
      "  :no-doc true"
      "  (ok))")))

(defmethod restructure-param :no-doc [_ v acc]
  (update-in acc [:info] assoc :no-doc v))

;;
;; swagger
;;

(defmethod help/help-for [:meta :swagger] [_ _]
  (help/text
    "Raw swagger-data, just for docs.\n"
    (help/code
      "(GET \"/ok\" []"
      "  :swagger {:responses {200 {:schema User}"
      "                        404 {:schema Error"
      "                             :description \"Not Found\"}}"
      "            :parameters {:query {:q s/Str}"
      "                         :body NewUser}}"
      "  (ok))")))

(defmethod restructure-param :swagger [_ swagger acc]
  (assoc-in acc [:info :public :swagger] swagger))

;;
;; name
;;

(defmethod help/help-for [:meta :name] [_ _]
  (help/text
    "Name of a route. Used in bi-directional routing.\n"
    (help/code
      "(context \"/user\" []"
      "  (GET \"/:id\" []"
      "    :path-params [id :- s/Int]"
      "    :name ::user"
      "    (ok))"
      "  (POST \"/\" []"
      "    (created (path-for ::user {:id (random-int)}))))")))

(defmethod restructure-param :name [_ v acc]
  (-> acc
      (assoc-in [:info :name] v)
      (assoc-in [:info :public :x-name] v)))

;;
;; tags
;;

(defmethod help/help-for [:meta :tags] [_ _]
  (help/text
    "Takes a sequence or a set of tags for the swagger-doc.\n"
    (help/code
      "(GET \"/ok\" []"
      "  :tags #{\"admin\", \"common\"}"
      "  (ok))")))

(defmethod restructure-param :tags [_ tags acc]
  (update-in acc [:info :public :tags] (comp set into) tags))

;;
;; return
;;

(defmethod help/help-for [:meta :return] [_ _]
  (help/text
    "Request (status 200) body schema for coercion and api-docs.\n"
    (help/code
      "(GET \"/user\" []"
      "  :return {:name (s/maybe s/Str)"
      "  (ok {:name \"Kirsi\"))")))

(defmethod restructure-param :return [_ schema acc]
  (let [response (convert-return schema)]
    (-> acc
        (update-in [:info :public :responses] (fnil conj []) response)
        (update-in [:responses] (fnil conj []) response))))

;;
;; responses
;;

(defmethod help/help-for [:meta :responses] [_ _]
  (help/text
    "Map of response status code (or :default) -> response schema."
    "Response can have keys :schema, :description and :headers.\n"
    (help/code
      "(GET \"/user\" []"
      "  :responses {200 {:schema {:name (s/maybe s/Str)}}"
      "              404 {:schema {:error s/Int}"
      "                   :description \"not found\"}"
      "  (ok {:name nil}))"
      ""
      "(GET \"/user\" []"
      "  :responses {200 {:schema s/Any, :description \"ok\"}"
      "              :default {:schema s/Any, :description \"default\""
      "  (bad-request \"kosh\"))")))

(defmethod restructure-param :responses [_ responses acc]
  (-> acc
      (update-in [:info :public :responses] (fnil conj []) responses)
      (update-in [:responses] (fnil conj []) responses)))

;;
;; body
;;

(defmethod help/help-for [:meta :body] [_ _]
  (help/text
    "body-params with let-syntax. First parameter is the symbol,"
    "second is the Schema used in coercion (and api-docs).\n"
    (help/code
      "(POST \"/echo\" []"
      "  :body [body User]"
      "  (ok body))")))

(defmethod restructure-param :body [_ [value schema] acc]
  (-> acc
      (update-in [:lets] into [value (src-coerce! schema :body-params :body false)])
      (assoc-in [:info :public :parameters :body] schema)))

;;
;; query
;;

(defmethod help/help-for [:meta :query] [_ _]
  (help/text
    "query-params with let-syntax. First parameter is the symbol,"
    "second is the Schema used in coercion (and api-docs).\n"
    (help/code
      "(GET \"/search\" []"
      "  :query [params {:q s/Str, :max s/Int}]"
      "  (ok params))")))

(defmethod restructure-param :query [_ [value schema] acc]
  (-> acc
      (update-in [:lets] into [value (src-coerce! schema :query-params :string)])
      (assoc-in [:info :public :parameters :query] schema)))

;;
;; headers
;;

(defmethod help/help-for [:meta :headers] [_ _]
  (help/text
    "header-params with let-syntax. First parameter is the symbol,"
    "second is the Schema used in coercion (and api-docs).\n"
    (help/code
      "(GET \"/headers\" []"
      "  :headers [headers HeaderSchema]"
      "  (ok headers))")))

(defmethod restructure-param :headers [_ [value schema] acc]
  (-> acc
      (update-in [:lets] into [value (src-coerce! schema :headers :string)])
      (assoc-in [:info :public :parameters :header] schema)))

;;
;; body-params
;;

(defmethod help/help-for [:meta :body-params] [_ _]
  (help/text
    "body-params with letk. Schema is used for both coercion and api-docs."
    "https://github.com/plumatic/plumbing/tree/master/src/plumbing/fnk#fnk-syntax\n"
    (help/code
      "(POST \"/math\" []"
      "  :body-params [x :- s/Int, {y :- s/Int 1}]"
      "  (ok {:total (+ x y)}))")))

(defmethod restructure-param :body-params [_ body-params acc]
  (let [schema (strict (fnk-schema body-params))]
    (-> acc
        (update-in [:letks] into [body-params (src-coerce! schema :body-params :body)])
        (assoc-in [:info :public :parameters :body] schema))))

;;
;; form-params
;;

(defmethod help/help-for [:meta :form-params] [_ _]
  (help/text
    "form-params with letk. Schema is used for both coercion and api-docs."
    "Also sets the :swagger :consumes to #{\"application/x-www-form-urlencoded\"}."
    "https://github.com/plumatic/plumbing/tree/master/src/plumbing/fnk#fnk-syntax\n"
    (help/code
      "(POST \"/math\" []"
      "  :form-params [x :- s/Int, {y :- s/Int 1}]"
      "  (ok {:total (+ x y)}))")))

(defmethod restructure-param :form-params [_ form-params acc]
  (let [schema (strict (fnk-schema form-params))]
    (-> acc
        (update-in [:letks] into [form-params (src-coerce! schema :form-params :string)])
        (update-in [:info :public :parameters :formData] st/merge schema)
        (assoc-in [:info :public :consumes] ["application/x-www-form-urlencoded"]))))

;;
;; multipart-params
;;

(defmethod help/help-for [:meta :multipart-params] [_ _]
  (help/text
    "multipart-params with letk. Schema is used for both coercion and api-docs."
    "Should be used with a middleware to do the actual file-upload."
    "Sets also the :swagger :consumes to #{\"multipart/form-data\"}."
    "https://github.com/plumatic/plumbing/tree/master/src/plumbing/fnk#fnk-syntax\n"
    (help/code
      "(require '[compojure.api.upload :as upload]"
      ""
      "(POST \"/file\" []"
      "  :multipart-params [foo :- upload/TempFileUpload]"
      "  :middleware [upload/wrap-multipart-params]"
      "  (ok (dissoc foo :tempfile)))")))

(defmethod restructure-param :multipart-params [_ params acc]
  (let [schema (strict (fnk-schema params))]
    (-> acc
        (update-in [:letks] into [params (src-coerce! schema :multipart-params :string)])
        (update-in [:info :public :parameters :formData] st/merge schema)
        (assoc-in [:info :public :consumes] ["multipart/form-data"]))))

;;
;; header-params
;;

(defmethod help/help-for [:meta :header-params] [_ _]
  (help/text
    "header-params with letk. Schema is used for both coercion and api-docs."
    "https://github.com/plumatic/plumbing/tree/master/src/plumbing/fnk#fnk-syntax\n"
    (help/code
      "(POST \"/math\" []"
      "  :header-params [x :- s/Int, {y :- s/Int 1}]"
      "  (ok {:total (+ x y)}))")))

(defmethod restructure-param :header-params [_ header-params acc]
  (let [schema (fnk-schema header-params)]
    (-> acc
        (update-in [:letks] into [header-params (src-coerce! schema :headers :string)])
        (assoc-in [:info :public :parameters :header] schema))))

;;
;; :query-params
;;

(defmethod help/help-for [:meta :query-params] [_ _]
  (help/text
    "query-params with letk. Schema is used for both coercion and api-docs."
    "https://github.com/plumatic/plumbing/tree/master/src/plumbing/fnk#fnk-syntax\n"
    (help/code
      "(POST \"/math\" []"
      "  :query-params [x :- s/Int, {y :- s/Int 1}]"
      "  (ok {:total (+ x y)}))")))

(defmethod restructure-param :query-params [_ query-params acc]
  (let [schema (fnk-schema query-params)]
    (-> acc
        (update-in [:letks] into [query-params (src-coerce! schema :query-params :string)])
        (assoc-in [:info :public :parameters :query] schema))))

;;
;; path-params
;;

(defmethod help/help-for [:meta :path-params] [_ _]
  (help/text
    "path-params with letk. Schema is used for both coercion and api-docs."
    "https://github.com/plumatic/plumbing/tree/master/src/plumbing/fnk#fnk-syntax\n"
    (help/code
      "(POST \"/math/:x/:y\" []"
      "  :path-params [x :- s/Int, {y :- s/Int 1}]"
      "  (ok {:total (+ x y)}))")))

(defmethod restructure-param :path-params [_ path-params acc]
  (let [schema (fnk-schema path-params)]
    (-> acc
        (update-in [:letks] into [path-params (src-coerce! schema :route-params :string)])
        (assoc-in [:info :public :parameters :path] schema))))

;;
;; middleware
;;

(defmethod help/help-for [:meta :middleware] [_ _]
  (help/text
    "Applies the given vector of middleware to the route."
    "Middleware is presented as data in a Duct-style form:"
    ""
    "1) ring mw-function (handler->request->response)"
    ""
    "2) mw-function and it's arguments separately - mw is"
    "   created by applying function with handler and args\n"
    (help/code
      "(defn require-role [handler role]"
      "  (fn [request]"
      "    (if (has-role? request role)"
      "      (handler request)"
      "      (unauthorized))))"
      ""
      "(def require-admin #(require-role % :admin))"
      ""
      "(GET \"/admin\" []"
      "  :middleware [require-admin]"
      "  (ok))"
      ""
      "(GET \"/admin\" []"
      "  :middleware [[require-role :admin]]"
      "  (ok))"
      ""
      "(GET \"/admin\" []"
      "  :middleware [#(require-role % :admin)]"
      "  (ok))"
      )))

(defmethod restructure-param :middleware [_ middleware acc]
  (update-in acc [:middleware] into middleware))

;;
;; components
;;

(defmethod help/help-for [:meta :components] [_ _]
  (help/text
    "binds components into request via letk. Schema is not used here."
    "to enable component injection into request, one should use either:"
    ""
    "1) `api`-options :components"
    "2) `compojure.api.middleware/wrap-components"
    ""
    (help/code
      "(defn app [{:keys [db] :as system}]"
      "  (api"
      "    {:components system}"
      "    (GET \"/ok\" []"
      "       :components [db]"
      "       (ok (do-something-with db)))))")))

(defmethod restructure-param :components [_ components acc]
  (update-in acc [:letks] into [components `(mw/get-components ~+compojure-api-request+)]))

;;
;; coercion
;;

(defmethod help/help-for [:meta :coercion] [_ _]
  (help/text
    "Route-spesific overrides for coercion. See more on wiki:"
    "https://github.com/metosin/compojure-api/wiki/Validation-and-coercion\n"
    (help/code
      "(POST \"/user\" []"
      "  :coercion my-custom-coercion"
      "  :body [user User]"
      "  (ok user))")))

(defmethod restructure-param :coercion [_ coercion acc]
  (-> acc
      (assoc-in [:info :coercion] coercion)
      (update-in [:middleware] conj [`mw/wrap-coercion coercion])))

;;
;; Impl
;;

(defmacro dummy-let
  "Dummy let-macro used in resolving route-docs. not part of normal invokation chain."
  [bindings & body]
  (let [bind-form (vec (apply concat (for [n (take-nth 2 bindings)] [n nil])))]
    `(let ~bind-form ~@body)))

(defmacro dummy-letk
  "Dummy letk-macro used in resolving route-docs. not part of normal invokation chain."
  [bindings & body]
  (reduce
    (fn [cur-body-form [bind-form]]
      (if (symbol? bind-form)
        `(let [~bind-form nil] ~cur-body-form)
        (let [{:keys [map-sym body-form]} (fnk-impl/letk-input-schema-and-body-form
                                            &env
                                            (fnk-impl/ensure-schema-metadata &env bind-form)
                                            []
                                            cur-body-form)
              body-form (clojure.walk/prewalk-replace {'plumbing.fnk.schema/safe-get 'clojure.core/get} body-form)]
          `(let [~map-sym nil] ~body-form))))
    `(do ~@body)
    (reverse (partition 2 bindings))))

(defmacro static-context
  [path route]
  `(compojure.api.compojure-compat/make-context
     ~(#'compojure.core/context-route path)
     (constantly ~route)))

(defn routing [handlers]
  (if-let [handlers (seq (keep identity (flatten handlers)))]
    (apply compojure.core/routes handlers)
    (fn ([_] nil) ([_ respond _] (respond nil)))))

;;
;; Api
;;

(defn- destructure-compojure-api-request
  "Returns a vector of four elements:
  - pruned path string
  - new lets list
  - bindings form for compojure route
  - symbol to which request will be bound"
  [path arg]
  (let [path-string (if (vector? path) (first path) path)]
    (cond
      ;; GET "/route" []
      (vector? arg) [path-string [] (into arg [:as +compojure-api-request+]) +compojure-api-request+]
      ;; GET "/route" {:as req}
      (map? arg) (if-let [as (:as arg)]
                   [path-string [+compojure-api-request+ as] arg as]
                   [path-string [] (merge arg [:as +compojure-api-request+]) +compojure-api-request+])
      ;; GET "/route" req
      (symbol? arg) [path-string [+compojure-api-request+ arg] arg arg]
      :else (throw
              (RuntimeException.
                (str "unknown compojure destruction syntax: " arg))))))

(defn- merge-public-parameters
  [{:keys [responses swagger] :as parameters}]
  (cond-> parameters
          (seq responses) (assoc :responses (common/merge-vector responses))
          swagger (-> (dissoc :swagger) (rsc/deep-merge swagger))))

(defn merge-parameters
  "Merge parameters at runtime to allow usage of runtime-paramers with route-macros."
  [info]
  (cond-> info
          (contains? info :public) (update :public merge-public-parameters)))

(defn- route-args? [arg]
  (not= arg []))

(def endpoint-vars (into #{}
                         (mapcat (fn [n]
                                   (map #(symbol (name %) (name n))
                                        '[compojure.api.core
                                          compojure.api.sweet])))
                         '[GET ANY HEAD PATCH DELETE OPTIONS POST PUT]))

(def routes-vars #{'compojure.api.sweet/routes
                   'compojure.api.core/routes})

(declare static-body? static-form?)

(defn- static-endpoint? [&env form]
  (and (seq? form)
       (boolean
         (let [sym (first form)]
           (when (symbol? sym)
             (when-some [v (resolve &env sym)]
               (when (var? v)
                 (let [sym (symbol v)]
                   (or (endpoint-vars sym)
                       (and (routes-vars sym)
                            (static-body? &env (next form))))))))))))

(def resource-vars '#{compojure.api.sweet/resource
                      compojure.api.resource/resource})

(defn- static-resource? [&env form]
  (and (seq? form)
       (boolean
         (let [sym (first form)]
           (when (symbol? sym)
             (when-some [v (resolve &env sym)]
               (when (var? v)
                 (let [sym (symbol v)]
                   (when (and (resource-vars sym)
                              (= 2 (count form)))
                     (let [[_ data] form]
                       (static-form? &env data)))))))))))

(def context-vars (into #{}
                        (mapcat (fn [n]
                                  (map #(symbol (name %) (name n))
                                       '[compojure.api.core
                                         compojure.api.sweet])))
                        '[context]))

(defn- static-context? [&env body]
  (and (seq? body)
       (boolean
         (let [sym (first body)]
           (when (symbol? sym)
             (when-some [v (resolve &env sym)]
               (when (var? v)
                 (when (context-vars (symbol v))
                   (let [[_ path route-arg & args] body
                         [options body] (extract-parameters args true)
                         [path-string lets arg-with-request] (destructure-compojure-api-request path route-arg)
                         {:keys [lets
                                 letks
                                 responses
                                 middleware
                                 info
                                 swagger
                                 body]} (reduce
                                          (fn [acc [k v]]
                                            (restructure-param k v (update-in acc [:parameters] dissoc k)))
                                          {:lets lets
                                           :letks []
                                           :responses nil
                                           :middleware []
                                           :info {}
                                           :body body}
                                          options)
                         static? (not (or (-> info :public :dynamic)
                                          (route-args? route-arg) (seq lets) (seq letks)))
                         safely-static (or (-> info :public :static) (static-body? &env body))]
                     safely-static)))))))))

(def middleware-vars (into #{}
                           (mapcat (fn [n]
                                     (map #(symbol (name %) (name n))
                                          '[compojure.api.core
                                            compojure.api.sweet])))
                           '[middleware]))

(defn- static-middleware? [&env body]
  (and (seq? body)
       (boolean
         (let [sym (first body)]
           (when (symbol? sym)
             (when-some [v (resolve &env sym)]
               (when (var? v)
                 (when (middleware-vars (symbol v))
                   (let [[_ path route-arg & args] body
                         [options body] (extract-parameters args true)
                         [path-string lets arg-with-request] (destructure-compojure-api-request path route-arg)
                         {:keys [lets
                                 letks
                                 responses
                                 middleware
                                 info
                                 swagger
                                 body]} (reduce
                                          (fn [acc [k v]]
                                            (restructure-param k v (update-in acc [:parameters] dissoc k)))
                                          {:lets lets
                                           :letks []
                                           :responses nil
                                           :middleware []
                                           :info {}
                                           :body body}
                                          options)
                         safely-static (static-body? &env body)]
                     safely-static)))))))))

(def route-middleware-vars (into #{}
                                 (mapcat (fn [n]
                                           (map #(symbol (name %) (name n))
                                                '[compojure.api.core
                                                  compojure.api.sweet])))
                                 '[route-middleware]))

(def ^:private ^:dynamic *not-safely-static* nil)

(defn- static-route-middleware? [&env body]
  (and (seq? body)
       (boolean
         (let [sym (first body)]
           (when (symbol? sym)
             (when-some [v (resolve &env sym)]
               (when (var? v)
                 (when (route-middleware-vars (symbol v))
                   (let [[_ mids & body] body]
                     (and (some? mids)
                          (static-body? &env body)))))))))))

(defn- static-cond? [&env form]
  (and (seq? form)
       (boolean
         (let [sym (first form)]
           (when (symbol? sym)
             (let [v (resolve &env sym)]
               (when (or (= #'when v)
                         (= #'cond v)
                         (= #'= v)
                         (= #'not= v)
                         (= #'boolean v)
                         (= sym 'if))
                 (static-body? &env (next form)))))))))

(defn- resolve-var [&env sym]
  (when (symbol? sym)
    (let [v (resolve &env sym)]
      (when (var? v)
        v))))

(defn- static-resolved-form? [&env form]
  (boolean
    (or (and (seq? form)
             (= 2 (count form))
             (= 'var (first form))
             (when-some [v (resolve-var nil (second form))]
               (not (:dynamic (meta v)))))
        (when (symbol? form)
          (let [r (resolve &env form)]
            (or (class? r)
                (and (var? r)
                     (not (:dynamic (meta r))))))))))

(defn- static-expansion? [&env form]
  (boolean
    (when (and (seq? form)
               (symbol? (first form))
               (not (contains? &env (first form))))
      (let [form' (macroexpand-1 form)]
        (when-not (identical? form' form)
          (static-form? &env form'))))))

(defn- constant-form? [&env form]
  (or ((some-fn nil? keyword? number? boolean? string?) form)
      (and (seq? form)
           (= 2 (count form))
           (= 'quote (first form)))
      (and (vector? form)
           (every? #(static-form? &env %) form))
      (and (map? form)
           (every? #(static-form? &env %) form))
      (and (seq? form)
           (next form)
           (= 'fn* (first form)))
      (and (seq? form)
           ('#{clojure.spec.alpha/keys}
             (some-> (resolve-var &env (first form))
                     symbol)))
      (and (seq? form)
           (symbol? (first form))
           (when-some [v (resolve-var &env (first form))]
             (when (or (#{"spec-tools.data-spec"
                          "spec-tools.core"
                          "schema.core"
                          "ring.util.http-response"}
                         (namespace (symbol v)))
                       ('#{compojure.api.sweet/describe
                           ring.swagger.json-schema/describe
                           clojure.core/constantly}
                         (symbol v)))
               (when-not (some #{:dynamic :macro} (meta v))
                 (every? #(static-form? &env %) (next form))))))))

(defn- static-binder-env [&env bv]
  (when (and (vector? bv)
             (even? (count bv)))
    (let [flat (eduction
                 (partition-all 2)
                 (mapcat (fn [[l init]]
                           (if (and (= :let l)
                                    (even? count init))
                             (partition-all 2 init)
                             [[l init]])))
                 bv)]
      (reduce (fn [&env [l init]]
                (if-not (or (simple-symbol? l)
                            (simple-keyword? l) ;;for
                            (static-form? init))
                  (reduced nil)
                  (cond-> &env
                    (simple-symbol? l)
                    (assoc l true))))
              (or &env {})
              flat))))

(defn- static-let? [&env form]
  (and (seq? form)
       (symbol? (first form))
       (when-some [op (or (when (= 'let* (first form))
                            'let*)
                          (when-some [v (resolve-var &env (first form))]
                            (let [sym (symbol v)]
                              (when (contains?
                                      '#{clojure.core/let clojure.core/for
                                         compojure.api.sweet/let-routes compojure.api.core/let-routes}
                                      sym)
                                sym))))]
         (let [;; expand destructuring
               [_ bv & body] (macroexpand
                               (if ('#{compojure.api.sweet/let-routes compojure.api.core/let-routes} op)
                                 form
                                 (list* `let (next form))))]
           (when-some [&env (static-binder-env &env bv)]
             (static-body? &env body))))))

(defn- static-vector? [&env body]
  (and (vector? body)
       (every? #(static-body? &env %) body)))

(defn- static-form? [&env form]
  (let [res (boolean
              (or (contains? &env form) ;;local
                  (static-resolved-form? &env form)
                  (constant-form? &env form)
                  (static-endpoint? &env form)
                  (static-resource? &env form)
                  (static-let? &env form)
                  (static-cond? &env form)
                  (static-context? &env form)
                  (static-middleware? &env form)
                  (static-route-middleware? &env form)
                  (static-expansion? &env form)))]
    (when-not res
      (some-> *not-safely-static* (swap! conj {:form form :&env (into {} (map (fn [[k v]]
                                                                                [k (if (boolean? v) v (class v))]))
                                                                      &env)})))
    res))

(defn- static-body? [&env body]
  (every? #(static-form? &env %) body))

(def ^:private warned-non-static? (atom false))

(defn restructure [method [path route-arg & args] {:keys [context? &form &env]}]
  (let [[options body] (extract-parameters args true)
        [path-string lets arg-with-request] (destructure-compojure-api-request path route-arg)

        {:keys [lets
                letks
                responses
                middleware
                info
                swagger
                body]} (reduce
                         (fn [acc [k v]]
                           (restructure-param k v (update-in acc [:parameters] dissoc k)))
                         {:lets lets
                          :letks []
                          :responses nil
                          :middleware []
                          :info {}
                          :body body}
                         options)

        coercion (:coercion info)

        _ (assert (not (and (-> info :public :dynamic)
                            (-> info :public :static)))
                  "Cannot be both a :dynamic and :static context.")

        bindings? (boolean (or (route-args? route-arg) (seq lets) (seq letks)))

        _ (assert (not (and (-> info :public :static)
                            bindings?))
                  "A context cannot be :static and also provide bindings. Either push bindings into endpoints or remove :static.")

        static? (not (or (-> info :public :dynamic) bindings?))

        a (atom [])
        safely-static (when context?
                        (or (-> info :public :static)
                            (when static?
                              (when-not (-> info :public :dynamic)
                                (try (binding [*not-safely-static* a]
                                       (static-body? &env body))
                                     (catch Exception e
                                       (println `restructure-param "Internal error, please report the following trace to https://github.com/metosin/compojure-api")
                                       (prn {:form &form :env &env})
                                       (prn e)
                                       false))))))

        _ (when (and context? static?)
            (when-not safely-static
              (when (and static? (not (-> info :public :static)))
                (let [coach (some-> (System/getProperty "compojure.api.meta.static-context-coach")
                                    edn/read-string)]
                  (if-not coach
                    (when (ffirst (reset-vals! warned-non-static? true))
                      (println
                        (str (format "WARNING: Performance issue detected with compojure-api usage in %s.\n" (ns-name *ns*))
                             "To fix this warning, set: -Dcompojure.api.meta.static-context-coach={:default :print}.\n"
                             "To suppress this warning, set: -Dcompojure.api.meta.static-context-coach={:default :off}.\n"
                             "This warning will only print once, other namespaces may be affected.")))
                    (let [_ (assert (map? coach)
                                    (str "-Dcompojure.api.meta.static-context-coach should be a map, given: "
                                         (pr-str coach)))
                          nsym (ns-name *ns*)
                          mode (or (get coach nsym)
                                   (get coach :default)
                                   :print)
                          _ (when (:verbose coach)
                              (println "The following forms were not inferred static:")
                              ((requiring-resolve 'clojure.pprint/pprint)
                               @a))
                          msg (str "This looks like it could be a static context: " (pr-str {:form &form :meta (meta &form)})
                                   "\n\n"
                                   "If you intend for the body of this context to be evaluated on every request, please "
                                   "use (context ... :dynamic true ...)."
                                   "\n\n"
                                   "If you intend for the body of this context to be fixed for every request, please "
                                   "use (context ... :static true ...)."
                                   "\n\n"
                                   "If you feel this case could be automatically inferred as :static, please suggest a "
                                   "new inference rule at https://github.com/metosin/compojure-api. Use "
                                   "-Dcompojure.api.meta.static-context-coach={:verbose true} to print additional information "
                                   "and include it in the issue."
                                   "\n\n"
                                   "To suppress this message for this namespace use -Dcompojure.api.meta.static-context-coach="
                                   "{" nsym " " :off "}"
                                   "\n\nCurrent coach config: " (pr-str coach))]
                      (case mode
                        :off nil
                        :print (println msg)
                        :assert (throw (ex-info msg
                                                {:form &form
                                                 :meta (meta &form)}))
                        (throw (ex-info "compojure.api.meta.static-context-coach mode must be either :off, :print, or :assert" {:coach coach
                                                                                                                                :provided mode})))))))))

        ;; :dynamic by default
        static-context? (and static? context? (boolean safely-static))

        info (cond-> info
                     static-context? (assoc :static-context? static-context?))

        _ (assert (nil? swagger) ":swagger is deprecated with 2.0.0, use [:info :public] instead")

        ;; response coercion middleware, why not just code?
        middleware (if (seq responses) (conj middleware `[coercion/wrap-coerce-response (common/merge-vector ~responses)]) middleware)]

    (if context?

      ;; context
      (let [form `(routing [~@body])
            form (if (seq letks) `(p/letk ~letks ~form) form)
            form (if (seq lets) `(let ~lets ~form) form)
            ;; coercion is set via middleware. for contexts, middleware is applied after let & letk -bindings
            ;; to make coercion visible to the lets & letks, we apply it before any let & letk -bindings
            form (if (and coercion (not static-context?))
                   `(let [~+compojure-api-request+ (coercion/set-request-coercion ~+compojure-api-request+ ~coercion)]
                      ~form)
                   form)
            form (if (seq middleware) `((mw/compose-middleware ~middleware) ~form) form)
            form (if static-context?
                   `(static-context ~path ~form)
                   `(compojure.core/context ~path ~arg-with-request ~form))

            ;; create and apply a separate lookup-function to find the inner routes
            childs (let [form (vec body)
                         form (if (seq letks) `(dummy-letk ~letks ~form) form)
                         form (if (seq lets) `(dummy-let ~lets ~form) form)
                         form `(compojure.core/let-request [~arg-with-request ~'+compojure-api-request+] ~form)
                         form `(fn [~'+compojure-api-request+] ~form)
                         form `(delay (flatten (~form {})))]
                     form)]

        `(routes/map->Route
           {:path ~path-string
            :method ~method
            :info (merge-parameters ~info)
            :childs ~childs
            :handler ~form}))

      ;; endpoints
      (let [form `(do ~@body)
            form (if (seq letks) `(p/letk ~letks ~form) form)
            form (if (seq lets) `(let ~lets ~form) form)
            form (compojure.core/compile-route method path arg-with-request (list form))
            form (if (seq middleware) `(compojure.core/wrap-routes ~form (mw/compose-middleware ~middleware)) form)]

        `(routes/map->Route
           {:path ~path-string
            :method ~method
            :info (merge-parameters ~info)
            :handler ~form})))))
