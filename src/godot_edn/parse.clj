(ns godot-edn.parse
  (:require
   [babashka.fs :as fs]
   [instaparse.core :as insta]
   [clojure.edn :as edn]
   [clojure.string :as string]
   ))

(def shared-transform
  {:key_val (fn [& args]
              {(-> args first str
                   ((fn [key]
                      (if (<= (count (string/split key #"/")) 2)
                        (keyword key)
                        key))))
               (-> args second)})
   :number  #(edn/read-string (apply str %&))
   :bool    (fn [val] (case val "true" true "false" false))
   :dict    (fn [& kvs]
              (->> kvs (partition 2 2)
                   (map (fn [[key val]] [(keyword key) val]))
                   (into {})))
   :list    #(into [] %&)
   :global  (fn [global]
              (symbol global))
   :class   (fn [cls & args]
              (cons (symbol cls) args))})

;; project.godot files

(def projects-godot-grammar
  (insta/parser "<project_config> = (<ows> comment <ows> | <ows> key_val <ows> | <ows> section_header <ows>)+
comment = #';.*'
<ows> = #'\\s*'
<rws> = #'\\s+'
section_header = <'['> word <']'>
<word> = #'[A-Za-z0-9/_.-]+'
key_val = key <'='> value
<key> = word

<value> = string | number | bool | dict | class | !bool global | list
<string> = <'\"'> chars? <'\"'>
<chars> = #'(^\\\"|\\\\\"|[A-Za-z0-9_.:/ *,-@#!?${}()\n\\'\\\\\\|])*'
list = <'['> (<ows> value <ows> <','?>)* <']'>
number = '-'? digits '.'? digits?
<digits> = #'[0-9]+'
bool = 'true' | 'false'
dict = <'{'> (<ows> string <':'> <rws> value <ows> <','?>)* <'}'>
class = #'[A-Za-z0-9]+' <'('> args? <')'>
<args> = value (<','> <ows> (value | kwarg) <ows>)*
kwarg = string <':'> value
global = #'[A-Za-z]+'
 "))

(def project-transform-def
  (merge
    shared-transform
    {:section_header (comp keyword str)
     :kwarg          (fn [key & vals]
                       [(keyword key) (some-> vals first)])
     :comment        (fn [& comments]
                       {:comment (apply str comments)})}))

(defn project->edn [parsed]
  (when-not (insta/failure? parsed)
    (let [parts     (->> (insta/transform project-transform-def parsed)
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
  (let [result (insta/parse projects-godot-grammar content)]
    (when (insta/failure? result) (println result))
    result))


;; .tscn files

(def tscn-grammar
  (insta/parser "
<parsed> = (<ows> tscn_line <ows>)+
<tscn_line> = gd_scene | ext_resource | sub_resource | node | key_val
<ows> = #'\\s*'
<rws> = #'\\s+'

gd_scene = <'[gd_scene'> kwargs <']'>
ext_resource = <'[ext_resource'> kwargs <']'>
sub_resource = <'[sub_resource'> kwargs <']'>
node = <'[node'> kwargs <']'>

<keyword> = #'[a-z_]+'
kwargs = (<rws> kwarg)*
kwarg = keyword <'='> kwarg_value
<kwarg_value> = string | number | class


key_val = keyword <' = '> value

<value> = string | number | bool | dict | class | !bool global | list
<string> = <'\"'> chars? <'\"'>
<chars> = #'(^\\\"|\\\\\"|[A-Za-z0-9_.:/ *,-@#!?${}()\n\\'\\\\\\|])*'
list = <'['> (<ows> value <ows> <','?>)* <']'>
number = '-'? digits '.'? digits?
<digits> = #'[0-9]+'
bool = 'true' | 'false'
dict = <'{'> (<ows> string <':'> <rws> value <ows> <','?>)* <'}'>
class = #'[A-Za-z0-9]+' <'('> args? <')'>
<args> = value (<','> <ows> value <ows>)*
global = #'[A-Za-z]+'
"))


(comment
  (def sample
    "[gd_scene load_steps=43 format=3 uid=\"uid://cdtqoa3gaqsdh\"]

[ext_resource type=\"Script\" path=\"res://src/hatbot/player/Player.gd\" id=\"1_20cuc\"]
[ext_resource type=\"PackedScene\" uid=\"uid://dgan7tpytfkfo\" path=\"res://addons/beehive/sidescroller/machine/SSMachine.tscn\" id=\"11_oh4d4\"]

[sub_resource type=\"OccluderPolygon2D\" id=\"OccluderPolygon2D_fow7p\"]
cull_mode = 2
polygon = PackedVector2Array(-3, -4, 3, -4, 3, 1, 2, 5, -2, 5, -3, 1)

[sub_resource type=\"CapsuleShape2D\" id=\"CapsuleShape2D_ll7ph\"]
radius = 3.8
height = 12.0

[node name=\"NearGroundCheck\" type=\"RayCast2D\" parent=\".\"]
target_position = Vector2(0, 40)

[node name=\"HeartParticles\" parent=\".\" instance=ExtResource(\"8_atx5s\")]

[node name=\"SkullParticles\" parent=\".\" instance=ExtResource(\"9_u5coh\")]

[node name=\"StateLabel\" type=\"RichTextLabel\" parent=\".\"]
clip_contents = false
offset_left = -24.0
")

  (->> sample
       (#(insta/parses tscn-grammar
                       %
                       ;; :unhide :all
                       )))

  (->> sample parse-tscn tscn->edn)

  )

(def tscn-transform-def
  (merge
    shared-transform
    {:kwarg  (fn [key val] [(keyword key) val])
     :kwargs #(into {} %&)}))

(defn tscn->edn [parsed]
  (if (insta/failure? parsed)
    parsed
    (->> (insta/transform tscn-transform-def parsed)
         (partition-by (comp #{:gd_scene :ext_resource :sub_resource :node} first))
         (mapcat (fn [groups]
                   (if (and (-> groups first vector?)
                            (-> groups ffirst #{:sub_resource :node})
                            (-> groups count (> 1)))
                     ;; flatten nodes without config data
                     ;; so that the following key_vals can be applied to the last one
                     ;; we (map list) to get back to the same structure
                     (->> groups (map list))
                     [groups])))
         ((fn [partitions]
            (let [
                  gd_scene (->> partitions (filter (comp #{:gd_scene} ffirst)) first)
                  ext_res  (->> partitions (filter (comp #{:ext_resource} ffirst)) first
                                (map second))
                  rst      (->> partitions (remove (comp #{:gd_scene :ext_resource} ffirst)))
                  elms     (reduce
                             (fn [ps p]
                               (if (-> p ffirst #{:sub_resource :node})
                                 (into [] (concat ps [(first p)]))
                                 (into [] (concat (butlast ps)
                                                  [(into [] (concat (last ps) [(apply merge p)]))]))))
                             []
                             rst)
                  sub_res  (->> elms (filter (comp #{:sub_resource} first)) (map rest))
                  nodes    (->> elms (filter (comp #{:node} first)) (map rest))]
              {:gd_scene           (-> gd_scene first second)
               :external_resources ext_res
               :sub_resources      sub_res
               :nodes              nodes}))))))

(defn parse-tscn [content]
  (let [result (insta/parse tscn-grammar content)]
    (when (insta/failure? result) (println result))
    result))

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
        (#{"godot" "cfg"} ext) (->> content (insta/parse projects-godot-grammar) project->edn)
        (#{"tscn" "tres"} ext) (->> content (insta/parse tscn-grammar) tscn->edn)
        :else                  (println "Unexpected file extension:" ext)))))

(comment
  (parse-godot-file (str (fs/home) "/russmatney/dino/src/hatbot/player/Player.tscn"))
  (slurp
    (str (fs/home) "/russmatney/dino/src/hatbot/player/Player.tscn"))

  (parse-godot-file (str (fs/home) "/russmatney/dino/project.godot"))
  (parse-godot-file (str (fs/home) "/russmatney/dino/export_presets.cfg")))
