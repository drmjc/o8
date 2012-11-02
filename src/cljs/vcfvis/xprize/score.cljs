;;Interactive functionality for X prize scoring based web pages.

(ns vcfvis.xprize.score
  (:require [clojure.string :as string]
            [chosen.core :as chosen]
            [crate.core :as crate]
            [domina :as domina]
            [shoreleave.remotes.http-rpc :as rpc]
            [goog.string :as gstring]
            [goog.Timer :as timer])
  (:require-macros [shoreleave.remotes.macros :as sl]))

;; ## Display scoring results

(defn- progress-percent
  "Rough progress points to indicate status of processing."
  [desc]
  (cond
   (gstring/startsWith desc "Starting variation") 10
   (gstring/startsWith desc "Prepare VCF, resorting to genome build: contestant") 15
   (gstring/startsWith desc "Normalize MNP and indel variants: contestant") 60
   (gstring/startsWith desc "Comparing VCFs: reference vs contestant") 75
   (gstring/startsWith desc "Summarize comparisons") 90
   (gstring/startsWith desc "Finished") 100
   :else nil))

(defn ^:export update-run-status
  "Update summary page with details about running statuses."
  [run-id]
  (sl/rpc (get-status run-id) [info]
          (if (= :finished (:state info))
            (sl/rpc (get-summary run-id) [sum-html]
                    (if (nil? sum-html)
                      (timer/callOnce (fn [] (update-run-status run-id)) 2000)
                      (domina/set-html! (domina/by-id "scoring-in-process")
                                        sum-html)))
            (do
              (when-not (nil? info)
                (domina/set-html! (domina/by-id "scoring-status")
                                  (crate/html [:p (:desc info)]))
                (when-let [pct (progress-percent (:desc info))]
                  (domina/set-attr! (domina/by-id "scoring-progress")
                                    :style (str "width: " pct "%"))))
              (timer/callOnce (fn [] (update-run-status run-id)) 2000)))))

;; ## Retrieve remote file information

(defn- gs-paths-to-chosen [xs]
  (map (fn [x] {:value (:full x) :text (:name x)}) xs))

(defn- update-gs-files!
  "Update file information based on parent"
  [file-chosen file-id dir ftype]
  (let [final-form-id (string/join "-" (cons "gs" (rest (string/split file-id #"-"))))]
    (sl/rpc ("variant/external-files" dir ftype) [files]
            (chosen/options file-chosen (gs-paths-to-chosen files))
            (domina/set-value! (domina/by-id final-form-id) (chosen/selected file-chosen))
            (add-watch file-chosen :change
                       (fn [fname]
                         (domina/set-value! (domina/by-id final-form-id) fname))))))

(defn prep-remote-selectors
  "Prepare dropdowns for retrieval via GenomeSpace or Galaxy."
  [select-id ftype]
  (let [folder-id (str select-id "-folder")
        file-id (str select-id "-file")]
    (let [folder-chosen (chosen/ichooseu! (str "#" folder-id))
          file-chosen (chosen/ichooseu! (str "#" file-id))]
      (sl/rpc ("variant/external-dirs") [dirs]
              (chosen/options folder-chosen (gs-paths-to-chosen dirs))
              (when-let [cur-dir (chosen/selected folder-chosen)]
                (update-gs-files! file-chosen file-id cur-dir ftype))
              (add-watch folder-chosen :change
                         (fn [dir]
                           (update-gs-files! file-chosen file-id dir ftype)))))))

(defn- prep-genome-selector
  "Prepare genome selector to pick analysis genome."
  []
  (let [genome-chosen (chosen/ichooseu! "#comparison-genome")]
    (sl/rpc ("meta/genomes") [genomes]
            (chosen/options genome-chosen genomes))))

(defn ^:export set-navigation
  "Correctly set the active top level navigation toolbar."
  []
  (let [loc (-> (.toString window.location ())
                (string/split #"/")
                last)]
    (doseq [list-item (domina/children (domina/by-id "top-navbar"))]
      (if (= (str "/" loc)
             (-> (domina/children list-item)
                 first
                 (domina/attr :href)))
        (domina/set-attr! list-item :class "active")
        (domina/remove-attr! list-item :class)))))

(defn ^:export setup-remotes 
  "Setup retrieval of file information from GenomeSpace and Galaxy."
  []
  (prep-genome-selector)
  (prep-remote-selectors "variant" "vcf")
  (prep-remote-selectors "region" "bed"))
