#include "codec_native.h"

#include <ctype.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/* Decode the small JSON object emitted by source_sdk.js. This avoids sending
 * hot codec calls through another Java/JSON round trip. */
static char *json_string(const char *json, const char *key) {
    char needle[64];
    size_t kl = strlen(key), i, n = strlen(json), outn = 0;
    char *out;
    if (kl + 3 >= sizeof(needle)) return NULL;
    needle[0] = '"';
    memcpy(needle + 1, key, kl);
    needle[kl + 1] = '"';
    needle[kl + 2] = 0;
    for (i = 0; i + kl + 2 < n; i++) {
        if (memcmp(json + i, needle, kl + 2) != 0) continue;
        i += kl + 2;
        while (i < n && isspace((unsigned char) json[i])) i++;
        if (i >= n || json[i++] != ':') continue;
        while (i < n && isspace((unsigned char) json[i])) i++;
        if (i >= n || json[i++] != '"') return NULL;
        out = (char *) malloc(n - i + 1);
        if (!out) return NULL;
        while (i < n && json[i] != '"') {
            unsigned char c = (unsigned char) json[i++];
            if (c != '\\') {
                out[outn++] = (char) c;
                continue;
            }
            if (i >= n) break;
            c = (unsigned char) json[i++];
            if (c == 'n') out[outn++] = '\n';
            else if (c == 'r') out[outn++] = '\r';
            else if (c == 't') out[outn++] = '\t';
            else if (c == 'b') out[outn++] = '\b';
            else if (c == 'f') out[outn++] = '\f';
            else if (c == 'u' && i + 4 <= n) {
                unsigned v = 0;
                int k;
                for (k = 0; k < 4; k++) {
                    char x = json[i++];
                    v = v * 16 + (unsigned) (isdigit((unsigned char) x) ? x - '0' : (
                            tolower((unsigned char) x) - 'a' + 10));
                }
                if (v < 0x80) out[outn++] = (char) v;
                else if (v < 0x800) {
                    out[outn++] = (char) (0xc0 | (v >> 6));
                    out[outn++] = (char) (0x80 | (v & 63));
                }
                else {
                    out[outn++] = (char) (0xe0 | (v >> 12));
                    out[outn++] = (char) (0x80 | ((v >> 6) & 63));
                    out[outn++] = (char) (0x80 | (v & 63));
                }
            } else out[outn++] = (char) c;
        }
        out[outn] = 0;
        return out;
    }
    return NULL;
}

static char *json_quote(const unsigned char *p, size_t n) {
    size_t i, m = 2;
    char *s;
    for (i = 0; i < n; i++) m += (p[i] == '"' || p[i] == '\\') ? 2 : (p[i] < 0x20 ? 6 : 1);
    s = (char *) malloc(m + 1);
    if (!s) return NULL;
    s[0] = '"';
    m = 1;
    for (i = 0; i < n; i++) {
        unsigned char c = p[i];
        if (c == '"' || c == '\\') {
            s[m++] = '\\';
            s[m++] = (char) c;
        }
        else if (c == '\n') {
            s[m++] = '\\';
            s[m++] = 'n';
        }
        else if (c == '\r') {
            s[m++] = '\\';
            s[m++] = 'r';
        }
        else if (c == '\t') {
            s[m++] = '\\';
            s[m++] = 't';
        }
        else if (c < 0x20) m += (size_t) sprintf(s + m, "\\u%04x", c);
        else s[m++] = (char) c;
    }
    s[m++] = '"';
    s[m] = 0;
    return s;
}

static uint32_t rol(uint32_t x, int n) { return (x << n) | (x >> (32 - n)); }

