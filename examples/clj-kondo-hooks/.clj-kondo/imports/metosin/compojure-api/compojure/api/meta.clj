(ns compojure.api.meta
  (:require [clojure.walk :as walk]
            [compojure.api.common :as common :refer [extract-parameters]]
            [compojure.api.middleware #?(:default :as-alias :default :as) mw]
            [compojure.api.routes #?(:default :as-alias :default :as) routes]
            [plumbing.core #?(:default :as-alias :default :as) p]
            [plumbing.fnk.impl #?(:default :as-alias :default :as) fnk-impl]
            [ring.swagger.common #?(:default :as-alias :default :as) rsc]
            [ring.swagger.json-schema #?(:default :as-alias :default :as) js]
            #?@(:default []
                :default [[schema.core :as s]
                          [schema-tools.core :as st]])
            [compojure.api.coerce #?(:default :as-alias :default :as) coerce]
            [compojure.core #?(:default :as-alias :default :as) comp-core]))

(defmacro ^:private system-property-check
  [& body]
  #?(:default nil
     :default `(do ~@body)))

(def +compojure-api-request+
  "lexically bound ring-request for handlers."
  '+compojure-api-request+)

;; https://github.com/clojure/tools.macro/blob/415512648bb51153f380823c41323cda2c13f47f/src/main/clojure/clojure/tools/macro.clj
;; Copyright (c) Rich Hickey. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (https://opensource.org/license/epl-1-0/)
;; which can be found in the file epl-v10.html at the root of this distribution. By using this software in any fashion, you are agreeing to
;; be bound bythe terms of this license. You must not remove this notice, or any other, from this software.
(defn name-with-attributes
  "To be used in macro definitions.
   Handles optional docstrings and attribute maps for a name to be defined
   in a list of macro arguments. If the first macro argument is a string,
   it is added as a docstring to name and removed from the macro argument
   list. If afterwards the first macro argument is a map, its entries are
   added to the name's metadata map and the map is removed from the
   macro argument list. The return value is a vector containing the name
   with its extended metadata map and the list of unprocessed macro
   arguments."
  [name macro-args]
  (let [[docstring macro-args] (if (string? (first macro-args))
                                 [(first macro-args) (next macro-args)]
                                 [nil macro-args])
    [attr macro-args]          (if (map? (first macro-args))
                                 [(first macro-args) (next macro-args)]
                                 [{} macro-args])
    attr                       (if docstring
                                 (assoc attr :doc docstring)
                                 attr)
    attr                       (if (meta name)
                                 (conj (meta name) attr)
                                 attr)]
    [(with-meta name attr) macro-args]))

;;
;; Schema
;;

(defn strict [schema]
  (dissoc schema 'schema.core/Keyword))

(defn fnk-schema [bind]
  #?(:default {}
     :default (->>
                (:input-schema
                  (fnk-impl/letk-input-schema-and-body-form
                    nil (with-meta bind {:schema s/Any}) [] nil))
                reverse
                (into {}))))

(defn src-coerce!
  "Return source code for coerce! for a schema with coercion type,
  extracted from a key in a ring request."
  [schema, key, type #_#_:- mw/CoercionType]
  (assert (not (#{:query :json} type)) (str type " is DEPRECATED since 0.22.0. Use :body or :string instead."))
  (assert (#{:body :string :response} type))
  `(coerce/coerce! ~schema ~key ~type ~+compojure-api-request+))

(defn- convert-return [schema]
  {200 {:schema schema
        :description (or #?(:default nil
                            :default (js/json-schema-meta schema))
                         "")}})

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
  (update-in acc [:swagger] assoc k v))

(defmethod restructure-param :description [k v acc]
  (update-in acc [:swagger] assoc k v))

(defmethod restructure-param :operationId [k v acc]
  (update-in acc [:swagger] assoc k v))

(defmethod restructure-param :consumes [k v acc]
  (update-in acc [:swagger] assoc k v))

(defmethod restructure-param :produces [k v acc]
  (update-in acc [:swagger] assoc k v))

;;
;; Smart restructurings
;;

; Boolean to discard the route out from api documentation
; Example:
; :no-doc true
(defmethod restructure-param :no-doc [_ v acc]
  (update-in acc [:swagger] assoc :x-no-doc v))

; publishes the data as swagger-parameters without any side-effects / coercion.
; Examples:
; :swagger {:responses {200 {:schema User}
;                       404 {:schema Error
;                            :description "Not Found"} }
;           :parameters {:query {:q s/Str}
;                        :body NewUser}}}
(defmethod restructure-param :swagger [_ swagger acc]
  (assoc-in acc [:swagger :swagger] swagger))

; Route name, used with path-for
; Example:
; :name :user-route
(defmethod restructure-param :name [_ v acc]
  (update-in acc [:swagger] assoc :x-name v))

; Tags for api categorization. Ignores duplicates.
; Examples:
; :tags [:admin]
(defmethod restructure-param :tags [_ tags acc]
  (update-in acc [:swagger :tags] (comp set into) tags))

; Defines a return type and coerces the return value of a body against it.
; Examples:
; :return MySchema
; :return {:value String}
; :return #{{:key (s/maybe Long)}}
(defmethod restructure-param :return [_ schema acc]
  (let [response (convert-return schema)]
    (-> acc
        (update-in [:swagger :responses] (fnil conj []) response)
        (update-in [:responses] (fnil conj []) response))))

; value is a map of http-response-code -> Schema. Translates to both swagger
; parameters and return schema coercion. Schemas can be decorated with meta-data.
; Examples:
; :responses {403 nil}
; :responses {403 {:schema ErrorEnvelope}}
; :responses {403 {:schema ErrorEnvelope, :description \"Underflow\"}}
(defmethod restructure-param :responses [_ responses acc]
  (-> acc
      (update-in [:swagger :responses] (fnil conj []) responses)
      (update-in [:responses] (fnil conj []) responses)))

; reads body-params into a enhanced let. First parameter is the let symbol,
; second is the Schema to be coerced! against.
; Examples:
; :body [user User]
(defmethod restructure-param :body [_ [value schema :as bv] acc]
  (system-property-check
    (when-not (= "true" (System/getProperty "compojure.api.meta.allow-bad-body"))
      (assert (= 2 (count bv))
              (str ":body should be [sym schema], provided: " bv
                   "\nDisable this check with -Dcompojure.api.meta.allow-bad-body=true"))))
  (-> acc
      (update-in [:lets] into [value (src-coerce! schema :body-params :body)])
      (assoc-in [:swagger :parameters :body] schema)))

; reads query-params into a enhanced let. First parameter is the let symbol,
; second is the Schema to be coerced! against.
; Examples:
; :query [user User]
(defmethod restructure-param :query [_ [value schema :as bv] acc]
  (system-property-check
    (when-not (= "true" (System/getProperty "compojure.api.meta.allow-bad-query"))
      (assert (= 2 (count bv))
              (str ":query should be [sym schema], provided: " bv
                   "\nDisable this check with -Dcompojure.api.meta.allow-bad-query=true"))))
  (-> acc
      (update-in [:lets] into [value (src-coerce! schema :query-params :string)])
      (assoc-in [:swagger :parameters :query] schema)))

; reads header-params into a enhanced let. First parameter is the let symbol,
; second is the Schema to be coerced! against.
; Examples:
; :headers [headers Headers]
(defmethod restructure-param :headers [_ [value schema :as bv] acc]
  (system-property-check
    (when-not (= "true" (System/getProperty "compojure.api.meta.allow-bad-headers"))
      (assert (= 2 (count bv))
              (str ":headers should be [sym schema], provided: " bv
                   "\nDisable this check with -Dcompojure.api.meta.allow-bad-headers=true"))))

  (-> acc
      (update-in [:lets] into [value (src-coerce! schema :headers :string)])
      (assoc-in [:swagger :parameters :header] schema)))

; restructures body-params with plumbing letk notation. Example:
; :body-params [id :- Long name :- String]
(defmethod restructure-param :body-params [_ body-params acc]
  (let [schema (strict (fnk-schema body-params))]
    (-> acc
        (update-in [:letks] into [body-params (src-coerce! schema :body-params :body)])
        (assoc-in [:swagger :parameters :body] schema))))

; restructures form-params with plumbing letk notation. Example:
; :form-params [id :- Long name :- String]
(defmethod restructure-param :form-params [_ form-params acc]
  (let [schema (strict (fnk-schema form-params))]
    (-> acc
        (update-in [:letks] into [form-params (src-coerce! schema :form-params :string)])
        #?@(:default []
            :default [(update-in [:swagger :parameters :formData] st/merge schema)])
        (assoc-in [:swagger :consumes] ["application/x-www-form-urlencoded"]))))

; restructures multipart-params with plumbing letk notation and consumes "multipart/form-data"
; :multipart-params [file :- compojure.api.upload/TempFileUpload]
(defmethod restructure-param :multipart-params [_ params acc]
  (let [schema (strict (fnk-schema params))]
    (-> acc
        (update-in [:letks] into [params (src-coerce! schema :multipart-params :string)])
        #?@(:default []
            :default [(update-in [:swagger :parameters :formData] st/merge schema)])
        (assoc-in [:swagger :consumes] ["multipart/form-data"]))))

; restructures header-params with plumbing letk notation. Example:
; :header-params [id :- Long name :- String]
(defmethod restructure-param :header-params [_ header-params acc]
  (let [schema (fnk-schema header-params)]
    (-> acc
        (update-in [:letks] into [header-params (src-coerce! schema :headers :string)])
        (assoc-in [:swagger :parameters :header] schema))))

; restructures query-params with plumbing letk notation. Example:
; :query-params [id :- Long name :- String]
(defmethod restructure-param :query-params [_ query-params acc]
  (let [schema (fnk-schema query-params)]
    (-> acc
        (update-in [:letks] into [query-params (src-coerce! schema :query-params :string)])
        (assoc-in [:swagger :parameters :query] schema))))

; restructures path-params by plumbing letk notation. Example:
; :path-params [id :- Long name :- String]
(defmethod restructure-param :path-params [_ path-params acc]
  (let [schema (fnk-schema path-params)]
    (-> acc
        (update-in [:letks] into [path-params (src-coerce! schema :route-params :string)])
        (assoc-in [:swagger :parameters :path] schema))))

; Applies the given vector of middlewares to the route
(defmethod restructure-param :middleware [_ middleware acc]
  (update-in acc [:middleware] into middleware))

; Bind to stuff in request components using letk syntax
(defmethod restructure-param :components [_ components acc]
  (update-in acc [:letks] into [components `(mw/get-components ~+compojure-api-request+)]))

; route-specific override for coercers
(defmethod restructure-param :coercion [_ coercion acc]
  (update-in acc [:middleware] conj [#?(:default `mw/wrap-coercion
                                        ;;FIXME why not quoted?
                                        :default mw/wrap-coercion)
                                     coercion]))

;;
;; Impl
;;

#?(:default nil
   :default
(defmacro dummy-let
  "Dummy let-macro used in resolving route-docs. not part of normal invocation chain."
  [bindings & body]
  (let [bind-form (vec (apply concat (for [n (take-nth 2 bindings)] [n nil])))]
    `(let ~bind-form ~@body)))
)

#?(:default nil
   :default
(defmacro dummy-letk
  "Dummy letk-macro used in resolving route-docs. not part of normal invocation chain."
  [bindings & body]
  (reduce
    (fn [cur-body-form [bind-form]]
      (if (symbol? bind-form)
        `(let [~bind-form nil] ~cur-body-form)
        (let [{:keys [map-sym body-form]} (fnk-impl/letk-input-schema-and-body-form ;;TODO clj-kondo
                                            &env
                                            (fnk-impl/ensure-schema-metadata &env bind-form)
                                            []
                                            cur-body-form)
              body-form (walk/prewalk-replace {'plumbing.fnk.schema/safe-get 'clojure.core/get} body-form)]
          `(let [~map-sym nil] ~body-form))))
    `(do ~@body)
    (reverse (partition 2 bindings))))
)

#?(:default nil
   :default
(defn routing [handlers]
  (if-let [handlers (seq (keep identity (flatten handlers)))]
    (apply comp-core/routes handlers)
    (fn ([_] nil) ([_ respond _] (respond nil)))))
)

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
              (ex-info
                (str "unknown compojure destruction syntax: " arg)
                {})))))

