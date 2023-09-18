(ns inverted-index-demo
  (:require [clojure.string]
            [clojure.set]
            [babashka.fs :as fs]))

(comment "Como os indexadores da web fazem o que fazem?"
         "Como recuperar uma página web com conteúdo relacionado a uma busca textual?")

(comment "Original implementation from stackoverflow: https://codereview.stackexchange.com/questions/19268/inverted-index-in-clojure-performance-vs-idiomatic-code"
         (def p (re-pattern #"[^\p{L}&&[^\p{M}]]"))

         (defn invert[file]
           (let [f      (.getName file)
                 tokens (.split p (lower-case (slurp file)))]
             (into {} (mapcat #(hash-map % #{f}) tokens))))

         (defn build-index[dirname]
           (reduce #(merge-with union %1 %2) (map invert (.listFiles (java.io.File. dirname))))))

(comment "Below I fine tuned the implementation to index clj files from the monorepo"

         "Note that this implementation does not take into account the number of times that a token appeared in a document."

         (def tokenizer-pattern #"[\n\(\)\[\]\s \"'\.]")

         (defn tokenize-string [s]
           (as-> s x
             (clojure.string/lower-case x)
             (clojure.string/split x tokenizer-pattern)
             (remove empty? x)))

         (tokenize-string "(ns [projects.hermes.core]")
         (tokenize-string "(->> xs
                                (map :foo)
                                sort")

         (defn tokenize-file [f]
           (as-> f x
             (slurp x)
             (tokenize-string x)))

         (tokenize-file "projects/hermes/src/projects/hermes/core.clj")

         (defn invert [file]
           (let [f (str file)
                 tokens (tokenize-file f)]
             #_"If we would like to take the number of occurrences of token on a file, we would have to update the accumulator function below"
             (into {} (mapcat #(hash-map % #{f}) tokens))))

         (invert "projects/hermes/src/projects/hermes/core.clj")

         (defn merge-indexes [a b] (merge-with clojure.set/union a b))

         (merge-indexes
          {"foo" #{1}}
          {"foo" #{2}
           "bar" #{3}})

         (defn build-index [dirname]
           (let [files (fs/glob dirname "**.clj")
                 indexes (map invert files)]
             (reduce merge-indexes indexes)))

         (build-index "sample"))

(comment "Let's use it on the monorepo!"
         (def index (build-index "."))

         (->>
          ["->>" "->" "as->"] ;; AND, ...
          (mapcat (comp vec index))
          frequencies
          (into [])
          (sort-by second)
          (reverse)))

(comment "What is the size of the index?"
         (defn total-memory [obj]
           (let [baos (java.io.ByteArrayOutputStream.)]
             (with-open [oos (java.io.ObjectOutputStream. baos)]
               (.writeObject oos obj))
             (count (.toByteArray baos))))

         (print "Size in bytes: "
              (total-memory index)))

(comment "What if we dump the index to file?"
         (let [filename "invested-index.edn"]
           (spit filename index)
           (print "The produced file has (in bytes):" (fs/size filename))))

(comment "O que mais fazem as search engines parrudas (e.g. elastic search)? Alguns exemplos:"
         ["Guardam binômios, trinômios, ...."]
         ["Guardam no posting list tanto a contagem da ocorrência de tokens, e a localização da ocorrência"]
         ["Comprimem os posting lists: bitmaps, bitmaps comprimidos, queries em bitmaps, queries em bitmaps comprimidos, bloom filter,..."]
         ["Utilizam uma estrutura de dados baseada em grafo para fazer queries fuzy"]
         ["Lidam com armazenamento persistente dos inverted indexes: tentam minimizar o número de acesso a discos necessário para completar busca"]
         ["Implementam queries de AND, OR, XOR, ...."])
