(ns godot-edn.parse-test
  (:require
   [clojure.test :refer [deftest is testing]]

   [godot-edn.parse :as sut]
   [instaparse.core :as insta]))

(def project-godot-examples
  {"; Engine configuration file."
   {:comments ["; Engine configuration file."]}

   "config_version=5"     {:config_version 5}
   "[application]"        {:application {}}
   "config/name=\"Dino\"" {:config/name "Dino"}

   "config/features=PackedStringArray(\"4.1\")"
   {:config/features '(PackedStringArray "4.1")}
   "environment/default_clear_color=Color(0, 0, 0, 1)"
   {:environment/default_clear_color '(Color 0 0 0 1)}

   "2d_physics/layer_1=\"walls, doors, env\"
2d_physics/layer_11=\"fences, low-walls\""
   {:2d_physics/layer_1  "walls, doors, env"
    :2d_physics/layer_11 "fences, low-walls"}

   "ui_accept={\"deadzone\": 0.5,}"
   {:ui_accept {:deadzone 0.5}}

   "ui_accept={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0)
]}"
   {:ui_accept
    {:deadzone 0.5
     :events
     ['(Object InputEventKey
               [:resource_local_to_scene false] [:resource_name nil]
               [:device 0])
      '(Object InputEventKey
               [:resource_local_to_scene false] [:resource_name nil]
               [:device 0] [:window_id 0])]}}

   "[application]

               config/name=\"Dino\"
               run/main_scene.dungeon-crawler=\"res://src/dungeonCrawler/zones/TwoGeon.tscn\""
   {:application
    {:config/name                    "Dino"
     :run/main_scene.dungeon-crawler "res://src/dungeonCrawler/zones/TwoGeon.tscn"}}

   "[autoload]

Debug=\"*res://addons/core/Debug.gd\" "
   ;; TODO autoload order is important... maybe this can't be a map
   {:autoload {;; perhaps this should be a symbol
               :Debug "*res://addons/core/Debug.gd"}}

   "[layer_names]

               key=\"Spaces in strings\""
   {:layer_names {:key "Spaces in strings"}}


   "[display]

window/size/viewport_width=1280
window/size/viewport_height=720
window/stretch/mode.runner=\"viewport\"
window/stretch/aspect.runner=\"keep\"
" {:display {"window/size/viewport_width"   1280
             "window/size/viewport_height"  720
             "window/stretch/mode.runner"   "viewport"
             "window/stretch/aspect.runner" "keep"}}

   "[rendering]

ns_one/ns_two=0
ns_one/ns_two/ns_three=0
ns_one/ns_two/ns_3.four=0
"
   {:rendering
    {:ns_one/ns_two            0
     "ns_one/ns_two/ns_three"  0
     "ns_one/ns_two/ns_3.four" 0
     }}


   "
; Engine configuration file.
; It's best edited using the editor UI and not directly,
; since the parameters that go here are not all obvious.
;

config_version=5

[application]
config/name=\"Dino\"
"
   {:comments
    ["; Engine configuration file."
     "; It's best edited using the editor UI and not directly,"
     "; since the parameters that go here are not all obvious."
     ";"]
    :config_version 5
    :application    {:config/name "Dino"}}

   "
; Engine configuration file.
; It's best edited using the editor UI and not directly,
; since the parameters that go here are not all obvious.
;

config_version=5

[application]

config/name=\"Dino\"
config/features=PackedStringArray(\"4.1\")

[global]

layer=false

[input]

ui_accept={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0)
]
}

[rendering]

textures/canvas_textures/default_texture_filter=0
2d/snapping/use_gpu_pixel_snap=true
environment/default_clear_color=Color(0, 0, 0, 1)
" {:comments       ["; Engine configuration file."
                    "; It's best edited using the editor UI and not directly,"
                    "; since the parameters that go here are not all obvious."
                    ";"]
   :config_version 5
   :application    {:config/name     "Dino"
                    :config/features '(PackedStringArray "4.1")}
   :input          {:ui_accept
                    {:deadzone 0.5
                     :events   ['(Object InputEventKey
                                         [:resource_local_to_scene false] [:resource_name nil]
                                         [:device 0])]}}
   :rendering      {"textures/canvas_textures/default_texture_filter" 0
                    "2d/snapping/use_gpu_pixel_snap"                  true
                    :environment/default_clear_color                  '(Color 0 0 0 1)}
   :global         {:layer false}}})


(deftest project-godot-grammar-test
  (testing "project.godot contents can be parsed"
    (doall
      (for [input (keys project-godot-examples)]
        (let [result (sut/parse-project input)]
          (is result)
          (is (not (insta/failure? result))))))))

(deftest project-godot->edn-test
  (testing "project.godot contents can be converted to edn"
    (doall
      (for [[input expected] project-godot-examples]
        (let [result (sut/project->edn (sut/parse-project input))]
          (is result)
          (is (= expected result)))))))

