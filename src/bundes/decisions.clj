(ns bundes.decisions
  "Comparison function between start state and end state which
   produces a list of side-effects that should be carried out."
  (:require [clojure.data       :refer [diff]]
            [clojure.set        :refer [intersection difference]]
            [clojure.core.match :refer [match]]))

(defn changed-map
  "Compare two versions of a single unit and return
   all resulting side-effects."
  [old new k]
  (let [old                 (get old k {:status :stop})
        new                 (get new k {:status :stop})]
    (match [(:type old) (:type new)
            (= (:runtime old) (:runtime new))
            (= (:schedule old) (:schedule new))
            (:status old) (:status new)]

           ;; Basic status changes
           [_ _ _ _       :stop :stop]  []
           [:daemon _ _ _ :start :stop] [{:action :stop      :unit   old}]
           [:batch  _ _ _ :start :stop] [{:action :sched-del :unit   old}]

           [_ :daemon _ _ :stop :start] [{:action :start     :unit   new}]
           [_ :batch  _ _ :stop :start] [{:action :sched-add :unit   new}]

           ;; If we got this far, both units are in status :start

           ;; Unit switches from batch to daemon
           [:batch :daemon _ _ _ _]     [{:action :sched-del :unit   old}
                                         {:action :start     :unit   new}]

           ;; Unit switches from batch to daemon
           [:daemon :batch _ _ _ _]     [{:action :stop      :unit   new}
                                         {:action :sched-add :unit   new}]

           ;; Unit runtime changed
           [_ :daemon false _ _ _]      [{:action :stop      :unit   old}
                                         {:action :start     :unit   new}]

           ;; Unit schedules changed
           [_ :batch _ false _ _]       [{:action :sched-del :unit   old}
                                         {:action :sched-add :unit   new}]

           ;; We really should not get this far
           [_ _ _ _ _ _]                (throw (ex-info "unhandled case"
                                                        {:id [old new]})))))

(defn add-map
  "If a new unit is in start state, return
   resulting side-effect."
  [new k]
  (let [val (get new k {})]
    (when (= :start (:status val))
      (if (= :batch (:type val))
        {:action :sched-add :units [val]}
        {:action :start     :units [val]}))))

(defn del-map
  "If an old unit was in start state, return
   resulting side-effect."
  [old k]
  (let [val (get old k {})]
    (when (= :start (:status val))
      (if (= :batch (:type val))
        {:action :sched-del :units [val]}
        {:action :stop      :units [val]}))))

(defn group-effects
  "Group similar effects together"
  [[action effects]]
  {:action action
   :units  (map :unit effects)})

(defn decisions
  "Given two versions of the world, return all resulting
   side-effects when transitioning from the previous to
   the next version."
  [old new]
  (let [[before after _] (diff old new)
        after            (set (keys after))
        before           (set (keys before))
        changed          (intersection before after)
        added            (difference after changed)
        removed          (difference before changed)]
    (->> (concat
          (mapcat (partial changed-map old new) changed)
          (map (partial add-map new) added)
          (map (partial del-map old) removed))
         (remove nil?)
         (vec)
         (group-by :action)
         (map group-effects))))
