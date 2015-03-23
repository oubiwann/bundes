(ns bundes.effect
  "Placeholder protocol for perform-effect.")

(defmulti perform-effect
  "Open protocol for perform-effect.
   Expects maps and dispatches on :action key."
  :action)
