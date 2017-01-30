(ns compojure.api.help
  (:require [schema.core :as s]
            [clojure.string :as str]))

(def Topic (s/maybe s/Keyword))
(def Subject (s/maybe (s/cond-pre s/Str s/Keyword s/Symbol)))

;;
;; content formatting
;;

(defn text [& s]
  (->> s
       (map #(if (seq? %) (apply text %) %))
       (str/join "\n")))

(defn title [& s]
  (str "\u001B[32m" (text s) "\u001B[0m"))

(defn code [& s]
  (str "\u001B[33m" (text s) "\u001B[0m"))

(defmulti help-for (fn [topic subject] [topic subject]) :default ::default)

(defn- subject-text [topic subject]
  (text
    (title subject)
    ""
    (help-for topic subject)))

(defn- topic-text [topic]
  (let [subjects (-> (methods help-for)
                     (dissoc ::default)
                     (keys)
                     (->> (filter #(-> % first (= topic)))))]
    (text
      "Topic:\n"
      (title topic)
      "\nSubjects:"
      (->> subjects
           (map (partial apply subject-text))
           (map (partial str "\n"))))))

(defn- help-text []
  (let [methods (dissoc (methods help-for) ::default)]
    (text
      "Usage:"
      ""
      (code
        "(help)"
        "(help topic)"
        "(help topic subject)")
      "\nTopics:\n"
      (title (->> methods keys (map first) (distinct) (sort)))
      "\nTopics & subjects:\n"
      (title (->> methods keys (map (partial str/join " ")) (sort))))))

(defmethod help-for ::default [_ _] (help-text))

(s/defn ^:always-validate help
  ([]
    (println "------------------------------------------------------------")
    (println (help-text)))
  ([topic :- Topic]
    (println "------------------------------------------------------------")
    (println (topic-text topic)))
  ([topic :- Topic, subject :- Subject]
    (println "------------------------------------------------------------")
    (println (subject-text topic subject))))
