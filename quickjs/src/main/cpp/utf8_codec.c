//
// Created by xyrlsz on 2026/8/2.
//

#include "utf8_codec.h"

#include <stdint.h>
#include <stddef.h>
#include <stdlib.h>

#define UNI_REPLACEMENT_CHAR 0xFFFDu

size_t utf8_to_utf16(const char *src,
                     size_t src_len,
                     jchar *dst) {
    if (src == NULL || src_len == 0) {
        return 0;
    }

    const unsigned char *s = (const unsigned char *) src;
    const unsigned char *end = s + src_len;
    size_t out = 0;

    while (s < end) {
        unsigned char c = *s;

        if (c >= 0x80u) {
            break;
        }

        if (dst != NULL) {
            dst[out] = (jchar) c;
        }
        ++out;
        ++s;
    }

    while (s < end) {
        uint32_t cp;
        unsigned char c = *s++;

        if (c < 0x80u) {
            cp = c;
        } else if ((c & 0xE0u) == 0xC0u) {
            if (s < end && (s[0] & 0xC0u) == 0x80u) {
                cp = ((uint32_t) (c & 0x1Fu) << 6) |
                     (uint32_t) (s[0] & 0x3Fu);
                if (cp < 0x80u) {
                    cp = UNI_REPLACEMENT_CHAR;
                } else {
                    ++s;
                }
            } else {
                cp = UNI_REPLACEMENT_CHAR;
            }
        } else if ((c & 0xF0u) == 0xE0u) {
            if ((size_t) (end - s) >= 2 &&
                (s[0] & 0xC0u) == 0x80u &&
                (s[1] & 0xC0u) == 0x80u) {
                cp = ((uint32_t) (c & 0x0Fu) << 12) |
                     ((uint32_t) (s[0] & 0x3Fu) << 6) |
                     (uint32_t) (s[1] & 0x3Fu);
                if (cp < 0x800u || (cp >= 0xD800u && cp <= 0xDFFFu)) {
                    cp = UNI_REPLACEMENT_CHAR;
                } else {
                    s += 2;
                }
            } else {
                cp = UNI_REPLACEMENT_CHAR;
            }
        } else if ((c & 0xF8u) == 0xF0u) {
            if ((size_t) (end - s) >= 3 &&
                (s[0] & 0xC0u) == 0x80u &&
                (s[1] & 0xC0u) == 0x80u &&
                (s[2] & 0xC0u) == 0x80u) {
                cp = ((uint32_t) (c & 0x07u) << 18) |
                     ((uint32_t) (s[0] & 0x3Fu) << 12) |
                     ((uint32_t) (s[1] & 0x3Fu) << 6) |
                     (uint32_t) (s[2] & 0x3Fu);
                if (cp < 0x10000u || cp > 0x10FFFFu) {
                    cp = UNI_REPLACEMENT_CHAR;
                } else {
                    s += 3;
                }
            } else {
                cp = UNI_REPLACEMENT_CHAR;
            }
        } else {
            cp = UNI_REPLACEMENT_CHAR;
        }

        if (cp <= 0xFFFFu) {
            if (dst != NULL) {
                dst[out] = (jchar) cp;
            }
            ++out;
        } else {
            cp -= 0x10000u;
            if (dst != NULL) {
                dst[out] = (jchar) (0xD800u + (cp >> 10));
                dst[out + 1] = (jchar) (0xDC00u + (cp & 0x3FFu));
            }
            out += 2;
        }
    }

    return out;
}

char *utf16_to_utf8(JNIEnv *env,
                    jstring str,
                    size_t *out_len) {
    char *out = NULL;
    size_t len = 0;
    jsize utf16_len;
    const jchar *utf16;

    if (out_len) {
        *out_len = 0;
    }
    if (!str) {
        return NULL;
    }

    utf16_len = (*env)->GetStringLength(env, str);
    if (utf16_len <= 0) {
        out = (char *) malloc(1);
        if (out) {
            out[0] = '\0';
            if (out_len) {
                *out_len = 0;
            }
        }
        return out;
    }

    utf16 = (*env)->GetStringChars(env, str, NULL);
    if (!utf16) {
        return NULL;
    }

    out = (char *) malloc((size_t) utf16_len * 4 + 1);
    if (out) {
        size_t used = 0;
        for (jsize i = 0; i < utf16_len; ++i) {
            uint32_t cp;
            uint16_t ch = (uint16_t) utf16[i];

            if (ch >= 0xD800u && ch <= 0xDBFFu) {
                uint16_t low;
                if (i + 1 < utf16_len) {
                    low = (uint16_t) utf16[i + 1];
                    if (low >= 0xDC00u && low <= 0xDFFFu) {
                        cp = ((uint32_t) (ch - 0xD800u) << 10) |
                             ((uint32_t) (low - 0xDC00u)) | 0x10000u;
                        ++i;
                    } else {
                        cp = 0xFFFDu;
                    }
                } else {
                    cp = 0xFFFDu;
                }
            } else if (ch >= 0xDC00u && ch <= 0xDFFFu) {
                cp = 0xFFFDu;
            } else {
                cp = (uint32_t) ch;
            }

            if (cp <= 0x7Fu) {
                out[used++] = (char) cp;
            } else if (cp <= 0x7FFu) {
                out[used++] = (char) (0xC0u | (cp >> 6));
                out[used++] = (char) (0x80u | (cp & 0x3Fu));
            } else if (cp <= 0xFFFFu) {
                out[used++] = (char) (0xE0u | (cp >> 12));
                out[used++] = (char) (0x80u | ((cp >> 6) & 0x3Fu));
                out[used++] = (char) (0x80u | (cp & 0x3Fu));
            } else {
                out[used++] = (char) (0xF0u | (cp >> 18));
                out[used++] = (char) (0x80u | ((cp >> 12) & 0x3Fu));
                out[used++] = (char) (0x80u | ((cp >> 6) & 0x3Fu));
                out[used++] = (char) (0x80u | (cp & 0x3Fu));
            }
        }
        out[used] = '\0';
        len = used;
    }

    (*env)->ReleaseStringChars(env, str, utf16);
    if (out_len) {
        *out_len = len;
    }
    return out;
}