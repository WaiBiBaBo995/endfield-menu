# Independent image decoder

Windows x64 helper for bounded RGBA PNG/JPEG decode. AUI owns the browser and
input; this helper only decodes complete compressed images into caller-owned
memory. No browser handle injection or AUI binary patching is involved.

Pinned upstream sources and archive SHA256 files are included. Build with MSVC,
CMake >=3.20 and NASM >=2.16 on PATH:

```powershell
tools/native-codec/build.cmd
Copy-Item tools/native-codec/build/endfield_codec.dll src/main/resources/natives/windows-x86_64/endfield_codec.dll
```

`build.cmd` locates the development machine's VS18 Build Tools. Change its
vcvars/CMake paths for another installation. CMake builds zlib-ng statically and
libjpeg-turbo via ExternalProject with runtime CPU-dispatched x64 SIMD. The C
runtime is linked statically. The DLL has no dependency on an installed codec
pack. See the original LICENSE/README.ijg files in vendor/ for license terms.

Both exports reject null pointers, mismatched dimensions and insufficient output
capacity. Input and output must be valid caller-owned native memory. The PNG
decoder also bounds chunk sizes and image dimensions. Java supplies complete
bounded images and has a platform decoder fallback when loading is unavailable.
