/* Endfield Menu PNG decoder. No browser or AUI binary is loaded or modified. */
#include <stdint.h>
#include <stddef.h>
#include "spng.h"
#include "turbojpeg.h"
__declspec(dllexport) int ef_decode_jpeg(const void *input, int length, void *rgba, int capacity, int width, int height) {
  if (!input || !rgba || length <= 0 || width <= 0 || height <= 0 || width > 8192 || height > 8192 || (int64_t)width * height * 4 > capacity) return -1;
  tjhandle ctx = tj3Init(TJINIT_DECOMPRESS);
  if (!ctx) return -2;
  int result = tj3DecompressHeader(ctx, input, (size_t)length);
  if (!result && (tj3Get(ctx, TJPARAM_JPEGWIDTH) != width || tj3Get(ctx, TJPARAM_JPEGHEIGHT) != height)) result = -3;
  if (!result) result = tj3Decompress8(ctx, input, (size_t)length, rgba, width * 4, TJPF_RGBA);
  tj3Destroy(ctx);
  return result;
}
__declspec(dllexport) int ef_decode_png(const void *input, int length, void *rgba, int capacity, int width, int height) {
  if (!input || !rgba || length <= 0 || width <= 0 || height <= 0 || (int64_t)width * height * 4 > capacity) return -1;
  spng_ctx *ctx = spng_ctx_new(0);
  if (!ctx) return -2;
  spng_set_image_limits(ctx, 8192, 8192);
  spng_set_chunk_limits(ctx, 16 * 1024 * 1024, 32 * 1024 * 1024);
  int result = spng_set_png_buffer(ctx, input, (size_t)length);
  struct spng_ihdr header;
  if (!result) result = spng_get_ihdr(ctx, &header);
  if (!result && (header.width != (uint32_t)width || header.height != (uint32_t)height)) result = -3;
  if (!result) result = spng_decode_image(ctx, rgba, (size_t)capacity, SPNG_FMT_RGBA8, 0);
  spng_ctx_free(ctx);
  return result;
}
