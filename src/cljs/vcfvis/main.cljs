(ns vcfvis.main
  (:use-macros [c2.util :only [pp p timeout]]
               [reflex.macros :only [constrain!]])
  (:require [vcfvis.core :as core]
            [vcfvis.data :as data]
            [vcfvis.controls :as controls]))

(constrain!
 (let [res (doall (map data/load @controls/file-selector))]
   ;;Need timeout here so that computed-observables that depend on !vcfs don't get picked up as dependencies for this form (causing an infinite loop). TODO: nice general solution for this problem.
   (timeout 10 (reset! core/!vcfs res))))

(reset! core/!available-files (data/available-files))







;;(constrain! (pp @core/!shared-metrics))




