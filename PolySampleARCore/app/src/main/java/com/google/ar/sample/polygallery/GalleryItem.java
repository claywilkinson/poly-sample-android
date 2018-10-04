/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.sample.polygallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.rendering.ModelRenderable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class GalleryItem {
    private static final String TAG = "GalleryItem";
    private final String key;
    private String displayName;
    private String authorName;
    private String license;
    private String description;
    private String thumbnail;
    private String modelUrl;
    private CompletableFuture<Bitmap> thumbnailHolder;
    private RecyclerView.ViewHolder viewHolder;
    private CompletableFuture<ModelRenderable> renderableHolder;

    public GalleryItem(String key) {
        this.key = key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setAuthorInfo(String authorName, String license) {
        this.authorName = authorName;
        this.license = license;
    }

    public String getAuthor() {
        return authorName;
    }

    public String getLicense() {
        return license;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public void loadThumbnail(Handler handler) {
        // Send an asynchronous request.
        if (thumbnailHolder == null) {
            thumbnailHolder = new CompletableFuture<>();
            AsyncHttpRequest request = new AsyncHttpRequest(getThumbnail(),
                    handler, new AsyncHttpRequest.CompletionListener() {

                @Override
                public void onHttpRequestSuccess(byte[] responseBody) {
                    thumbnailHolder.complete(BitmapFactory.decodeByteArray(responseBody,
                            0, responseBody.length));
                }

                @Override
                public void onHttpRequestFailure(int statusCode, String message, Exception exception) {
                    Log.e(TAG, "Cannot load thumbnail: " + statusCode + " " + message, exception);
                    thumbnailHolder.completeExceptionally(exception);
                }
            });
            request.send();
        }
    }

    public String getModelUrl() {
        return modelUrl;
    }

    public void setModelUrl(String modelUrl) {
        this.modelUrl = modelUrl;
    }

    public RecyclerView.ViewHolder getViewHolder() {
        return viewHolder;
    }

    public void setViewHolder(RecyclerView.ViewHolder viewHolder) {
        this.viewHolder = viewHolder;
    }

    public CompletableFuture<ModelRenderable> getRenderableHolder() {

        if (renderableHolder == null) {
            Context context = viewHolder.itemView.getContext();
            RenderableSource source = RenderableSource.builder().setSource(context,
                    Uri.parse(modelUrl), RenderableSource.SourceType.GLTF2)
                    .setRecenterMode(RenderableSource.RecenterMode.ROOT)
                    .build();


            renderableHolder = ModelRenderable.builder().setRegistryId(key)
                    .setSource(context, source)
                    .build();
        }
        return renderableHolder;
    }

    public CompletionStage<Bitmap> getThumbnailHolder() {
        return thumbnailHolder;
    }
}