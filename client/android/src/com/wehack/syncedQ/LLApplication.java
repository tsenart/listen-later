package com.wehack.syncedQ;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.soundcloud.android.utils.images.ImageOptionsFactory;
import com.soundcloud.api.ApiWrapper;
import com.soundcloud.api.Token;

import android.app.Application;

public class LLApplication extends Application {

    public static final String TOKEN = "1-2863-52909310-46110de8461fdfb1";
    public static LLApplication instance;
    ApiWrapper mWrapper = new ApiWrapper("xce4DwrNEmNzTIcuaAjkA", "GANQKmfSMpx9FUJ7G837OQZzeBEyv7Fj3ART1WvjQA", null, null);
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        mWrapper.setToken(new Token(TOKEN, null, Token.SCOPE_NON_EXPIRING));
        ImageLoader.getInstance().init(
                new ImageLoaderConfiguration.Builder(this)
                        .defaultDisplayImageOptions(ImageOptionsFactory.cache())
                        .build()
        );

    }

    public ApiWrapper getApiWrapper() {
        return mWrapper;
    }
}
