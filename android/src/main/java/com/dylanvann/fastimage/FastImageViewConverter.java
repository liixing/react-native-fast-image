package com.dylanvann.fastimage;

import static com.bumptech.glide.request.RequestOptions.signatureOf;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.Headers;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ApplicationVersionSignature;
import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
import com.facebook.react.bridge.NoSuchKeyException;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

class FastImageViewConverter {
    private static final Drawable TRANSPARENT_DRAWABLE = new ColorDrawable(Color.TRANSPARENT);

    private static final Map<String, FastImageCacheControl> FAST_IMAGE_CACHE_CONTROL_MAP =
            new HashMap<String, FastImageCacheControl>() {{
                put("immutable", FastImageCacheControl.IMMUTABLE);
                put("web", FastImageCacheControl.WEB);
                put("cacheOnly", FastImageCacheControl.CACHE_ONLY);
            }};

    private static final Map<String, Priority> FAST_IMAGE_PRIORITY_MAP =
            new HashMap<String, Priority>() {{
                put("low", Priority.LOW);
                put("normal", Priority.NORMAL);
                put("high", Priority.HIGH);
            }};

    private static final Map<String, ImageView.ScaleType> FAST_IMAGE_RESIZE_MODE_MAP =
            new HashMap<String, ImageView.ScaleType>() {{
                put("contain", ScaleType.FIT_CENTER);
                put("cover", ScaleType.CENTER_CROP);
                put("stretch", ScaleType.FIT_XY);
                put("center", ScaleType.CENTER_INSIDE);
            }};

    // Resolve the source uri to a file path that android understands.
    static @Nullable
    FastImageSource getImageSource(Context context, @Nullable ReadableMap source) {
        if (source == null) {
            return null;
        }
        
        String cacheKey = null;
        try {
            cacheKey = source.hasKey("cacheKey") ? source.getString("cacheKey") : null;
        } catch (NoSuchKeyException e) {
            // cacheKey is optional
        }
        
        return new FastImageSource(context, source.getString("uri"), getHeaders(source), cacheKey);
    }

    static Headers getHeaders(ReadableMap source) {
        Headers headers = Headers.DEFAULT;

        if (!source.hasKey("headers")) {
            return headers;
        }

        // this will be true in case of Expo projects
        if (source.getType("headers") == ReadableType.Map) {
            ReadableMap headersMap = source.getMap("headers");
            ReadableMapKeySetIterator iterator = headersMap.keySetIterator();
            LazyHeaders.Builder builder = new LazyHeaders.Builder();

            while (iterator.hasNextKey()) {
                String header = iterator.nextKey();
                String value = headersMap.getString(header);

                if (value != null) {
                    builder.addHeader(header, value);
                }
            }

            headers = builder.build();
            return headers;
        }

        ReadableArray headersArray = source.getArray("headers");
        if (headersArray == null || headersArray.size() == 0) {
            return headers;
        }

        LazyHeaders.Builder builder = new LazyHeaders.Builder();

        for (int i = 0; i < headersArray.size(); i++) {
            ReadableMap headerEntry = headersArray.getMap(i);

            String header = headerEntry.hasKey("header") ? headerEntry.getString("header") : null;
            String value = headerEntry.hasKey("value") ? headerEntry.getString("value") : null;

            if (header != null && value != null) {
                builder.addHeader(header, value);
            }
        }

        headers = builder.build();
        return headers;
    }

    static RequestOptions getOptions(Context context, FastImageSource imageSource, ReadableMap source) {
        // Get priority.
        final Priority priority = FastImageViewConverter.getPriority(source);
        // Get cache control method.
        final FastImageCacheControl cacheControl = FastImageViewConverter.getCacheControl(source);
        DiskCacheStrategy diskCacheStrategy = DiskCacheStrategy.AUTOMATIC;
        boolean onlyFromCache = false;
        boolean skipMemoryCache = false;
        switch (cacheControl) {
            case WEB:
                // If using none then OkHttp integration should be used for caching.
                diskCacheStrategy = DiskCacheStrategy.NONE;
                skipMemoryCache = true;
                break;
            case CACHE_ONLY:
                onlyFromCache = true;
                break;
            case IMMUTABLE:
                // Use defaults.
                break;
        }

        RequestOptions options = new RequestOptions()
                .diskCacheStrategy(diskCacheStrategy)
                .onlyRetrieveFromCache(onlyFromCache)
                .skipMemoryCache(skipMemoryCache)
                .priority(priority)
                .placeholder(TRANSPARENT_DRAWABLE);

        if (imageSource.isResource()) {
            // Every local resource (drawable) in Android has its own unique numeric id, which are
            // generated at build time. Although these ids are unique, they are not guaranteed unique
            // across builds. The underlying glide implementation caches these resources. To make
            // sure the cache does not return the wrong image, we should clear the cache when the
            // application version changes. Adding a cache signature for only these local resources
            // solves this issue: https://github.com/DylanVann/react-native-fast-image/issues/402
            options = options.apply(signatureOf(ApplicationVersionSignature.obtain(context)));
        }

        return options;
    }

    private static FastImageCacheControl getCacheControl(ReadableMap source) {
        return getValueFromSource("cache", "immutable", FAST_IMAGE_CACHE_CONTROL_MAP, source);
    }

    private static Priority getPriority(ReadableMap source) {
        return getValueFromSource("priority", "normal", FAST_IMAGE_PRIORITY_MAP, source);
    }

    static ScaleType getScaleType(String propValue) {
        return getValue("resizeMode", "cover", FAST_IMAGE_RESIZE_MODE_MAP, propValue);
    }

    private static <T> T getValue(String propName, String defaultPropValue, Map<String, T> map, String propValue) {
        if (propValue == null) propValue = defaultPropValue;
        T value = map.get(propValue);
        if (value == null)
            throw new JSApplicationIllegalArgumentException("FastImage, invalid " + propName + " : " + propValue);
        return value;
    }

    private static <T> T getValueFromSource(String propName, String defaultProp, Map<String, T> map, ReadableMap source) {
        String propValue;
        try {
            propValue = source != null ? source.getString(propName) : null;
        } catch (NoSuchKeyException e) {
            propValue = null;
        }
        return getValue(propName, defaultProp, map, propValue);
    }
}
