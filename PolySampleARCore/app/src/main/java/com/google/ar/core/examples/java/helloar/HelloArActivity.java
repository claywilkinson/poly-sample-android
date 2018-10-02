/*
 * Copyright 2017 Google Inc. All Rights Reserved.
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

package com.google.ar.core.examples.java.helloar;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3d model of the Android robot.
 */
public class HelloArActivity extends AppCompatActivity {
  private static final String TAG = HelloArActivity.class.getSimpleName();

  // The asset ID to download and display.
  private static final String ASSET_ID = "6b7Ul6MeLrJ";

  // Scale factor to apply to asset when displaying.
  private static final float ASSET_SCALE = 0.2f;

  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private ArFragment arFragment;

  private final ArrayList<AnchorNode> anchors = new ArrayList<>();

  // Attributions text to display for the object (title and author).
  private String mAttributionText = "";

  // Have we already shown the attribution toast?
  private boolean mShowedAttributionToast;
  private Renderable renderable;
  private Handler mBackgroundThreadHandler;

  private RecyclerView gallery;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
    arFragment.setOnTapArPlaneListener(this::onTapPlane);

    findViewById(R.id.fab).setOnClickListener(this::onSearch);


    // Create a background thread, where we will do the heavy lifting.
    // Our background thread, which does all of the heavy lifting so we don't block the main thread.
    HandlerThread mBackgroundThread = new HandlerThread("Worker");
    mBackgroundThread.start();
    // Handler for the background thread, to which we post background thread tasks.
     mBackgroundThreadHandler = new Handler(mBackgroundThread.getLooper());