static const uint32_t md5k[64] = {
        0xd76aa478, 0xe8c7b756, 0x242070db, 0xc1bdceee, 0xf57c0faf, 0x4787c62a, 0xa8304613,
        0xfd469501,
        0x698098d8, 0x8b44f7af, 0xffff5bb1, 0x895cd7be, 0x6b901122, 0xfd987193, 0xa679438e,
        0x49b40821,
        0xf61e2562, 0xc040b340, 0x265e5a51, 0xe9b6c7aa, 0xd62f105d, 0x02441453, 0xd8a1e681,
        0xe7d3fbc8,
        0x21e1cde6, 0xc33707d6, 0xf4d50d87, 0x455a14ed, 0xa9e3e905, 0xfcefa3f8, 0x676f02d9,
        0x8d2a4c8a,
        0xfffa3942, 0x8771f681, 0x6d9d6122, 0xfde5380c, 0xa4beea44, 0x4bdecfa9, 0xf6bb4b60,
        0xbebfbc70,
        0x289b7ec6, 0xeaa127fa, 0xd4ef3085, 0x04881d05, 0xd9d4d039, 0xe6db99e5, 0x1fa27cf8,
        0xc4ac5665,
        0xf4292244, 0x432aff97, 0xab9423a7, 0xfc93a039, 0x655b59c3, 0x8f0ccc92, 0xffeff47d,
        0x85845dd1,
        0x6fa87e4f, 0xfe2ce6e0, 0xa3014314, 0x4e0811a1, 0xf7537e82, 0xbd3af235, 0x2ad7d2bb,
        0xeb86d391};
static const unsigned char md5s[64] = {7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
                                       5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 4,
                                       11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6,
                                       10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21};

static void md5(const unsigned char *in, size_t len, unsigned char out[16]) {
    uint32_t h[4] = {0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476};
    size_t total = ((len + 9 + 63) / 64) * 64, off;
    unsigned char *p = (unsigned char *) calloc(1, total);
    if (!p) return;
    memcpy(p, in, len);
    p[len] = 0x80;
    {
        uint64_t bits = (uint64_t) len * 8;
        memcpy(p + total - 8, &bits, 8);
    }
    for (off = 0; off < total; off += 64) {
        uint32_t a = h[0], b = h[1], c = h[2], d = h[3], x[16], f, g, t;
        int i;
        for (i = 0; i < 16; i++) memcpy(&x[i], p + off + i * 4, 4);
        for (i = 0; i < 64; i++) {
            if (i < 16) {
                f = (b & c) | (~b & d);
                g = i;
            }
            else if (i < 32) {
                f = (d & b) | (~d & c);
                g = (5 * i + 1) % 16;
            }
            else if (i < 48) {
                f = b ^ c ^ d;
                g = (3 * i + 5) % 16;
            }
            else {
                f = c ^ (b | ~d);
                g = (7 * i) % 16;
            }
            t = d;
            d = c;
            c = b;
            b = b + rol(a + f + md5k[i] + x[g], md5s[i]);
            a = t;
        }
        h[0] += a;
        h[1] += b;
        h[2] += c;
        h[3] += d;
    }
    memcpy(out, h, 16);
    free(p);
}

static int b64v(int c) {
    if (c >= 'A' && c <= 'Z')return c - 'A';
    if (c >= 'a' && c <= 'z')return c - 'a' + 26;
    if (c >= '0' && c <= '9')return c - '0' + 52;
    if (c == '+' || c == '-')return 62;
    if (c == '/' || c == '_')return 63;
    return -1;
}

static char *b64enc(const unsigned char *p, size_t n) {
    static const char *a = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    size_t i, m = ((n + 2) / 3) * 4;
    char *s = (char *) malloc(m + 1), *q = s;
    if (!s)return NULL;
    for (i = 0; i < n; i += 3) {
        unsigned v = p[i] << 16 | (i + 1 < n ? p[i + 1] : 0) << 8 | (i + 2 < n ? p[i + 2] : 0);
        *q++ = a[v >> 18];
        *q++ = a[(v >> 12) & 63];
        *q++ = i + 1 < n ? a[(v >> 6) & 63] : '=';
        *q++ = i + 2 < n ? a[v & 63] : '=';
    }
    *q = 0;
    return s;
}

static unsigned char *b64dec(const char *s, size_t *outn) {
    size_t n = strlen(s), i, j = 0;
    unsigned char *o = (unsigned char *) malloc(n / 4 * 3 + 3);
    int a, b, c, d;
    if (!o)return NULL;
    for (i = 0; i < n;) {
        while (i < n && isspace((unsigned char) s[i]))i++;
        if (i >= n)break;
        a = b64v(s[i++]);
        b = i < n ? b64v(s[i++]) : -1;
        c = (i < n && s[i] != '=') ? b64v(s[i++]) : -1;
        if (i < n && s[i] == '=')i++;
        d = (i < n && s[i] != '=') ? b64v(s[i++]) : -1;
        if (a < 0 || b < 0 || (c < 0 && c != -1) || (d < 0 && d != -1)) {
            free(o);
            return NULL;
        }
        o[j++] = (unsigned char) ((a << 2) | (b >> 4));
        if (c >= 0)o[j++] = (unsigned char) ((b << 4) | (c >> 2));
        if (d >= 0)o[j++] = (unsigned char) ((c << 6) | d);
    }
    *outn = j;
    return o;
}

