package com.google.ar.core.examples.java.helloar;

import android.app.ActionBar;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.ar.rendercore.math.Vector3;

import java.util.List;

/** Created by wilkinsonclay on 3/26/18. */
class GalleryAdapter extends RecyclerView.Adapter {
    private final List<GalleryItem> items;

    public GalleryAdapter(List<GalleryItem> items) {
        this.items = items;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Create a new view.
        View v = new ImageView(parent.getContext());
        v.setLayoutParams(
                new FrameLayout.LayoutParams(
                        ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT));
        return new GalleryItemHolder(v);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        ImageView imageView = (ImageView) holder.itemView;
        imageView.setImageBitmap(
                BitmapFactory.decodeResource(
                        holder.itemView.getResources(), items.get(position).thumbnail));
        ((GalleryItemHolder) holder).setItem(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class GalleryItem {
        public static final String KeyName = "name";
        public static final String KeyUri = "uri";
        public static final String KeyScale = "scale";
        public final String name;
        public final Uri modelUri;
        public final int thumbnail;
        public Vector3 scale;

        public GalleryItem(String name, int thumbnailId, String modelUri) {
            this.name = name;
            this.thumbnail = thumbnailId;
            this.modelUri = Uri.parse(modelUri);
        }
    }
}
