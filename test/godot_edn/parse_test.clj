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

               "ui_accept={
\"deadzone\": 0.5,
\"events\": [Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0)
, Object(InputEventKey,\"resource_local_to_scene\":false,\"resource_name\":\"\",\"device\":0,\"window_id\":0)
]
}
"
               {:ui_accept
                {:deadzone 0.5
                 :events
                 ['(Object InputEventKey
                           [:resouce_local_to_scene false] [:resource_name ""]
                           [:device 0])
                  '(Object InputEventKey
                           [:resouce_local_to_scene false] [:resource_name ""]
                           [:device 0] [:window_id 0])]}}

               "[rendering]

textures/canvas_textures/default_texture_filter=0"
               {:rendering
                {:textures.canvas_textures/default_texture_filter 0}}

               "
; Engine configuration file.
; It's best edited using the editor UI and not directly,
; since the parameters that go here are not all obvious.
;

config_version=5

[application]

config/name=\"Dino\"
config/features=PackedStringArray(\"4.1\")

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
" {:config_version 5
   :application
   {:config/name     "Dino"
    :config/features '(PackedStringArray "4.1")}
   :input
   {:ui_accept
    {:deadzone 0.5
     :events   ['(Object InputEventKey
                         [:resouce_local_to_scene false] [:resource_name ""]
                         [:device 0])]}}
   :rendering
   {:textures.canvas_textures/default_texture_filter 0
    :2d.snapping/use_gpu_pixel_snap                  true
    :environment/default_clear_color                 '(Color 0 0 0 1)}}})



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
