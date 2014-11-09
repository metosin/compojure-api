(ns examples.thingie
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [ring.swagger.schema :refer [field describe]]
            ring.swagger.json-schema-dirty
            [examples.domain :refer :all]
            [examples.dates :refer :all]))

;;
;; Schemas
;;

(s/defschema Recursive {(s/optional-key :foobar) (describe (s/either (s/recursive #'Recursive) String) "Another Recursive or a String")})

(s/defschema Total {:total Long})

(s/defschema ErrorEnvelope {:message String})

;;
;; Routes
;;

(defapi app
  (swagger-ui)
  (swagger-docs
    :title "Api thingies"
    :description "playing with things")

  (swaggered "math"
    :description "Math with parameters"
    (context "/math" []

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
        (ok {:total (long (Math/pow x y))}))))

  (swaggered "pizza"
    :description "Pizza api"
    pizza-routes)

  (swaggered "dates"
    :description "Roundrobin of Dates"
    date-routes)

  (swaggered "responses"
    :description "responses demo"
    (context "/responses" []
      (POST* "/number" []
        :return       Total
        :query-params [x :- Long y :- Long]
        :responses    {403 ^{:message "Underflow"} ErrorEnvelope}
        :summary      "x-y with body-parameters."
        (let [total (- x y)]
          (if (>= total 0)
            (ok {:total (- x y)})
            (forbidden {:message "difference is negative"}))))))

  (swaggered "primitives"
    :description "returning primitive values"
    (context "/primitives" []

      (GET* "/plus" []
        :return       Long
        :query-params [x :- Long {y :- Long 1}]
        :summary      "x+y with query-parameters. y defaults to 1."
        (ok (+ x y)))

      (GET* "/datetime-now" []
        :return org.joda.time.DateTime
        :summary "current datetime"
        (ok (org.joda.time.DateTime.)))


      (GET* "/hello" []
        :return String
        :query-params [name :- (describe String "foobar")
                       ;; Broken on aot
                       ; foo :- (s/if (constantly true) String Long)
                       ]
        :notes   "<h1>hello world.</h1>"
        :summary "echos a string from query-params"
        (ok (str "hello, " name)))))

  (swaggered "echo"
    :description "echoes data"
    (context "/echo" []

    (POST* "/recursion" []
      :return   Recursive
      :body     [body (describe Recursive "Recursive Schema")]
      :summary  "echoes a the json-body"
      (ok body))

    (PUT* "/anonymous" []
      :return   [{:hot Boolean}]
      :body     [body [{:hot (s/either Boolean String)}]]
      :summary  "echoes a vector of anonymous hotties"
      (ok body)))))
