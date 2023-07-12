(ns godot-edn.parse
  (:require
   [babashka.fs :as fs]
   [instaparse.core :as insta]
   [clojure.edn :as edn]))


(def example-project-input "; Engine configuration file.
; It's best edited using the editor UI and not directly,
; since the parameters that go here are not all obvious.
;
; Format:
;   [section] ; section goes between []
;   param=value ; assign values to parameters

config_version=5

[application]

config/name=\"Dino\"
run/main_scene=\"res://src/dino/DinoMenu.tscn\"
config/features=PackedStringArray(\"4.1\")
config/icon=\"res://assets/icons/dino_sheet.png\"

[input]

ui_accept={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194309,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194309,\"echo\":false,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194310,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194310,\"echo\":false,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":32,\"physical_keycode\":0,\"key_label\":0,\"unicode\":32,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":1,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}

[physics]

common/enable_pause_aware_picking=true

[rendering]

textures/canvas_textures/default_texture_filter=0
2d/snapping/use_gpu_pixel_snap=true
environment/default_clear_color=Color(0, 0, 0, 1)
environment/default_environment=\"res://default_env.tres\"
textures/vram_compression/import_s3tc_bptc.macos=true
")


(def projects-godot-grammar
  (insta/parser "<project_config> = line+
<line> = line_content? <newline?>
<line_content> = <wsp> | comment | key_val | section_header
<newline> = '\n'
comment = ';' <wsp*> #'.*'
<wsp> = #'\\s+'
section_header = <'['> word <']'>
<word> = #'[A-Za-z0-9/_.]+'
key_val = key <'='> value
<key> = word

<wsp-or-nl> = <wsp> | <newline>

<value> = string | number | bool | dict | class | !bool global | list
<string> = <'\"'> chars <'\"'>
list = <'['> value <wsp*> (<','> <wsp*> value <wsp-or-nl*>)* <']'>
<chars> = #'[A-Za-z0-9_.:/ ]*'
number = '-'? digit* '.'? digit*
<digit> = #'[0-9]'
bool = 'true' | 'false'
dict = <'{'> (<wsp*> string <':'> <wsp*> value <','?> <wsp*>)* <newline?> <'}'>
class = #'[A-Za-z0-9]+' args
args = <'('> value (<','> <wsp*> (value | kwarg) <wsp*>)* <')'>
kwarg = string <':'> value
global = #'[A-Za-z]+'
 "))

(defn parse-project [content]
  (let [result (insta/parse projects-godot-grammar content
                            #_#_:trace true)]
    (when (insta/failure? result) (println (insta/get-failure result)))
    result))

(comment
  (parse-project example-project-input)
  (parse-project "; Engine configuration file.")
  (project->edn
    (parse-project "config_version=5"))
  (parse-project "[application]")
  (parse-project "config/name=\"Dino\"")
  (parse-project "config/features=PackedStringArray(\"4.1\")")
  (parse-project "environment/default_clear_color=Color(0, 0, 0, 1)")
  (parse-project "ui_accept={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194309,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194309,\"echo\":false,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194310,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194310,\"echo\":false,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":32,\"physical_keycode\":0,\"key_label\":0,\"unicode\":32,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":1,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
"))

(defn project->edn [parsed]
  ;; TODO support parse for single lines too, to ease testing/debugging
  (when-not (insta/failure? parsed)
    (let [parts
          (->> (insta/transform
                 {:section_header (comp keyword str)
                  :key_val        (fn [& args]
                                    {(-> args first str keyword)
                                     (-> args second)})
                  :number         #(edn/read-string (apply str %&))}

                 parsed)
               (partition-by keyword?))
          global (when (some-> parts first first map?)
                   (->> parts first (apply merge)))
          config (cond->> parts
                   global (drop 1)
                   true   (partition 2 2)
                   true   (map (fn [[section conf]]
                                 {(first section) (apply merge conf)}))
                   true   (apply merge))]
      (merge global config))))


(comment
  (into {} ["a"])
  (-> "config_version=5" parse-project project->edn)
  (-> "config_version=5

[application]

config/name=\"Dino\"
config/features=PackedStringArray(\"4.1\")

[rendering]

textures/canvas_textures/default_texture_filter=0
2d/snapping/use_gpu_pixel_snap=true
environment/default_clear_color=Color(0, 0, 0, 1)"
      parse-project project->edn)
  )


(def tscn-grammar (insta/parser "some = 'some'"))

(comment
  (insta/parse projects-godot-grammar example-project-input))

(def tres-grammar
  (insta/parser "some = 'some'"))

(comment
  (insta/parse projects-godot-grammar example-project-input))

(defn parse-godot-file [path]
  (let [path    (if (string? path) (fs/file path) path)
        ext     (fs/extension path)
        content (-> path slurp)]
    #_content
    (if-not (fs/exists? path)
      (println "No file at path:" path)
      (cond
        (#{"godot"} ext) (insta/parse projects-godot-grammar content)
        (#{"tscn"} ext)  (insta/parse tscn-grammar content)
        (#{"tres"} ext)  (insta/parse tres-grammar content)
        :else            (println "Unexpected file extension:" ext)))))

(comment
  (parse-godot-file
    (str (fs/home) "/russmatney/dino/project.godot")))