static char *urlenc(const char *s) {
    static const char h[] = "0123456789ABCDEF";
    size_t i, n = strlen(s), m = 0;
    char *o;
    for (i = 0; i < n; i++)
        m += isalnum((unsigned char) s[i]) || strchr("-_.~", s[i]) ? 1 : (s[i] == ' ' ? 1 : 3);
    o = (char *) malloc(m + 1);
    if (!o)return NULL;
    m = 0;
    for (i = 0; i < n; i++) {
        unsigned char c = s[i];
        if (isalnum(c) || strchr("-_.~", c))o[m++] = c;
        else if (c == ' ')
            o[m++] = '+';
        else {
            o[m++] = '%';
            o[m++] = h[c >> 4];
            o[m++] = h[c & 15];
        }
    }
    o[m] = 0;
    return o;
}

static int hexv(int c) {
    if (c >= '0' && c <= '9')return c - '0';
    if (c >= 'a' && c <= 'f')return c - 'a' + 10;
    if (c >= 'A' && c <= 'F')return c - 'A' + 10;
    return -1;
}

static char *urldec(const char *s) {
    size_t i, n = strlen(s), m = 0;
    char *o = (char *) malloc(n + 1);
    if (!o)return NULL;
    for (i = 0; i < n; i++) {
        if (s[i] == '+')o[m++] = ' ';
        else if (s[i] == '%' && i + 2 < n) {
            int a = hexv(s[i + 1]), b = hexv(s[i + 2]);
            if (a < 0 || b < 0) {
                free(o);
                return NULL;
            }
            o[m++] = (char) ((a << 4) | b);
            i += 2;
        }
        else o[m++] = s[i];
    }
    o[m] = 0;
    return o;
}

/* LZ-string decompressFromBase64, kept byte-oriented until the final UTF-8
 * conversion so non-ASCII manga titles do not pass through a lossy locale. */
typedef struct {
    const char *s;
    size_t index;
    int value, position;
} LzReader;

static int lzbit(LzReader *r) {
    int bit = r->value & r->position;
    r->position >>= 1;
    if (!r->position) {
        r->position = 32;
        r->value = r->index < strlen(r->s) ? b64v(r->s[r->index++]) : 0;
    }
    return bit ? 1 : 0;
}

static uint16_t *lz64(const char *s, size_t *outn) {
    char **dict;
    size_t dictn = 4, cap = 256, rn = 0, i;
    uint16_t *result = (uint16_t *) malloc(cap * sizeof(uint16_t));
    LzReader r = {s, 1, b64v(s[0]), 32};
    int bits = 0, numBits = 3, enlarge = 4, c, code;
    if (!result || !s[0]) {
        free(result);
        return NULL;
    }
    dict = (char **) calloc(4, sizeof(char *));
    for (i = 0; i < 3; i++)dict[i] = strdup("");
    for (i = 0; i < 2; i++)bits = (bits << 1) | lzbit(&r);
    if (bits == 2) {
        free(result);
        for (i = 0; i < 3; i++)free(dict[i]);
        free(dict);
        return NULL;
    }
    {
        int width = bits == 0 ? 8 : 16;
        uint16_t ch = 0;
        for (i = 0; i < (size_t) width; i++)ch |= (uint16_t) lzbit(&r) << i;
        dict[3] = (char *) malloc(3);
        dict[3][0] = (char) (ch & 255);
        dict[3][1] = (char) (ch >> 8);
        dict[3][2] = 0;
        c = 3;
    }
    while (1) {
        bits = 0;
        for (i = 0; i < (size_t) numBits; i++)bits |= lzbit(&r) << i;
        code = bits;
        if (code == 0 || code == 1) {
            int width = code ? 16 : 8;
            uint16_t ch = 0;
            for (i = 0; i < (size_t) width; i++)ch |= (uint16_t) lzbit(&r) << i;
            if (dictn == cap) {
                cap *= 2;
                dict = (char **) realloc(dict, cap * sizeof(char *));
            }
            dict[dictn] = (char *) malloc(3);
            dict[dictn][0] = (char) (ch & 255);
            dict[dictn][1] = (char) (ch >> 8);
            dict[dictn][2] = 0;
            code = (int) dictn++;
            if (--enlarge == 0) {
                enlarge = 1 << numBits;
                numBits++;
            }
        }
        else if (code == 2)break;
        if (code < 0 || (size_t) code >= dictn) {
            free(result);
            result = NULL;
            break;
        }
        {
            char *entry = dict[code];
            size_t el = strlen(entry) / 2;
            for (i = 0; i < el; i++) {
                if (rn == cap * 2) {
                    cap *= 2;
                    result = (uint16_t *) realloc(result, cap * sizeof(uint16_t));
                }
                result[rn++] = (uint16_t) ((unsigned char) entry[i * 2] |
                                           ((unsigned char) entry[i * 2 + 1] << 8));
            }
            if (dictn == cap) {
                cap *= 2;
                dict = (char **) realloc(dict, cap * sizeof(char *));
            }
            {
                char *w = dict[c], *x = (char *) malloc(strlen(w) + strlen(entry) + 3);
                strcpy(x, w);
                x[strlen(w)] = (char) entry[0];
                x[strlen(w) + 1] = entry[1];
                x[strlen(w) + 2] = 0;
                dict[dictn++] = x;
            }
            c = code;
        }
        if (--enlarge == 0) {
            enlarge = 1 << numBits;
            numBits++;
        }
        if (r.index > strlen(s) + 1) {
            free(result);
            result = NULL;
            break;
        }
    }
    for (i = 0; i < dictn; i++)free(dict[i]);
    free(dict);
    *outn = rn;
    return result;
}

