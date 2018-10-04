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

import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.view.View;

/**
 * ViewHolder for showing a thumbnail of a model from Poly.
 */
public class GalleryItemHolder extends RecyclerView.ViewHolder {
    private static final int SELECTED_COLOR = Color.WHITE;
    private static final int DESELECTED_COLOR = Color.DKGRAY;
    private final GalleryAdapter adapter;
    private GalleryItem item;

    public GalleryItemHolder(View itemView, GalleryAdapter galleryAdapter) {
        super(itemView);
        this.adapter = galleryAdapter;
    }

    public void setItem(GalleryItem item) {
        this.item = item;
        itemView.setOnClickListener(this::onClick);
        if (!item.equals(adapter.getSelected())) {
            itemView.setBackgroundColor(DESELECTED_COLOR);
            itemView.setSelected(false);
        } else {
            itemView.setSelected(true);
            itemView.setBackgroundColor(SELECTED_COLOR);
        }
    }

    private void onClick(View view) {
        GalleryItem selected = adapter.getSelected();
        if (!item.equals(selected)) {
            if (selected != null) {
                selected.getViewHolder().itemView.setBackgroundColor(DESELECTED_COLOR);
            }
            adapter.setSelected(item);
            itemView.setSelected(true);
            itemView.setBackgroundColor(SELECTED_COLOR);
        } else {
            adapter.setSelected(null);
            itemView.setSelected(false);
            itemView.setBackgroundColor(DESELECTED_COLOR);
        }
    }


}