    // Request the asset from the Poly API.
    Log.d(TAG, "Requesting asset "+ ASSET_ID);
    PolyApi.GetAsset(ASSET_ID, mBackgroundThreadHandler, new AsyncHttpRequest.CompletionListener() {
      @Override
      public void onHttpRequestSuccess(byte[] responseBody) {
        // Successfully fetched asset information. This does NOT include the model's geometry,
        // it's just the metadata. Let's parse it.
        parseAsset(responseBody);
      }
      @Override
      public void onHttpRequestFailure(int statusCode, String message, Exception exception) {
        // Something went wrong with the request.
        handleRequestFailure(statusCode, message, exception);
      }
    });

  }

  private void onSearch(View view) {
    PolyApi.ListAssets("android", false, "", mBackgroundThreadHandler,
            new AsyncHttpRequest.CompletionListener() {
              @Override
              public void onHttpRequestSuccess(byte[] responseBody) {
                parseListResults(responseBody);
              }

              @Override
              public void onHttpRequestFailure(int statusCode, String message, Exception exception) {
                // Something went wrong with the request.
                handleRequestFailure(statusCode, message, exception);
              }
            });
  }

  private void onTapPlane(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
    if (renderable == null) {
      return;
    }

    if (anchors.size() >= 20) {

      AnchorNode node = anchors.remove(0);
      if (node.getAnchor() != null) {
        node.getAnchor().detach();
      }
    }

    // Adding an Anchor tells ARCore that it should track this position in
    // space. This anchor is created on the Plane to place the 3D model
    // in the correct position relative both to the world and to the plane.

    // Create the Anchor.
    Anchor anchor = hitResult.createAnchor();
    AnchorNode anchorNode = new AnchorNode(anchor);
    anchorNode.setParent(arFragment.getArSceneView().getScene());

    // Create the transformable andy and add it to the anchor.
    TransformableNode andy = new TransformableNode(arFragment.getTransformationSystem());
    andy.setParent(anchorNode);
    andy.setRenderable(renderable);
    andy.select();

    anchors.add(anchorNode);
    if (!mShowedAttributionToast) {
      showAttributionToast();
    }
  }

  private void parseListResults(byte[] responseBody) {
    Log.d(TAG, "Got asset response (" + responseBody.length + " bytes). Parsing.");
    String assetBody = new String(responseBody, Charset.forName("UTF-8"));
    Log.d(TAG, assetBody);

    try {
      JSONObject response = new JSONObject(assetBody);
      gallery = findViewById(R.id.recyclerView);
      gallery.setHasFixedSize(true);

      LinearLayoutManager layoutManager = new LinearLayoutManager(this);
      layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
      gallery.setLayoutManager(layoutManager);

      List<GalleryAdapter.GalleryItem> items = new ArrayList<>();


      /* for each model
      ImageView cabin = new ImageView(this);
        cabin.setImageResource(R.drawable.cabin_thumb);
        cabin.setContentDescription("cabin");
        cabin.setOnClickListener(view ->{addObject(Uri.parse("Cabin.sfb"));});
        gallery.addView(cabin);
       */

      GalleryAdapter galleryAdapter = new GalleryAdapter(items);
      gallery.setAdapter(galleryAdapter);

    } catch (JSONException e) {
      Log.e(TAG, "JSON parsing error while processing response: " + e);
      e.printStackTrace();
    }
  }


    // NOTE: this runs on the background thread.
  private void parseAsset(byte[] assetData) {
    Log.d(TAG, "Got asset response (" + assetData.length + " bytes). Parsing.");
    String assetBody = new String(assetData, Charset.forName("UTF-8"));
    Log.d(TAG, assetBody);
    try {
      JSONObject response = new JSONObject(assetBody);
      String displayName = response.getString("displayName");
      String authorName = response.getString("authorName");
      Log.d(TAG, "Display name: " + displayName);
      Log.d(TAG, "Author name: " + authorName);
      mAttributionText = displayName + " by " + authorName;

      // Look for the glTF format URL.
      JSONArray formats = response.getJSONArray("formats");

      // The asset may have several formats (OBJ, GLTF, FBX, etc). We will look for the OBJ format.
      boolean foundObjFormat = false;
      for (int i = 0; i < formats.length(); i++) {
        JSONObject format = formats.getJSONObject(i);
        if (format.getString("formatType").equals("GLTF2")) {
          // Found the OBJ format. The format gives us the URL of the data files that we should
          // download (which include the OBJ file, the MTL file and the textures). We will now
          // request those files.
          requestRenderable(format);
         // requestDataFiles(format);
          foundObjFormat = true;
          break;
        }
      }
      if (!foundObjFormat) {
        // If this happens, it's because the asset doesn't have a representation in the OBJ
        // format. Since this simple sample code can only parse OBJ, we can't proceed.
        // But other formats might be available, so if your client supports multiple formats,
        // you could still try a different format instead.
        Log.e(TAG, "Could not find OBJ format in asset.");
      }
    } catch (JSONException jsonException) {
      Log.e(TAG, "JSON parsing error while processing response: " + jsonException);
      jsonException.printStackTrace();
    }
  }


  private void requestRenderable(JSONObject gltfFormat) throws JSONException {
    JSONObject rootFile = gltfFormat.getJSONObject("root");
   String url = rootFile.getString("url");
   String relativePath = rootFile.getString("relativePath");

    RenderableSource source = RenderableSource.builder().setSource(this,
            Uri.parse(url),RenderableSource.SourceType.GLTF2).setScale(ASSET_SCALE)
            .setRecenterMode(RenderableSource.RecenterMode.ROOT)
            .build();

    // Need to build from the uiThread
    runOnUiThread(() -> ModelRenderable.builder().setRegistryId(relativePath)
            .setSource(this, source)
            .build().thenAccept(
                    renderable -> this.renderable = renderable)
            .exceptionally(throwable -> {
              handleRequestFailure(500, "Exception loading model:", (Exception)throwable);
              return null;
            }));
  }

  // NOTE: this runs on the background thread.
  private void handleRequestFailure(int statusCode, String message, Exception exception) {
    // NOTE: because this is a simple sample, we don't have any real error handling logic
    // other than just printing the error. In an actual app, this is where you would take
    // appropriate action according to your app's use case. You could, for example, surface
    // the error to the user or retry the request later.
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

  private void showAttributionToast() {
    mShowedAttributionToast = true;
    runOnUiThread(() -> {
      // NOTE: we use a toast for showing attribution in this sample because it's the
      // simplest way to accomplish this. In your app, you are not required to use
      // a toast. You can display this attribution information in the most appropriate
      // way for your application.
      Toast.makeText(HelloArActivity.this, mAttributionText, Toast.LENGTH_LONG).show();
    });
  }
}
