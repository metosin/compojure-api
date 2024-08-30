
(s/defn unwrap-schema-form-key :- (s/maybe (s/pair s/Keyword "k" s/Bool "optional?"))
  "Given a possibly-unevaluated schema map key form, unpack an explicit keyword
   and optional? flag, or return nil for a non-explicit key"
  [k]
  (cond (s/specific-key? k)
        [(s/explicit-schema-key k) (s/required-key? k)]

        ;; Deal with `(s/optional-key k) form from impl
        (and (sequential? k) (not (vector? k)) (= (count k) 2)
             (= (first k) 'schema.core/optional-key))
        [(second k) false]

        ;; Deal with `(with-meta ...) form from impl
        (and (sequential? k) (not (vector? k)) (= (first k) `with-meta))
        (unwrap-schema-form-key (second k))))
