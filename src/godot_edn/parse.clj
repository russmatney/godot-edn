(ns godot-edn.parse
  (:require
   [babashka.fs :as fs]
   [instaparse.core :as insta]
   [clojure.edn :as edn]
   [clojure.string :as string]
   ))

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
<string> = <'\"'> chars <'\"'>
<chars> = #'[A-Za-z0-9_.:/ *]*'
list = <'['> (<ows> value <ows> <','?>)* <']'>
number = '-'? digits '.'? digits?
<digits> = #'[0-9]+'
bool = 'true' | 'false'
dict = <'{'> (<ows> string <':'> <rws> value <ows> <','?>)* <'}'>
class = #'[A-Za-z0-9]+' args
<args> = <'('> value (<','> <ows> (value | kwarg) <ows>)* <')'>
kwarg = string <':'> value
global = #'[A-Za-z]+'
 "))

(comment
  (->>
    "
[input]

ui_accept={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194309,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194309,\"echo\":false,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194310,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194310,\"echo\":false,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":32,\"physical_keycode\":0,\"key_label\":0,\"unicode\":32,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":1,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
move_up={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":87,\"physical_keycode\":0,\"key_label\":0,\"unicode\":119,\"echo\":false,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":1,\"axis_value\":-1.0,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":11,\"pressure\":0.0,\"pressed\":true,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":0,\"physical_keycode\":4194320,\"key_label\":0,\"unicode\":0,\"echo\":false,\"script\":null)
]
}
"
    ;; parse-project project->edn

    (#(string/replace % "\n\n" "\n"))
    (#(insta/parses projects-godot-grammar %
                    :partial true
                    :unhide :all
                    ))
    #_(map (fn [parse]
             (-> parse project->edn))))

  (->>
    "[header]

x={
\"y\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0)]
} "
    (#(insta/parses projects-godot-grammar %
                    :partial true
                    :unhide :all
                    )))

  (-> "
[editor_plugins]

enabled=PackedStringArray(\"res://addons/AsepriteWizard/plugin.cfg\", \"res://addons/BulletUpHell/plugin.cfg\", \"res://addons/core/plugin.cfg\", \"res://addons/beehive/plugin.cfg\", \"res://addons/camera/plugin.cfg\", \"res://addons/dj/plugin.cfg\", \"res://addons/gdfxr/plugin.cfg\", \"res://addons/hood/plugin.cfg\", \"res://addons/hotel/plugin.cfg\", \"res://addons/metro/plugin.cfg\", \"res://addons/navi/plugin.cfg\", \"res://addons/quest/plugin.cfg\", \"res://addons/reptile/plugin.cfg\", \"res://addons/thanks/plugin.cfg\", \"res://addons/trolley/plugin.cfg\")
"
      #_(#(insta/parses projects-godot-grammar %
                        :partial true
                        :unhide :all
                        ))
      parse-project project->edn)
  )

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
  (def proj-content
    (slurp
      (str (fs/home) "/russmatney/dino/project.godot")))

  (-> "; Some comment
; another comment" parse-project project->edn)


  (project->edn parsed-inputs)
  (def parsed-inputs (->
                       "
[input]

ui_accept={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194309,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194309,\"echo\":false,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194310,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194310,\"echo\":false,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":32,\"physical_keycode\":0,\"key_label\":0,\"unicode\":32,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":1,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
ui_select={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":32,\"physical_keycode\":0,\"key_label\":0,\"unicode\":32,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":1,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
ui_cancel={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194305,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194305,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":0,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
ui_focus_next={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194306,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194306,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":10,\"pressure\":0.0,\"pressed\":true,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":3,\"axis_value\":1.0,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":2,\"axis_value\":1.0,\"script\":null)
]
}
ui_focus_prev={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":true,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194306,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194306,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":9,\"pressure\":0.0,\"pressed\":true,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":3,\"axis_value\":-1.0,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":2,\"axis_value\":-1.0,\"script\":null)
]
}
ui_left={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194319,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194319,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"button_index\":13,\"pressure\":0.0,\"pressed\":false,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":0,\"axis_value\":-1.0,\"script\":null)
]
}
ui_right={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194321,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194321,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"button_index\":14,\"pressure\":0.0,\"pressed\":false,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":0,\"axis_value\":1.0,\"script\":null)
]
}
ui_up={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194320,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194320,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"button_index\":11,\"pressure\":0.0,\"pressed\":false,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":1,\"axis_value\":-1.0,\"script\":null)
]
}
ui_down={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194322,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194322,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"button_index\":12,\"pressure\":0.0,\"pressed\":false,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":1,\"axis_value\":1.0,\"script\":null)
]
}
ui_page_up={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194323,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194323,\"echo\":false,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":3,\"axis_value\":-1.0,\"script\":null)
]
}
ui_page_down={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194324,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194324,\"echo\":false,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":3,\"axis_value\":1.0,\"script\":null)
]
}
ui_menu={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194370,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194370,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":4,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
move_up={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":87,\"physical_keycode\":0,\"key_label\":0,\"unicode\":119,\"echo\":false,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":1,\"axis_value\":-1.0,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":11,\"pressure\":0.0,\"pressed\":true,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":0,\"physical_keycode\":4194320,\"key_label\":0,\"unicode\":0,\"echo\":false,\"script\":null)
]
}
move_down={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":83,\"physical_keycode\":0,\"key_label\":0,\"unicode\":115,\"echo\":false,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":1,\"axis_value\":1.0,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":12,\"pressure\":0.0,\"pressed\":true,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":0,\"physical_keycode\":4194322,\"key_label\":0,\"unicode\":0,\"echo\":false,\"script\":null)
]
}
move_left={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":65,\"physical_keycode\":0,\"key_label\":0,\"unicode\":97,\"echo\":false,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":0,\"axis_value\":-1.0,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":13,\"pressure\":0.0,\"pressed\":true,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":0,\"physical_keycode\":4194319,\"key_label\":0,\"unicode\":0,\"echo\":false,\"script\":null)
]
}
move_right={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":68,\"physical_keycode\":0,\"key_label\":0,\"unicode\":100,\"echo\":false,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":0,\"axis_value\":1.0,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":14,\"pressure\":0.0,\"pressed\":true,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":0,\"physical_keycode\":4194321,\"key_label\":0,\"unicode\":0,\"echo\":false,\"script\":null)
]
}
pause={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194305,\"physical_keycode\":0,\"key_label\":0,\"unicode\":0,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":6,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
attack={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":77,\"physical_keycode\":0,\"key_label\":0,\"unicode\":109,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":2,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
action={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":69,\"physical_keycode\":0,\"key_label\":0,\"unicode\":101,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":3,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
jump={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":78,\"physical_keycode\":0,\"key_label\":0,\"unicode\":110,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":0,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
fire={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":77,\"physical_keycode\":0,\"key_label\":0,\"unicode\":109,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":2,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
restart={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":true,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":82,\"physical_keycode\":0,\"key_label\":0,\"unicode\":82,\"echo\":false,\"script\":null)
]
}
respawns={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":82,\"physical_keycode\":0,\"key_label\":0,\"unicode\":114,\"echo\":false,\"script\":null)
]
}
zoom_in={
\"deadzone\": 0.5,
\"events\": [Object(InputEventMouseButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"button_mask\":0,\"position\":Vector2(0, 0),\"global_position\":Vector2(0, 0),\"factor\":1.0,\"button_index\":4,\"canceled\":false,\"pressed\":false,\"double_click\":false,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194320,\"physical_keycode\":0,\"key_label\":0,\"unicode\":0,\"echo\":false,\"script\":null)
]
}
zoom_out={
\"deadzone\": 0.5,
\"events\": [Object(InputEventMouseButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"button_mask\":0,\"position\":Vector2(0, 0),\"global_position\":Vector2(0, 0),\"factor\":1.0,\"button_index\":5,\"canceled\":false,\"pressed\":false,\"double_click\":false,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194322,\"physical_keycode\":0,\"key_label\":0,\"unicode\":0,\"echo\":false,\"script\":null)
]
}
jetpack={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":74,\"physical_keycode\":0,\"key_label\":0,\"unicode\":106,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":1,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
slowmo={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194325,\"physical_keycode\":0,\"key_label\":0,\"unicode\":0,\"echo\":false,\"script\":null)
, Object(InputEventJoypadMotion,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"axis\":5,\"axis_value\":1.0,\"script\":null)
]
}
debug_toggle={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":80,\"physical_keycode\":0,\"key_label\":0,\"unicode\":112,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":4,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
close={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":81,\"physical_keycode\":0,\"key_label\":0,\"unicode\":113,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":0,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
cycle_next_action={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":0,\"physical_keycode\":4194306,\"key_label\":0,\"unicode\":0,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":10,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
cycle_previous_action={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":true,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":0,\"physical_keycode\":4194306,\"key_label\":0,\"unicode\":0,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":9,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
dash={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":0,\"physical_keycode\":32,\"key_label\":0,\"unicode\":32,\"echo\":false,\"script\":null)
]
}
cycle_weapon={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":0,\"physical_keycode\":96,\"key_label\":0,\"unicode\":96,\"echo\":false,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":0,\"physical_keycode\":72,\"key_label\":0,\"unicode\":104,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":1,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
"
                       (#(insta/parses projects-godot-grammar %
                                       :optimize :memory
                                       ))
                       ;; parse-project project->edn
                       )
    )

  :test
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
