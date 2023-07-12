(ns godot-edn.parse
  (:require
   [babashka.fs :as fs]
   [instaparse.core :as insta]
   [clojure.edn :as edn]
   [clojure.string :as string]
   ))

;; project.godot files

(def projects-godot-grammar
  (insta/parser "<project_config> = line+
<line> = line_content? <newline?>
<line_content> = <wsp> | comment | key_val | section_header
<newline> = '\n'
comment = ';' #'.*'?
<wsp> = #'\\s+'
section_header = <'['> word <']'>
<word> = #'[A-Za-z0-9/_.-]+'
key_val = key <'='> value
<key> = word

<wsp-or-nl> = <wsp> | <newline>

<value> = string | number | bool | dict | class | !bool global | list
<string> = <'\"'> chars <'\"'>
list = <'['> value <wsp*> (<','> <wsp*> value <wsp-or-nl*>)* <']'>
<chars> = #'[A-Za-z0-9_.:/ *]*'
number = '-'? digit* '.'? digit*
<digit> = #'[0-9]'
bool = 'true' | 'false'
dict = <'{'> (<wsp*> string <':'> <wsp*> value <','?> <wsp*>)* <newline?> <'}'>
class = #'[A-Za-z0-9]+' args
<args> = <'('> value (<','> <wsp*> (value | kwarg) <wsp*>)* <')'>
kwarg = string <':'> value
global = #'[A-Za-z]+'
 "))

(def transform-def
  {:section_header (comp keyword str)
   :key_val        (fn [& args]
                     {(-> args first str
                          ((fn [key]
                             (if (<= (count (string/split key #"/")) 2)
                               (keyword key)
                               key))))
                      (-> args second)})
   :number         #(edn/read-string (apply str %&))
   :bool           (fn [val] (case val "true" true "false" false))
   :dict           (fn [& kvs]
                     (->> kvs (partition 2 2)
                          (map (fn [[key val]] [(keyword key) val]))
                          (into {})))
   :list           #(into [] %&)
   :kwarg          (fn [key val]
                     [(keyword key) val])
   :global         (fn [global]
                     (symbol global))
   :class          (fn [cls & args]
                     (cons (symbol cls) args))
   :comment        (fn [& comments]
                     {:comment (apply str comments)})})

(defn project->edn [parsed]
  (when-not (insta/failure? parsed)
    (let [parts     (->> (insta/transform transform-def parsed)
                         (partition-by keyword?))
          parts     (if-not (some-> parts first first keyword?)
                      (cons (list :_top_level) parts) parts)
          sections
          (cond->> parts
            (> (count parts) 1) (partition 2 2)
            (= (count parts) 1) (map (fn [[kwd]] [[kwd] [{}]]))
            true
            (map (fn [[section kvs]]
                   (let [comments (->> kvs (map first)
                                       (filter (comp #{:comment} first))
                                       (map second) (into []))
                         rest     (->> kvs (map first)
                                       (remove (comp #{:comment} first)))]
                     {(first section)
                      (into {} (apply merge rest
                                      (when (seq comments)
                                        {:comments comments})))})))
            true                (apply merge))
          top-level (:_top_level sections)]
      (-> sections
          (merge top-level)
          (dissoc :_top_level)))))

(defn parse-project [content]
  (let [result (insta/parse projects-godot-grammar content
                            #_#_:trace true)]
    (when (insta/failure? result) (println (insta/get-failure result)))
    result))

(comment
  (-> "; Some comment
; another comment" parse-project project->edn)

  (->
    "ui_accept={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0)
]} " parse-project project->edn)

  (-> "config_version=5

[application]

config/name=\"Dino\"
config/features=PackedStringArray(\"4.1\")

[rendering]

textures/canvas_textures/default_texture_filter=0
2d/snapping/use_gpu_pixel_snap=true
environment/default_clear_color=Color(0, 0, 0, 1)"
      parse-project project->edn))


;; .tscn files

(def tscn-grammar (insta/parser "some = 'some'"))

(comment
  (insta/parse projects-godot-grammar ""))


;; .tres files

(def tres-grammar
  (insta/parser "some = 'some'"))

(comment
  (insta/parse projects-godot-grammar ""))


;; public

(defn parse-godot-file [path]
  (let [path    (if (string? path) (fs/file path) path)
        ext     (fs/extension path)
        content (-> path slurp)]
    #_content
    (if-not (fs/exists? path)
      (println "No file at path:" path)
      (cond
        (#{"godot"} ext) (insta/parse projects-godot-grammar content)
        (#{"cfg"} ext)   (insta/parse projects-godot-grammar content)
        (#{"tscn"} ext)  (insta/parse tscn-grammar content)
        (#{"tres"} ext)  (insta/parse tres-grammar content)
        :else            (println "Unexpected file extension:" ext)))))

(comment
  (parse-godot-file
    (str (fs/home) "/russmatney/dino/project.godot"))

  (parse-godot-file
    (str (fs/home) "/russmatney/dino/project.godot")))
