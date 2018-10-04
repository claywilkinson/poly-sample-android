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
import android.widget.TextView;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.collision.Box;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.Locale;
import java.util.Objects;

public class SceneContext {
    private final Context context;
    private AnchorNode anchorNode;
    private Node modelNode;
    private Node infoCard;

    public SceneContext(Context context) {
        this.context = context;
    }

    public static void setScaleRange(TransformableNode node, float minSize, float maxSize) {
        // Set the min/max scale based on size not factors
        Box box = (Box) node.getRenderable().getCollisionShape();
        Vector3 size = Objects.requireNonNull(box).getSize();
        // use the largest dimension
        float maxDim = Math.max(size.x, Math.max(size.y, size.z));

        float minScale = node.getScaleController().getMinScale();
        float maxScale = node.getScaleController().getMaxScale();
        // min is 1cm
        minScale = Math.min(minSize / maxDim, minScale);
        /// max is 3m
        maxScale = Math.max(maxSize / maxDim, maxScale);

        node.getScaleController().setMinScale(minScale);
        node.getScaleController().setMaxScale(maxScale);
    }

    public void resetContext() {
        if (modelNode != null) {
            modelNode.setParent(null);
            modelNode = null;
        }

        if (anchorNode != null) {
            anchorNode.getAnchor().detach();
            anchorNode.setParent(null);
            anchorNode = null;
        }
        if (infoCard != null) {
            infoCard.setParent(null);
            infoCard = null;
        }
    }

    public boolean hasModelNode() {
        return modelNode != null;
    }

    public String setModelInfo(Camera camera) {
        String msg = null;
        if (modelNode.getRenderable() != null) {
            Vector3 scale = modelNode.getLocalScale();
            Vector3 size = ((Box) modelNode.getCollisionShape()).getSize();
            size.x *= scale.x;
            size.y *= scale.y;
            size.z *= scale.z;
            Vector3 dir = Vector3.subtract(modelNode.getForward(), camera.getForward());
            msg = String.format(Locale.getDefault(), "%s\n%s\n%s",
                    String.format(Locale.getDefault(), "scale: (%.02f, %.02f, %.02f)",
                            scale.x,
                            scale.y,
                            scale.z),
                    String.format(Locale.getDefault(), "size: (%.02f, %.02f, %.02f)",
                            size.x,
                            size.y,
                            size.z),
                    String.format(Locale.getDefault(), "dir: (%.02f, %.02f, %.02f)",
                            dir.x,
                            dir.y,
                            dir.z)
            );

        }
        return msg;
    }

    public void rotateInfoCardToCamera(Camera camera) {
        // Rotate the card to look at the camera.
        if (infoCard != null) {
            Vector3 cameraPosition = camera.getWorldPosition();
            Vector3 cardPosition = infoCard.getWorldPosition();
            Vector3 direction = Vector3.subtract(cameraPosition, cardPosition);
            Quaternion lookRotation = Quaternion.lookRotation(direction, Vector3.up());
            infoCard.setWorldRotation(lookRotation);
        }
    }

    public void resetModelNode(Scene scene, Vector3 position) {
        if (modelNode != null) {
            modelNode.setParent(null);
        }
        //Create the model node each time so scale and rotation is reset.
        modelNode = new Node();
        modelNode.setParent(scene);
        modelNode.setWorldPosition(position);
    }

    public void attachInfoCardNode(GalleryItem selectedItem) {
        if (infoCard == null) {
            infoCard = new Node();
            ViewRenderable.builder()
                    .setView(context, R.layout.model_info)
                    .build()
                    .thenAccept(
                            (renderable) -> {
                                infoCard.setRenderable(renderable);
                                setModelLabel(renderable, selectedItem);
                            })
                    .exceptionally(
                            (throwable) -> {
                                throw new AssertionError(
                                        "Could not load plane card view.", throwable);
                            });
        } else {
            setModelLabel((ViewRenderable) infoCard.getRenderable(), selectedItem);
        }
        infoCard.setParent(modelNode);
        float height = .5f;
        if (modelNode.getRenderable() instanceof ModelRenderable) {
            height = getRenderableHeight((ModelRenderable) modelNode.getRenderable());
        }
        infoCard.setLocalPosition(new Vector3(0, height, 0));
    }

    private float getRenderableHeight(ModelRenderable renderable) {
        Box box = (Box) renderable.getCollisionShape();
        return Objects.requireNonNull(box).getCenter().y + box.getExtents().y;
    }

    private void setModelLabel(ViewRenderable viewRenderable, GalleryItem selectedItem) {
        TextView textView = (TextView) viewRenderable.getView();
        textView.setText(String.format("%s by %s\n%s",
                selectedItem.getDisplayName(), selectedItem.getAuthor(), selectedItem.getLicense()));
    }

    public void setModelRenderable(ModelRenderable renderable) {
        modelNode.setRenderable(renderable);
        infoCard.setLocalPosition(new Vector3(0, getRenderableHeight(renderable), 0));

    }

    public void resetAnchorNode(Anchor anchor, Scene scene) {
        // Clean up old anchor
        if (anchorNode != null && anchorNode.getAnchor() != null) {
            anchorNode.getAnchor().detach();
        } else {
            anchorNode = new AnchorNode();
            anchorNode.setParent(scene);
        }
        anchorNode.setAnchor(anchor);
    }

    public void attachModelNodeToAnchorNode(TransformableNode node) {
        if (modelNode != null) {
            modelNode.setParent(null);
        }

        modelNode = node;
        modelNode.setParent(anchorNode);
    }
}
