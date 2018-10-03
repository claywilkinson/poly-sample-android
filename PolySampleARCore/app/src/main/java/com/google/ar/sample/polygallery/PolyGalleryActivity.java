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

import android.annotation.SuppressLint;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.SceneView;
import com.google.ar.sceneform.collision.Box;
import com.google.ar.sceneform.collision.Ray;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.IOException;
import java.util.List;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3d model of the Android robot.
 */
public class PolyGalleryActivity extends AppCompatActivity {
  private static final String TAG = PolyGalleryActivity.class.getSimpleName();

  private RecyclerView gallery;
  private TextView model_info;

  private AnchorNode anchorNode;
  private Node modelNode;
  private Node infoCard;

  private Handler mBackgroundThreadHandler;
  private Fragment fragment;

  private static final Vector3 STARTING_CAMERA_POSITION = new Vector3(0, 2, 3);
  private static final Quaternion STARTING_CAMERA_ROTATION = Quaternion.axisAngle(Vector3.left(),15f);


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    gallery = findViewById(R.id.recyclerView);
    intializeGallery(gallery);

    findViewById(R.id.search).setOnClickListener(this::onSearch);


    Switch arToggle = findViewById(R.id.ar_mode_toggle);
    arToggle.setChecked(true);
    arToggle.setOnCheckedChangeListener(this::onArToggle);
    initializeARMode();

    // This is a text overlay.
    model_info = findViewById(R.id.model_info);

