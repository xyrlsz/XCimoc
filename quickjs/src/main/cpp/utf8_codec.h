#ifndef UTF8_CODEC_H
#define UTF8_CODEC_H

#include <jni.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Convert UTF-8 to UTF-16.
 *
 * @param src      UTF-8 input buffer
 * @param src_len  input length in bytes
 * @param dst      output UTF-16 buffer, or NULL to calculate length
 *
 * @return number of UTF-16 code units required/written
 *
 * Invalid UTF-8 sequences are replaced with U+FFFD.
 */
size_t utf8_to_utf16(const char *src,
                     size_t src_len,
                     jchar *dst);

/**
 * Convert a Java String to a standard UTF-8 buffer.
 *
 * @param env      JNI environment
 * @param str      Java string
 * @param out_len  optional output length in bytes, not including NUL terminator
 *
 * @return newly allocated UTF-8 buffer, caller must free() it
 */
char *utf16_to_utf8(JNIEnv *env,
                    jstring str,
                    size_t *out_len);

#ifdef __cplusplus
}
#endif

#endif /* UTF8_CODEC_H */