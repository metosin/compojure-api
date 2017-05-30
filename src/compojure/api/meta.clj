(ns compojure.api.meta
  (:require [compojure.api.common :refer [extract-parameters]]
            [compojure.api.middleware :as mw]
            [compojure.api.routes :as routes]
            [plumbing.core :as p]
            [plumbing.fnk.impl :as fnk-impl]
            [ring.swagger.common :as rsc]
            [ring.swagger.json-schema :as js]
            [schema.core :as s]
            [schema-tools.core :as st]
            [compojure.api.coerce :as coerce]
            [compojure.api.help :as help]
            compojure.core
            compojure.api.compojure-compat))

(def +compojure-api-request+
  "lexically bound ring-request for handlers."
  '+compojure-api-request+)

;;
;; Schema
;;

(defn strict [schema]
  (dissoc schema 'schema.core/Keyword))

(defn fnk-schema [bind]
  (->>
    (:input-schema
      (fnk-impl/letk-input-schema-and-body-form
        nil (with-meta bind {:schema s/Any}) [] nil))
    reverse
    (into {})))

(s/defn src-coerce!
  "Return source code for coerce! for a schema with coercion type,
  extracted from a key in a ring request."
  ([schema, key, type :- mw/CoercionType]
    (src-coerce! schema, key, type, true))
  ([schema, key, type :- mw/CoercionType, keywordize?]
    `(coerce/coerce! ~schema ~key ~type ~keywordize? ~+compojure-api-request+)))

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
      "            :paramerers {:query {:q s/Str}"
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
  (update-in acc [:info :public] assoc :x-name v))

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
  (update-in acc [:middleware] conj [mw/wrap-coercion coercion]))

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
  `(#'compojure.api.compojure-compat/make-context
     ~(#'compojure.core/context-route path)
     (constantly ~route)))

(defn routing [handlers]
  (if-let [handlers (seq (keep identity handlers))]
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
    (seq responses) (assoc :responses (apply merge responses))
    swagger (-> (dissoc :swagger) (rsc/deep-merge swagger))))

(defn merge-parameters
  "Merge parameters at runtime to allow usage of runtime-paramers with route-macros."
  [info]
  (cond-> info
    (contains? info :public) (update :public merge-public-parameters)))

(defn- route-args? [arg]
  (not= arg []))

(defn restructure [method [path route-arg & args] {:keys [context? dynamic?]}]
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

        static? (not (or dynamic? (route-args? route-arg) (seq lets) (seq letks)))

        static-context? (and static? context?)
        info (cond-> info
               static-context? (assoc :static-context? static-context?))

        _ (assert (nil? swagger) ":swagger is deprecated with 2.0.0, use [:info :public] instead")

        ;; response coercion middleware, why not just code?
        middleware (if (seq responses) (conj middleware `[coerce/body-coercer-middleware (merge ~@responses)]) middleware)]

    (if context?

      ;; context
      (let [form `(routing [~@body])
            form (if (seq letks) `(p/letk ~letks ~form) form)
            form (if (seq lets) `(let ~lets ~form) form)
            form (if (seq middleware) `((mw/compose-middleware ~middleware) ~form) form)
            form (if static?
                   `(static-context ~path ~form)
                   `(compojure.core/context ~path ~arg-with-request ~form))

            ;; create and apply a separate lookup-function to find the inner routes
            childs (let [form (vec body)
                         form (if (seq letks) `(dummy-letk ~letks ~form) form)
                         form (if (seq lets) `(dummy-let ~lets ~form) form)
                         form `(compojure.core/let-request [~arg-with-request ~'+compojure-api-request+] ~form)
                         form `(fn [~'+compojure-api-request+] ~form)
                         form `(delay (~form {}))]
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
