#ifndef XCIMOC_CODEC_NATIVE_H
#define XCIMOC_CODEC_NATIVE_H

/* Handles hot pure codecs. out_json is malloc'ed and owned by the caller. */
int qjs_native_codec(const char *name, const char *args_json, char **out_json);

#endif