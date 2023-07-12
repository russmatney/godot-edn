(ns godot-edn.parse-test
  (:require
   [clojure.test :refer [deftest is testing]]

   [godot-edn.parse :as sut]
   [instaparse.core :as insta]))

(def examples {
               "; Engine configuration file."
               {}

               "config_version=5"
               {}

               "[application]"
               {}
               "config/name=\"Dino\""
               {}
               "config/features=PackedStringArray(\"4.1\")"
               {}
               "environment/default_clear_color=Color(0, 0, 0, 1)"
               {}

               "ui_accept={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194309,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194309,\"echo\":false,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":4194310,\"physical_keycode\":0,\"key_label\":0,\"unicode\":4194310,\"echo\":false,\"script\":null)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0,\"alt_pressed\":false,\"shift_pressed\":false,\"ctrl_pressed\":false,\"meta_pressed\":false,\"pressed\":false,\"keycode\":32,\"physical_keycode\":0,\"key_label\":0,\"unicode\":32,\"echo\":false,\"script\":null)
, Object(InputEventJoypadButton,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":-1,\"button_index\":1,\"pressure\":0.0,\"pressed\":true,\"script\":null)
]
}
"
               {}

               #_#_ "
; Engine configuration file.
; It's best edited using the editor UI and not directly,
; since the parameters that go here are not all obvious.
;

config_version=5

[application]

config/name=\"Dino\"
config/features=PackedStringArray(\"4.1\")

[rendering]

textures/canvas_textures/default_texture_filter=0
2d/snapping/use_gpu_pixel_snap=true
environment/default_clear_color=Color(0, 0, 0, 1)
" {:config-version 5
   :application    {:config/name "Dino"}}}
  )

(comment
  (->> examples keys first
       (insta/parse sut/projects-godot-grammar)))

(deftest project-godot-grammar-test
  (testing "a basic project.godot file can be parsed"
    (doall
      (for [input (keys examples)]
        (let [result (sut/parse-project input)]
          (is result)
          (is (not (insta/failure? result))))))))
