package com.wehack.syncedQ;

import com.soundcloud.android.utils.images.ImageUtils;

public class User {
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    private String username;

    public String getAvatar_url() {
        return avatar_url;
    }

    public void setAvatar_url(String avatar_url) {
        this.avatar_url = avatar_url;
    }

    private String avatar_url;

    public boolean shouldLoadIcon() {
        return ImageUtils.checkIconShouldLoad(avatar_url);
    }

}
