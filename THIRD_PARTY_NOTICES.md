# Third-party notices

## endfield_menu-main (user-supplied archive)

This mod directly runs the static fan recreation supplied as
`endfield_menu-main.zip` inside ApricityUI's built-in WebView. Its original HTML
and all 20 component JavaScript files are packaged byte-for-byte unchanged. The
Minecraft bridge is injected separately when the local page is served. The original
files and README are also preserved in
`reference/endfield_menu-main/`. The original code is licensed under
GNU GPL version 3 or later; this derivative is distributed under the same license.
See LICENSE. The archive did not provide an independently verified author or source
repository URL, so none is invented here.

The original README excludes `components/button-art.js`,
`components/backdrop-field.js`, and `components/card-contours.js` from its GPL grant:
they contain artwork redrawn, sampled, or traced from Arknights: Endfield,
copyright Hypergryph. These three original files are included to preserve the
user-requested webpage. The GPL license applies to the code covered by the
original grant; it does not relicense that artwork. The central map is newly
generated from real Minecraft terrain; the decorative card contours remain the
original supplied artwork.
This is an unofficial fan project, unaffiliated with Hypergryph or Gryphline.

## ApricityUI

ApricityUI by Tower-of-Sighs is a required, separately installed dependency.
https://github.com/Tower-of-Sighs/AUI
https://modrinth.com/mod/apricityui
It is not bundled in the distributable mod JAR.

## Independent image decoder (Windows x64)

`endfield_codec.dll` is built from this project's bounded wrapper and the following
unmodified upstream sources. It does not patch or replace ApricityUI binaries.

- libspng 0.7.4 (BSD-2-Clause and libpng-2.0), https://github.com/randy408/libspng
- zlib-ng 2.2.4 (zlib), https://github.com/zlib-ng/zlib-ng
- libjpeg-turbo 3.1.2 (IJG, BSD-3-Clause and zlib), https://github.com/libjpeg-turbo/libjpeg-turbo

This software is based in part on the work of the Independent JPEG Group.
License texts are included under `licenses/` in the JAR. Complete vendored
sources, pinned archive hashes and build instructions are in `tools/native-codec/`.

## NeoForge MDK

Gradle wrapper and build scaffold originate from the NeoForge 1.21.1 ModDevGradle
MDK at commit f7002b1b6011723c7b12fde95147689491a6ddab.
https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle
