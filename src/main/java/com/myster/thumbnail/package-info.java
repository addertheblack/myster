/**
 * Local OS thumbnails, shared memory caching and a standalone progressive preview grid.
 * Call {@link com.myster.thumbnail.Thumbnails#summonThumbnailAsync(java.nio.file.Path, int)}
 * from Swing, or its blocking companion from a worker thread. Unsupported files return null.
 * Platform implementation details stay within this package.
 */
package com.myster.thumbnail;
