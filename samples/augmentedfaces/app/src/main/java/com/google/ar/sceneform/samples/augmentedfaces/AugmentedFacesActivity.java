/*
 * Copyright 2019 Google LLC. All Rights Reserved.
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
package com.google.ar.sceneform.samples.augmentedfaces;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.Texture;
import com.google.ar.sceneform.ux.AugmentedFaceNode;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * This is an example activity that uses the Sceneform UX package to make common Augmented Faces
 * tasks easier.
 */
public class AugmentedFacesActivity extends AppCompatActivity {
  private static final String TAG = AugmentedFacesActivity.class.getSimpleName();

  private static final double MIN_OPENGL_VERSION = 3.0;

  private FaceArFragment arFragment;

  private ModelRenderable faceRegionsRenderable;
  private ModelRenderable faceRegionsRenderable2;

  private final HashMap<AugmentedFace, Set<Node>> faceNodeMap = new HashMap<>();
  private String GLTF_ASSET = "https://getcharly.s3-us-west-2.amazonaws.com/file/122277006.glb";
  private String GLTF_ASSET2 = "https://getcharly.s3-us-west-2.amazonaws.com/file/4082146436+(1).glb";

  @Override
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  // CompletableFuture requires api level 24
  // FutureReturnValueIgnored is not valid
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!checkIsSupportedDeviceOrFinish(this)) {
      return;
    }

    setContentView(R.layout.activity_face_mesh);
    arFragment = (FaceArFragment) getSupportFragmentManager().findFragmentById(R.id.face_fragment);

    // Load the face regions renderable.
    // This is a skinned model that renders 3D objects mapped to the regions of the augmented face.

    RenderableSource x = RenderableSource.builder().setSource(
            this,
            Uri.parse(GLTF_ASSET),
            RenderableSource.SourceType.GLB)
            .setScale(0.175f)
            .setRecenterMode(RenderableSource.RecenterMode.CENTER)
            .build();


    ModelRenderable.builder()
        .setSource(this, x)
        .build()
        .thenAccept(
            modelRenderable -> {
              faceRegionsRenderable = modelRenderable;
              modelRenderable.setShadowCaster(false);
              modelRenderable.setShadowReceiver(false);
            });

    RenderableSource y = RenderableSource.builder().setSource(
            this,
            Uri.parse(GLTF_ASSET2),
            RenderableSource.SourceType.GLB)
            .setScale(0.150f)
            .setRecenterMode(RenderableSource.RecenterMode.ROOT)
            .build();


    ModelRenderable.builder()
            .setSource(this, y)
            .build()
            .thenAccept(
                    modelRenderable -> {
                      faceRegionsRenderable2 = modelRenderable;
                      modelRenderable.setShadowCaster(false);
                      modelRenderable.setShadowReceiver(false);
                    });


    ArSceneView sceneView = arFragment.getArSceneView();

    // This is important to make sure that the camera stream renders first so that
    // the face mesh occlusion works correctly.
    sceneView.setCameraStreamRenderPriority(Renderable.RENDER_PRIORITY_FIRST);

    Scene scene = sceneView.getScene();

    scene.addOnUpdateListener(
        (FrameTime frameTime) -> {

          Collection<AugmentedFace> faceList =
              sceneView.getSession().getAllTrackables(AugmentedFace.class);

          // Make new AugmentedFaceNodes for any new faces.
          for (AugmentedFace face : faceList) {
            if (!faceNodeMap.containsKey(face)) {
              HashSet<Node> set = new HashSet();

              if (faceRegionsRenderable != null) {
                MyFaceNode hatNode = new MyFaceNode(face);
                hatNode.setParent(scene);
                hatNode.setRenderable(faceRegionsRenderable);
                set.add(hatNode);
              }

              if (faceRegionsRenderable2 != null) {
                ShadeNode shadeNode = new ShadeNode(face);
                shadeNode.setParent(scene);
                shadeNode.setRenderable(faceRegionsRenderable2);
                set.add(shadeNode);
              }

              faceNodeMap.put(face, set);
            }
          }

          // Remove any AugmentedFaceNodes associated with an AugmentedFace that stopped tracking.
          Iterator<Map.Entry<AugmentedFace, Set<Node>>> iter =
              faceNodeMap.entrySet().iterator();
          while (iter.hasNext()) {
            Map.Entry<AugmentedFace, Set<Node>> entry = iter.next();
            AugmentedFace face = entry.getKey();
            if (face.getTrackingState() == TrackingState.STOPPED) {
              for (Node node : entry.getValue()) {
                node.setParent(null);
              }
              iter.remove();
            }
          }
        });
  }

  /**
   * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
   * on this device.
   *
   * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
   *
   * <p>Finishes the activity if Sceneform can not run
   */
  public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
    if (ArCoreApk.getInstance().checkAvailability(activity)
        == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) {
      Log.e(TAG, "Augmented Faces requires ARCore.");
      Toast.makeText(activity, "Augmented Faces requires ARCore", Toast.LENGTH_LONG).show();
      activity.finish();
      return false;
    }
    String openGlVersionString =
        ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
            .getDeviceConfigurationInfo()
            .getGlEsVersion();
    if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
      Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
      Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
          .show();
      activity.finish();
      return false;
    }
    return true;
  }
}
