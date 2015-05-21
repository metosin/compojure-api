(ns examples.thingie
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [compojure.api.upload :refer :all]
            [schema.core :as s]
            ring.swagger.json-schema-dirty
            [examples.pizza :refer [pizza-routes Pizza]]
            [examples.ordered :refer [ordered-routes]]
            [examples.dates :refer [date-routes]])
  (:import [org.joda.time DateTime]))

;;
;; Schemas
;;

(s/defschema Recursive {(s/optional-key :foobar) (describe (s/either (s/recursive #'Recursive) String) "Another Recursive or a String")})

(s/defschema Total {:total Long})

;;
;; Routes
;;

(defapi app
  (swagger-ui)
  (swagger-docs
    {:info {:version "1.0.0"
            :title "Thingies API"
            :description "the description"
            :termsOfService "http://www.metosin.fi"
            :contact {:name "My API Team"
                      :email "foo@example.com"
                      :url "http://www.metosin.fi"}
            :license {:name "Eclipse Public License"
                      :url "http://www.eclipse.org/legal/epl-v10.html"}}
     :tags [{:name "math", :description "Math with parameters"}
            {:name "pizzas", :description "Pizza API"}
            {:name "failing", :description "Handling uncaught exceptions"}
            {:name "dates", :description "Dates API"}
            {:name "responses", :description "Responses demo"}
            {:name "primitives", :description "Returning primitive values"}
            {:name "context*", :description "context* routes"}
            {:name "echo", :description "Echoes data"}
            {:name "ordered", :description "Ordered schema"}
            {:name "file", :description "File upload"}]})

  (context* "/math" []
    :tags ["math"]

    (GET* "/plus" []
      :return       Total
      ;; You can add any keys to meta-data, but Swagger-ui might not show them
      :query-params [x :- (describe Long "description")
                     {y :- Long 1}]
      :summary      "x+y with query-parameters. y defaults to 1."
      (ok {:total (+ x y)}))

    (POST* "/minus" []
      :return      Total
      :body-params [x :- (describe Long "first param")
                    y :- (describe Long "second param")]
      :summary     "x-y with body-parameters."
      (ok {:total (- x y)}))

    (GET* "/times/:x/:y" []
      :return      Total
      :path-params [x :- Long y :- Long]
      :summary     "x*y with path-parameters"
      (ok {:total (* x y)}))

    (POST* "/req" req (ok (dissoc req :body )))

    (POST* "/divide" []
      :return      {:total Double}
      :form-params [x :- Long y :- Long]
      :summary     "x/y with form-parameters"
      (ok {:total (/ x y)}))

    (GET* "/power" []
      :return        Total
      :header-params [x :- Long y :- Long]
      :summary       "x^y with header-parameters"
      (ok {:total (long (Math/pow x y))})))

  (context* "/failing" []
    :tags ["failing"]
    (GET* "/exceptions" []
      (throw (RuntimeException. "KOSH"))))

  pizza-routes

  #_ordered-routes

  (context* "/dates" []
    :tags ["dates"]
    date-routes)

  (context* "/responses" []
    :tags ["responses"]
    (GET* "/" []
      :query-params [return :- (s/enum :200 :403 :404)]
      :responses    {403 {:schema {:code s/Str, :description "spiders?"}}
                     404 {:schema {:reson s/Str}, :description "lost?"}}
      :return       Total
      :summary      "multiple returns models"
      (case return
        :200 (ok {:total 42})
        :403 (forbidden {:code "forest"})
        :404 (not-found {:reason "lost"}))))

  (context* "/primitives" []
    :tags ["primitives"]

    (GET* "/plus" []
      :return       Long
      :query-params [x :- Long {y :- Long 1}]
      :summary      "x+y with query-parameters. y defaults to 1."
      (ok (+ x y)))

    (GET* "/datetime-now" []
      :return DateTime
      :summary "current datetime"
      (ok (DateTime.)))

    (GET* "/hello" []
      :return String
      :query-params [name :- (describe String "foobar")
                     ;; Broken on aot
                     ; foo :- (s/if (constantly true) String Long)
                     ]
      :description  "<h1>hello world.</h1>"
      :summary "echos a string from query-params"
      (ok (str "hello, " name))))

  (context* "/context" []
    :summary "summary inherited from context"
    :tags ["context*"]

    (context* "/:kikka" []
      :path-params [kikka :- s/Str]
      :query-params [kukka :- s/Str]

      (GET* "/:kakka" []
        :return {:kikka s/Str
                 :kukka s/Str
                 :kakka s/Str}
        :path-params [kakka :- s/Str]
        (ok {:kikka kikka
             :kukka kukka
             :kakka kakka}))))

  (context* "/echo" []
    :tags ["echo"]

    (POST* "/recursion" []
      :return   Recursive
      :body     [body (describe Recursive "Recursive Schema")]
      :summary  "echoes a the json-body"
      (ok body))

    (PUT* "/anonymous" []
      :return   [{:hot Boolean}]
      :body     [body [{:hot (s/either Boolean String)}]]
      :summary  "echoes a vector of anonymous hotties"
      (ok body)))

  (context* "/foreign" []
    :tags ["foreign"]

    (POST* "/pizza" []
      :summary "Foreign schema with unknown subschemas"
      :return (s/maybe Pizza)
      :body [body Pizza]
      (ok)))

  (context* "/foreign" []
    :tags ["abc"]

    (GET* "/abc" []
      :summary "Foreign schema with unknown subschemas"
      :return (s/maybe Pizza)
      (ok))
    (GET* "/info" []
      :summary "from examples.thingie ns"
      (ok {:source "examples.thingie"})))

  (context* "/file" []
    :tags ["file"]

    (POST* "/upload" []
      :multipart-params [file :- TempFileUpload]
      :middlewares [wrap-multipart-params]
      :consumes ["multipart/form-data"]
      (ok (dissoc file :tempfile)))))
