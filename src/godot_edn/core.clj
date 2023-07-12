(ns godot-edn.core
  (:require [instaparse.core :as insta]))



(comment
  :sup
  )

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

[physics]

common/enable_pause_aware_picking=true

[rendering]

textures/canvas_textures/default_texture_filter=0
2d/snapping/use_gpu_pixel_snap=true
environment/default_clear_color=Color(0, 0, 0, 1)
environment/default_environment=\"res://default_env.tres\"
textures/vram_compression/import_s3tc_bptc.macos=true
")

(def projects-godot
  (insta/parser "
project_config = <whitespace?>  <comment?> key_vals (<whitespace+> section)*
<comment> = ';' <whitespace*> #'.*'
<whitespace> = #'\\s+'
section = section_header <whitespace+> key_vals
section_header = '[' word ']'
<word> = #'[A-Za-z0-9/_]'+
key_vals = key '=' value
key = word
value = #'.*$'
 "))

(comment
  (insta/parse projects-godot example-project-input)
  )