    // Create a background thread, where we will do the heavy lifting.
    // Our background thread, which does all of the heavy lifting so we don't block the main thread.
    HandlerThread mBackgroundThread = new HandlerThread("Worker");
    mBackgroundThread.start();
    // Handler for the background thread, to which we post background thread tasks.
    mBackgroundThreadHandler = new Handler(mBackgroundThread.getLooper());
  }

  /**
   * Handles the toggling between AR and non-AR mode.
   * @param compoundButton
   * @param checked
   */
  private void onArToggle(CompoundButton compoundButton, boolean checked) {
    if (checked) {
      Log.d(TAG, "Switching to AR Mode.");
      initializeARMode();
    } else {
      Log.d(TAG, "Switching to non-AR mode.");
      initializeNonArMode();
    }
  }

  private void initializeARMode() {
    setInfoText("Switching to AR mode.");
    cleanupFragment(fragment);

    // Put the AR Fragment in the layout.
    fragment = new ArFragment();
    getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commitAllowingStateLoss();

    // Add a listener that is called when the fragment is initialized and onResume is called
    // indicating the fragment is running.
    fragment.getLifecycle().addObserver(new LifecycleObserver() {
      @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
      public void connectListener() {
        ArFragment arFragment = (ArFragment) fragment;
        arFragment.setOnTapArPlaneListener(PolyGalleryActivity.this::onTapPlane);
        getScene().addOnUpdateListener(PolyGalleryActivity.this::onSceneUpdate);
        fragment.getLifecycle().removeObserver(this);
      }
    });
  }

  private void initializeNonArMode() {
    setInfoText("Switching to non-AR mode.");
    cleanupFragment(fragment);

    fragment = new SceneformFragment();
    getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commitAllowingStateLoss();

    fragment.getLifecycle().addObserver(new LifecycleObserver() {
      @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
      public void connectListener() {
        SceneformFragment sceneformFragment = (SceneformFragment) fragment;

        // Keeping it simple, and just look for a tap event on the fragment.
        // Had this been an actual application, gesture processing would be more appropriate.
        sceneformFragment.getSceneView().setOnClickListener(PolyGalleryActivity.this::onSceneTouch);
        sceneformFragment.getSceneView().getScene().addOnUpdateListener(
                PolyGalleryActivity.this::onSceneUpdate);
        fragment.getLifecycle().removeObserver(this);

        // Initialize the scene since AR is not used, we need to position the camera.
        initializeNonArScene();
      }
    });
  }

  /**
   * Move the camera to a reasonable default to simulate where the camera would be in AR mode.
   */
  private void initializeNonArScene() {
    Camera camera = getScene().getCamera();
    camera.setWorldPosition(STARTING_CAMERA_POSITION);
    camera.setWorldRotation(STARTING_CAMERA_ROTATION);
  }


  /**
   * Abstracts the getting of the Scene object.
   * @return The Scenform scene or null if a SceneView is not available.
   */
  private Scene getScene() {
    SceneView sceneView = null;
    if (fragment instanceof ArFragment) {
      sceneView = ((ArFragment)fragment).getArSceneView();
    } else if (fragment instanceof SceneformFragment) {
      sceneView =  ((SceneformFragment) fragment).getSceneView();
    }
    return sceneView != null ? sceneView.getScene() : null;
  }

  /**
   * Clean up when swapping out fragments.  This removes listeners and also removes all the
   * Sceneform objects since they are bound to the Sceneform.
   * @param fragment - the fragment of interest.
   */
  private void cleanupFragment(Fragment fragment) {
    if (fragment == null) {
      return;
    }
    Scene scene = null;
    if (fragment instanceof ArFragment) {
      scene = ((ArFragment) fragment).getArSceneView().getScene();
      ((ArFragment)fragment).setOnTapArPlaneListener(null);
    } else if (fragment instanceof SceneformFragment){
      SceneView view =  ((SceneformFragment) fragment).getSceneView();
      if (view != null) {
        scene = view.getScene();
        view.setOnClickListener(null);
      }
    }
    if (scene != null) {
      scene.removeOnUpdateListener(this::onSceneUpdate);
    }

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


  /**
   * Initializes the gallery that will hold the poly objects.
   * @param view - the Recycler view.
   */
  private void intializeGallery(RecyclerView view) {
    LinearLayoutManager layoutManager = new LinearLayoutManager(this);
    layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
    view.setLayoutManager(layoutManager);
    view.setItemAnimator(null);
    view.setHasFixedSize(false);
  }

  /**
   * Handle searching Poly for models.
   * @param view - the button/view triggering the search.
   */
  private void onSearch(View view) {
    final View search_dialog = this.getLayoutInflater().inflate(R.layout.search_dialog,
            (ViewGroup) view.getParent(), false);
    ((TextView) search_dialog.findViewById(R.id.keywords)).setText("fruit");
    new AlertDialog.Builder(this).setTitle("Search Poly")
            .setView(search_dialog)
            .setPositiveButton("Search", (dialogInterface, i) -> {
                      String kw = ((TextView) search_dialog.findViewById(R.id.keywords)).getText().toString();
                      PolyApi.ListAssets(kw, false, "", mBackgroundThreadHandler,
                              new AsyncHttpRequest.CompletionListener() {
                                @Override
                                public void onHttpRequestSuccess(byte[] responseBody) {
                                  try {
                                    final List<GalleryItem> items = GalleryAdapter.parseListResults(
                                            responseBody,mBackgroundThreadHandler);
                                    runOnUiThread(() -> {
                                      GalleryAdapter galleryAdapter = new GalleryAdapter(items);
                                      gallery.setAdapter(galleryAdapter);
                                    });
                                  } catch (IOException e) {
                                    handleRequestFailure(-1, "Error parsing list", e);
                                  }
                                }
                                @Override
                                public void onHttpRequestFailure(int statusCode, String message, Exception exception) {
                                  // Something went wrong with the request.
                                  handleRequestFailure(statusCode, message, exception);
                                }
                              });
                    }
            )
            .setCancelable(true)
            .create().show();
  }

  /**
   * Called on every frame.  This updates the information and moves nodes as needed.
   * @param frameTime - unused.
   */
  private void onSceneUpdate(FrameTime frameTime) {

    // Show the "what to do" text until there is a model selected and placed.
    if (gallery.getAdapter() == null || gallery.getAdapter().getItemCount() == 0) {
      setInfoText("Search Poly for models");
      return;
    }
    if (modelNode == null) {
      setInfoText("Select a model and tap a plane to place");
      return;
    }

    Scene scene = getScene();

    if (scene == null) {
      return;
    }

    Camera camera = scene.getCamera();

    setModelInfo(modelNode, camera);

    // Rotate the card to look at the camera.
    if (infoCard != null) {
      Vector3 cameraPosition = camera.getWorldPosition();
      Vector3 cardPosition = infoCard.getWorldPosition();
      Vector3 direction = Vector3.subtract(cameraPosition, cardPosition);
      Quaternion lookRotation = Quaternion.lookRotation(direction, Vector3.up());
      infoCard.setWorldRotation(lookRotation);
    }
  }

  private void setModelInfo(Node modelNode, Camera camera) {
    if (modelNode.getRenderable() != null) {
      Vector3 scale = modelNode.getLocalScale();
      Vector3 size = ((Box) modelNode.getCollisionShape()).getSize();
      size.x *= scale.x;
      size.y *= scale.y;
      size.z *= scale.z;
      Vector3 dir = Vector3.subtract(modelNode.getForward(), camera.getForward());
      @SuppressLint("DefaultLocale")
      String msg =
              String.format("scale: (%.02f, %.02f, %.02f)",
                      scale.x,
                      scale.y,
                      scale.z) +
                      "\n" +
                      String.format("size: (%.02f, %.02f, %.02f)",
                              size.x,
                              size.y,
                              size.z) +
                      "\n" +
                      String.format("dir: (%.02f, %.02f, %.02f)",
                              dir.x,
                              dir.y,
                              dir.z);

      setInfoText(msg);
    }
  }

  private void onSceneTouch(View view) {
    // Place a node at the center of the view.

    SceneView sceneView = (SceneView) view;
    Ray ray =
    sceneView.getScene().getCamera().screenPointToRay(sceneView.getWidth()/2,
            sceneView.getHeight() - sceneView.getHeight()/4);

    Vector3 pos = ray.getPoint(1f);

    // Get the model selected from the Gallery.
    GalleryItem selectedItem = ((GalleryAdapter) gallery.getAdapter()).getSelected();
    if (selectedItem == null) {
      return;
    }
    // Update the status.
    setInfoText("loading model " + selectedItem.getDisplayName());

    if (modelNode != null) {
      modelNode.setParent(null);
    }
    //Create the model node each time so scale and rotation is reset.
    modelNode = new Node();
    modelNode.setParent(getScene());
    modelNode.setWorldPosition(pos);

    attachInfoCardNode(selectedItem);

    // Set the renderable from the gallery.
    selectedItem.getRenderableHolder().thenAccept(renderable -> {
      // Create the transformable andy and add it to the anchor.
      modelNode.setRenderable(renderable);
      infoCard.setLocalPosition(new Vector3(0,getRenderableHeight(renderable),0));
    }).exceptionally(throwable -> {
      handleRequestFailure(-1, throwable.getMessage(), (Exception) throwable);
      return null;
    });


  }

  private void onTapPlane(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
    // Clean up old anchor
     if (anchorNode != null && anchorNode.getAnchor() != null) {
       anchorNode.getAnchor().detach();
     } else {
       anchorNode = new AnchorNode();
       anchorNode.setParent(getScene());
     }

     // Get the model selected from the Gallery.
    GalleryItem selectedItem = ((GalleryAdapter) gallery.getAdapter()).getSelected();
    if (selectedItem == null) {
      return;
    }

    // Create the Anchor.
    Anchor anchor = hitResult.createAnchor();
    anchorNode.setAnchor(anchor);

    // Update the status.
    setInfoText("loading model " + selectedItem.getDisplayName());

    if (modelNode != null) {
      modelNode.setParent(null);
    }

    TransformableNode transformableNode = new TransformableNode(
            ((ArFragment)fragment).getTransformationSystem());
    modelNode = transformableNode;
    modelNode.setParent(anchorNode);

    attachInfoCardNode(selectedItem);

    // Set the renderable from the gallery.
    selectedItem.getRenderableHolder().thenAccept(renderable -> {
      // Create the transformable andy and add it to the anchor.
      modelNode.setRenderable(renderable);
      transformableNode.select();
      setScaleRange(transformableNode, .01f, 3f);
      if (infoCard != null) {
        infoCard.setLocalPosition(new Vector3(0, getRenderableHeight(renderable), 0));
      }
    }).exceptionally(throwable -> {
      handleRequestFailure(-1, throwable.getMessage(), (Exception) throwable);
      return null;
    });
  }

  private void attachInfoCardNode(GalleryItem selectedItem) {
    if ( infoCard == null) {
      infoCard = new Node();
      ViewRenderable.builder()
              .setView(this, R.layout.model_info)
              .build()
              .thenAccept(
                      (renderable) -> {
                        infoCard.setRenderable(renderable);
                        setModelLabel(renderable, selectedItem);
                      })
              .exceptionally(
                      (throwable) -> {
                        throw new AssertionError("Could not load plane card view.", throwable);
                      });
    }
    else {
      setModelLabel((ViewRenderable) infoCard.getRenderable(), selectedItem);
    }
    infoCard.setParent(modelNode);
    float height = .5f;
    if (modelNode.getRenderable() instanceof ModelRenderable) {
      height = getRenderableHeight((ModelRenderable) modelNode.getRenderable());
    }
    infoCard.setLocalPosition(new Vector3(0,height,0));
  }

  private float getRenderableHeight(ModelRenderable renderable) {
    Box box = (Box) renderable.getCollisionShape();
   return  box.getCenter().y + box.getExtents().y;
  }

  private static void setScaleRange(TransformableNode node, float minSize, float maxSize) {
    // Set the min/max scale based on size not factors
    Vector3 size = ((Box) node.getRenderable().getCollisionShape()).getSize();
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

  private void setModelLabel(ViewRenderable viewRenderable, GalleryItem selectedItem) {
    TextView textView = (TextView) viewRenderable.getView();
    textView.setText(selectedItem.getDisplayName() + " by " + selectedItem.getAuthor() +
            "\n" + selectedItem.getLicense());
  }

  /**
   * Set the text of the info overlay.
   * @param msg
   */
  private void setInfoText(String msg) {
    if (model_info != null) {
      model_info.setText(msg);
    }
  }

  // NOTE: this runs on the background thread.
  private void handleRequestFailure(int statusCode, String message, Exception exception) {
    runOnUiThread(() ->
    {
      String msg = "Request failed. Status code " + statusCode + ", message: " + message +
              ((exception != null) ? ", exception: " + exception : "");
      new AlertDialog.Builder(this).setTitle("Error").
              setMessage(msg).create().show();
      Log.e(TAG, msg);
      if (exception != null) exception.printStackTrace();
    });
  }
}