(def many-inputs-test-data
  "Parsing this structure was causing OOM errors early in development.
  This should parse very quickly!

  Debugging instaparse performance usually means reducing ambiguity in the grammar.
  You can find ambiguities with insta/parses, and 'internal' ambiguities by passing
  `:partial true` and `:unhide all`:

  (insta/parses projects-godot-grammar \"input str\" :partial true :unhide :all)

  see: https://github.com/Engelberg/instaparse/blob/master/docs/Performance.md
  "
  "[input]

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
")

(deftest project.godot-many-inputs-performance-test
  (testing "a large sample input should parse quickly, or at least not OOM and crash"
    (let [res (sut/project->edn (sut/parse-project many-inputs-test-data))]
      (is res)
      ;; should be 33 inputs!
      (is (= 33 (-> res :input keys count))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; export_presets.cfg
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def export-presets-cfg-examples
  {"[preset.0]

   name=\"dino-linux\""
   {:preset.0 {:name "dino-linux"}}

   "ssh_remote_deploy/run_script=\"#!/usr/bin/env bash
export DISPLAY=:0
unzip -o -q \\\"{temp_dir}/{archive_name}\\\" -d \\\"{temp_dir}\\\"
\\\"{temp_dir}/{exe_name}\\\" {cmd_args}\"
ssh_remote_deploy/cleanup_script=\"#!/usr/bin/env bash
kill $(pgrep -x -f \\\"{temp_dir}/{exe_name} {cmd_args}\\\")
rm -rf \\\"{temp_dir}\\\"\""
   {:ssh_remote_deploy/run_script     "#!/usr/bin/env bash
export DISPLAY=:0
unzip -o -q \\\"{temp_dir}/{archive_name}\\\" -d \\\"{temp_dir}\\\"
\\\"{temp_dir}/{exe_name}\\\" {cmd_args}"
    :ssh_remote_deploy/cleanup_script "#!/usr/bin/env bash
kill $(pgrep -x -f \\\"{temp_dir}/{exe_name} {cmd_args}\\\")
rm -rf \\\"{temp_dir}\\\""
    }

   "
[preset.0]

name=\"dino-linux\"
platform=\"Linux/X11\"
runnable=true
dedicated_server=false
custom_features=\"dino\"
export_filter=\"all_resources\"
include_filter=\"\"
exclude_filter=\"\"
export_path=\"dist/dino-linux/dino-linux.x86_64\"
encryption_include_filters=\"\"
encryption_exclude_filters=\"\"
encrypt_pck=false
encrypt_directory=false

[preset.0.options]

custom_template/debug=\"\"
custom_template/release=\"\"
debug/export_console_wrapper=1
binary_format/embed_pck=true
texture_format/bptc=true
texture_format/s3tc=true
texture_format/etc=false
texture_format/etc2=false
binary_format/architecture=\"x86_64\"
ssh_remote_deploy/enabled=false
ssh_remote_deploy/host=\"user@host_ip\"
ssh_remote_deploy/port=\"22\"
ssh_remote_deploy/extra_args_ssh=\"\"
ssh_remote_deploy/extra_args_scp=\"\"
ssh_remote_deploy/run_script=\"#!/usr/bin/env bash
export DISPLAY=:0
unzip -o -q \\\"{temp_dir}/{archive_name}\\\" -d \\\"{temp_dir}\\\"
\\\"{temp_dir}/{exe_name}\\\" {cmd_args}\"
ssh_remote_deploy/cleanup_script=\"#!/usr/bin/env bash
kill $(pgrep -x -f \\\"{temp_dir}/{exe_name} {cmd_args}\\\")
rm -rf \\\"{temp_dir}\\\"\" "
   {:preset.0
    {:export_filter              "all_resources",
     :encryption_include_filters nil,
     :name                       "dino-linux",
     :encryption_exclude_filters nil,
     :custom_features            "dino",
     :encrypt_pck                false,
     :export_path                "dist/dino-linux/dino-linux.x86_64",
     :runnable                   true,
     :exclude_filter             nil,
     :include_filter             nil,
     :encrypt_directory          false,
     :dedicated_server           false,
     :platform                   "Linux/X11"},
    :preset.0.options
    {:texture_format/etc               false,
     :binary_format/embed_pck          true,
     :custom_template/release          nil,
     :ssh_remote_deploy/extra_args_ssh nil,
     :custom_template/debug            nil,
     :texture_format/bptc              true,
     :ssh_remote_deploy/port           "22",
     :debug/export_console_wrapper     1,
     :texture_format/etc2              false,
     :ssh_remote_deploy/cleanup_script
     "#!/usr/bin/env bash
kill $(pgrep -x -f \\\"{temp_dir}/{exe_name} {cmd_args}\\\")
rm -rf \\\"{temp_dir}\\\"",
     :ssh_remote_deploy/extra_args_scp nil,
     :ssh_remote_deploy/enabled        false,
     :ssh_remote_deploy/run_script
     "#!/usr/bin/env bash
export DISPLAY=:0
unzip -o -q \\\"{temp_dir}/{archive_name}\\\" -d \\\"{temp_dir}\\\"
\\\"{temp_dir}/{exe_name}\\\" {cmd_args}",
     :ssh_remote_deploy/host           "user@host_ip",
     :texture_format/s3tc              true,
     :binary_format/architecture       "x86_64"}}})

(deftest export-presets-cfg-grammar-test
  (testing "export_presets.cfg contents can be parsed"
    (doall
      (for [input (keys export-presets-cfg-examples)]
        (let [result (sut/parse-project input)]
          (is result)
          (is (not (insta/failure? result))))))))

(deftest export-preset->edn-test
  (testing "export_presets.cfg contents can be converted to edn"
    (doall
      (for [[input expected] export-presets-cfg-examples]
        (let [result (sut/project->edn (sut/parse-project input))]
          (is result)
          (is (= expected result)))))))
