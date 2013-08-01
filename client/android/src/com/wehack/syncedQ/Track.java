package com.wehack.syncedQ;

import com.soundcloud.android.utils.images.ImageUtils;
import org.jetbrains.annotations.Nullable;

import android.net.Uri;

import java.net.MalformedURLException;
import java.net.URL;

public class Track {

    private long id;
    private String title;

    private String waveform_url;
    private String artwork_url;

    private int duration;

    private String stream_url;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    private User user;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }


    public String getUsername() {
        return user.getUsername();
    }

    public long getId() {
        return id;
    }

    public String getWaveform_url() {
        return waveform_url;
    }

    public void setWaveform_url(String waveform_url) {
        this.waveform_url = waveform_url;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getStreamUrl() {
        return stream_url;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void setStream_url(String stream_url) {
        this.stream_url = stream_url;
    }

    public @Nullable
    URL getWaveformDataURL() {
            try {
                Uri waveform = Uri.parse(waveform_url);
                return new URL("http://wis.sndcdn.com/"+waveform.getLastPathSegment());
            } catch (MalformedURLException e) {
                return null;
            }
    }

    public void setArtwork_url(String artwork_url) {
        this.artwork_url = artwork_url;
    }

    public String getArtwork() {
        if (shouldLoadArtwork()){
            return artwork_url;
        } else if (user != null && user.shouldLoadIcon()){
            return user.getAvatar_url();
        } else {
            return null;
        }
    }

    public boolean shouldLoadArtwork() {
        return ImageUtils.checkIconShouldLoad(artwork_url);
    }
}