static char *u16quote(const uint16_t *p, size_t n) {
    size_t i, m = 2;
    char *o, *q;
    for (i = 0; i < n; i++)m += p[i] < 0x80 ? 1 : p[i] < 0x800 ? 2 : 3;
    o = (char *) malloc(m + 1);
    if (!o)return NULL;
    q = o;
    *q++ = '"';
    for (i = 0; i < n; i++) {
        uint32_t c = p[i];
        if (c < 0x80)*q++ = (char) c;
        else if (c < 0x800) {
            *q++ = (char) (0xc0 | (c >> 6));
            *q++ = (char) (0x80 | (c & 63));
        }
        else {
            *q++ = (char) (0xe0 | (c >> 12));
            *q++ = (char) (0x80 | ((c >> 6) & 63));
            *q++ = (char) (0x80 | (c & 63));
        }
    }
    *q++ = '"';
    *q = 0;
    return o;
}

int qjs_native_codec(const char *name, const char *args, char **out) {
    char *data = NULL, *op = NULL, *r = NULL;
    size_t n;
    unsigned char digest[16];
    *out = NULL;
    if (!strcmp(name, "md5")) {
        data = json_string(args, "data");
        if (data) {
            char hex[33];
            size_t i;
            md5((unsigned char *) data, strlen(data), digest);
            for (i = 0; i < 16; i++)sprintf(hex + i * 2, "%02x", digest[i]);
            r = json_quote((unsigned char *) hex, 32);
        }
    }
    else if (!strcmp(name, "base64")) {
        data = json_string(args, "data");
        op = json_string(args, "op");
        if (data && op) {
            if (!strcmp(op, "encode")) {
                char *x = b64enc((unsigned char *) data, strlen(data));
                if (x) {
                    r = json_quote((unsigned char *) x, strlen(x));
                    free(x);
                }
            }
            else {
                unsigned char *x = b64dec(data, &n);
                if (x) {
                    r = json_quote(x, n);
                    free(x);
                }
            }
        }
    }
    else if (!strcmp(name, "urlencode")) {
        data = json_string(args, "data");
        if (data) {
            char *x = urlenc(data);
            if (x) {
                r = json_quote((unsigned char *) x, strlen(x));
                free(x);
            }
        }
    }
    else if (!strcmp(name, "urldecode")) {
        data = json_string(args, "data");
        if (data) {
            char *x = urldec(data);
            if (x) {
                r = json_quote((unsigned char *) x, strlen(x));
                free(x);
            }
        }
    }
        /* LZ-string stays on the existing Java implementation until its
            UTF-16 dictionary representation has a dedicated conformance test. */
    else {
        free(data);
        free(op);
        return 0;
    }
    free(data);
    free(op);
    if (!r)return 0;
    *out = r;
    return 1;
}