(defn merge-parameters
  "Merge parameters at runtime to allow usage of runtime-parameters with route-macros."
  [{:keys [responses swagger] :as parameters}]
  #?(:default parameters
     :default (cond-> parameters
                (seq responses) (assoc :responses (common/merge-vector responses))
                swagger (-> (dissoc :swagger) (rsc/deep-merge swagger)))))

(defn restructure [method [path arg & args] {:keys [context? kondo-rule?]}]
  #?(:default (assert kondo-rule?)
     :default nil)
  (let [[options body] (extract-parameters args true)
        [path-string lets arg-with-request arg] (destructure-compojure-api-request path arg)

        {:keys [lets
                letks
                responses
                middleware
                middlewares
                swagger
                parameters
                body]} (reduce
                         (fn [acc [k v]]
                           (restructure-param k v (update-in acc [:parameters] dissoc k)))
                         {:lets lets
                          :letks []
                          :responses nil
                          :middleware []
                          :swagger {}
                          :body body
                          :kondo-rule? kondo-rule?}
                         options)

        ;; migration helpers
        _ (assert (not middlewares) ":middlewares is deprecated with 1.0.0, use :middleware instead.")
        _ (assert (not parameters) ":parameters is deprecated with 1.0.0, use :swagger instead.")

        ;; response coercion middleware, why not just code?
        middleware (if (seq responses) (conj middleware `[coerce/body-coercer-middleware (common/merge-vector ~responses)]) middleware)]

    #?(:default (let [form `(do ~@body)
                        form (if (seq letks) `(p/letk ~letks ~form) form)
                        form (if (seq lets) `(let ~lets ~form) form)]
                    `(fn [~+compojure-api-request+] ~form))
    :default
    (if context?
      ;; context
      (let [form `(comp-core/routes ~@body)
            form (if (seq letks) `(p/letk ~letks ~form) form)
            form (if (seq lets) `(let ~lets ~form) form)
            form (if (seq middleware) `((mw/compose-middleware ~middleware) ~form) form)
            form `(comp-core/context ~path ~arg-with-request ~form)

            ;; create and apply a separate lookup-function to find the inner routes
            childs #?(:default nil
                      :default (let [form (vec body)
                                     form (if (seq letks) `(dummy-letk ~letks ~form) form)
                                     form (if (seq lets) `(dummy-let ~lets ~form) form)
                                     form `(comp-core/let-request [~arg-with-request ~'+compojure-api-request+] ~form)
                                     form `(fn [~'+compojure-api-request+] ~form)
                                     form `(~form {})]
                                 form))]

        `(routes/create ~path-string ~method (merge-parameters ~swagger) ~childs ~form))

      ;; endpoints
      (let [form `(do ~@body)
            form (if (seq letks) `(p/letk ~letks ~form) form)
            form (if (seq lets) `(let ~lets ~form) form)
            form (comp-core/compile-route method path arg-with-request (list form))
            form (if (seq middleware) `(comp-core/wrap-routes ~form (mw/compose-middleware ~middleware)) form)]

        `(routes/create ~path-string ~method (merge-parameters ~swagger) nil ~form))))))
