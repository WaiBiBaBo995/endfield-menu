# Packed alpha experiment — rejected for release

`packed-alpha.js` is a research adapter, not the active menu. It clones the DOM
into a second monochrome plane and captures color and alpha in one double-height
browser image. `packed-alpha.fsh` reconstructs the two planes.

The static comparison was close (mean alpha error 0.0000053 / 255), but the
entrance/exit animations did not match: mean error reached 8.73 / 255 and 13.58%
of pixels differed by more than 8. Browser throughput was approximately 39–43
presented frames per second in the prototype. This does not meet release quality.

The release adapter does not create the clone, register its MutationObserver, or
offer its config flag. `tools/qa/page-planes.mjs` explicitly serves this archived
adapter. Run it after installing `tools/native-export` npm dependencies. Results
are recorded in `packed-alpha-results.json`.

The independent DLL in the release is an image decoder only. AUI's exposed API
does not provide a browser controller or GPU shared-texture handle, so this
experiment did not establish a GPU-sharing/zero-copy rendering path.
