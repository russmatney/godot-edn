(ns godot-edn.parse-test
  (:require
   [clojure.test :refer [deftest is testing]]

   [godot-edn.parse :as sut]
   [instaparse.core :as insta]))

(def examples {"; Engine configuration file."
               {:comments ["; Engine configuration file."]}

               "config_version=5"     {:config_version 5}
               "[application]"        {:application {}}
               "config/name=\"Dino\"" {:config/name "Dino"}

               "config/features=PackedStringArray(\"4.1\")"
               {:config/features '(PackedStringArray "4.1")}
               "environment/default_clear_color=Color(0, 0, 0, 1)"
               {:environment/default_clear_color '(Color 0 0 0 1)}

               "ui_accept={\"deadzone\": 0.5,} "
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
                           [:resource_local_to_scene false] [:resource_name ""]
                           [:device 0])
                  '(Object InputEventKey
                           [:resource_local_to_scene false] [:resource_name ""]
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
" {
   :comments
   ["; Engine configuration file."
    "; It's best edited using the editor UI and not directly,"
    "; since the parameters that go here are not all obvious."
    ";"]
   :config_version 5
   :application
   {:config/name     "Dino"
    :config/features '(PackedStringArray "4.1")}
   :input
   {:ui_accept
    {:deadzone 0.5
     :events   ['(Object InputEventKey
                         [:resource_local_to_scene false] [:resource_name ""]
                         [:device 0])]}}
   :rendering
   {"textures/canvas_textures/default_texture_filter" 0
    "2d/snapping/use_gpu_pixel_snap"                  true
    :environment/default_clear_color                  '(Color 0 0 0 1)}
   :global         {:layer false}}})


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

(deftest project-godot->edn-test
  (testing "a project.godot can be converted to edn"
    (doall
      (for [[input expected] examples]
        (let [result (sut/project->edn (sut/parse-project input))]
          (is result)
          (is (= expected result)))))))
