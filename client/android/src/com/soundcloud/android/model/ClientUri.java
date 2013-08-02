package com.soundcloud.android.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import android.content.Intent;
import android.net.Uri;

/**
 * Models a SoundCloud client uri
 * @see <a href="https://soundcloudnet-main.pbworks.com/w/page/53336406/Client%20URL%20Scheme">Client URL Scheme</a>
 */
public class ClientUri {

    public static final String SCHEME = "soundcloud";
    public static final String SOUNDS_TYPE = "sounds";
    public static final String TRACKS_TYPE = "tracks";
    public static final String PLAYLISTS_TYPE = "playlists";
    public static final String USERS_TYPE = "users";

    public @NotNull final Uri uri;
    public @NotNull final String type;
    public @NotNull final String id;
    public final long numericId;

    public ClientUri(String uri) {
        this(Uri.parse(uri));
    }

    public ClientUri(@NotNull Uri uri) {
        if (!SCHEME.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("not a soundcloud uri");
        }
        final String specific = uri.getSchemeSpecificPart();
        final String[] components = specific.split(":", 2);
        if (components != null && components.length == 2) {
            type = fixType(components[0]);
            id = components[1];
            long n = -1;
            try {
                n = Long.parseLong(id);
            } catch (NumberFormatException ignored) {
            }
            numericId = n;
        } else {
            throw new IllegalArgumentException("invalid uri: "+uri);
        }
        this.uri = uri;
    }

    private static String fixType(String type){
        return type.replace("//","");
    }

    public Intent getViewIntent() {
        return new Intent(Intent.ACTION_VIEW).setData(uri);
    }

    public boolean isSound() {
        return TRACKS_TYPE.equalsIgnoreCase(type) || PLAYLISTS_TYPE.equalsIgnoreCase(type) || SOUNDS_TYPE.equalsIgnoreCase(type);
    }

    public static @Nullable ClientUri fromUri(@NotNull String uri) {
        return fromUri(Uri.parse(uri));
    }

    public static @Nullable ClientUri fromUri(Uri uri) {
        try {
            return new ClientUri(uri);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Uri forTrack(long id) {
        return Uri.parse("soundcloud:sounds:"+id);
    }

    public static Uri forUser(long id) {
        return Uri.parse("soundcloud:users:"+id);
    }

    @Override
    public String toString() {
        return uri.toString();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientUri clientUri = (ClientUri) o;
        return uri.equals(clientUri.uri);
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }
}
