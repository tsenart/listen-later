package com.soundcloud.android.utils.images;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.DisplayMetrics;

public enum ImageSize {
    T500("t500x500", 500, 500),
    CROP("crop", 400, 400),
    T300("t300x300", 300, 300),
    LARGE("large", 100, 100),
    T67("t67x67", 67, 67),
    BADGE("badge", 47, 47),
    SMALL("small", 32, 32),
    TINY_ARTWORK("tiny", 20, 20),
    TINY_AVATAR("tiny", 18, 18),
    MINI("mini", 16, 16),
    Unknown("large", 100, 100);

    public final int width;
    public final int height;
    public final String key;

    ImageSize(String key, int width, int height) {
        this.key = key;
        this.width = width;
        this.height = height;
    }

    public static ImageSize fromString(String s) {
        for (ImageSize gs : values()) {
            if (gs.key.equalsIgnoreCase(s)) return gs;
        }
        return Unknown;
    }

    public static String formatUriForList(Context c, String uri){
        return getListItemImageSize(c).formatUri(uri);
    }

    public static ImageSize getListItemImageSize(Context c) {
        if (ImageUtils.isScreenXL(c)) {
            return ImageSize.LARGE;
        } else {
            if (c.getResources().getDisplayMetrics().density > 1) {
                return ImageSize.LARGE;
            } else {
                return ImageSize.BADGE;
            }
        }
    }

    public static String formatUriForNotificationLargeIcon(Context c, String uri) {
        return getNotificationLargeIconImageSize(c.getResources().getDisplayMetrics()).formatUri(uri);
    }

    private static ImageSize getNotificationLargeIconImageSize(DisplayMetrics metrics) {
        if (metrics.density > 2) {
            return ImageSize.T300;
        } else {
            return ImageSize.LARGE;
        }
    }

    public static String formatUriForSearchSuggestionsList(Context c, String uri) {
        return getSearchSuggestionsListItemImageSize(c).formatUri(uri);
    }

    public static ImageSize getSearchSuggestionsListItemImageSize(Context c) {
        if (ImageUtils.isScreenXL(c)) {
            return ImageSize.T67;
        } else {
            if (c.getResources().getDisplayMetrics().density > 1) {
                return ImageSize.BADGE;
            } else {
                return ImageSize.SMALL;
            }
        }
    }

    public static String formatUriForPlayer(Context c, String uri) {
        return getPlayerImageSize(c).formatUri(uri);
    }

    public static ImageSize getPlayerImageSize(Context c) {
        // for now, just return T500. logic will come with more screen support
        return ImageSize.T500;
    }

    public String formatUri(String uri) {
        if (TextUtils.isEmpty(uri)) return null;
        for (ImageSize size : ImageSize.values()) {
            if (uri.contains("-" + size.key) && this != size) {
                return uri.replace("-" + size.key, "-" + key);
            }
        }
        Uri u = Uri.parse(uri);
        if (u.getPath().equals("/resolve/image")) {
            String size = u.getQueryParameter("size");
            if (size == null) {
                return u.buildUpon().appendQueryParameter("size", key).toString();
            } else if (!size.equals(key)) {
                return uri.replace(size, key);
            }
        }
        return uri;
    }

    public static ImageSize getMinimumSizeFor(int width, int height, boolean fillDimensions) {
        ImageSize valid = null;
        for (ImageSize gs : values()) {
            if (fillDimensions){
                if (gs.width >= width && gs.height >= height) {
                    valid = gs;
                } else {
                    break;
                }
            } else {
                if (gs.width >= width || gs.height >= height) {
                    valid = gs;
                } else {
                    break;
                }
            }

        }
        return valid == null ? Unknown : valid;
    }

}
