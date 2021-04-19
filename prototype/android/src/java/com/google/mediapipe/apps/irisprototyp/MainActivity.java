// Copyright 2019 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.mediapipe.apps.irisprototyp;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.mediapipe.apps.base.R;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.framework.PacketGetter;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.HashMap;
import java.util.Map;

/**
 * Main activity of MediaPipe iris tracking app.
 */
public class MainActivity extends com.google.mediapipe.apps.base.MainActivity {
    private static final String TAG = "MainActivity";

    private static final String FOCAL_LENGTH_STREAM_NAME = "focal_length_pixel";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "iris_landmarks";

    private static final String LEFT_IRIS_DEPTH_MM = "left_iris_depth_mm";
    private static final String RIGHT_IRIS_DEPTH_MM = "right_iris_depth_mm";

    private boolean haveAddedSidePackets = false;

    private TextView textViewR;
    private TextView textViewL;

    private float right_depth;
    private float left_depth;

    private float distance_left_right = 6.3f;
    float difference = 0.0f;

    double alpha;
    double beta;
    double gamma;

    @Override
    protected void onCameraStarted(SurfaceTexture surfaceTexture) {
        super.onCameraStarted(surfaceTexture);

        // onCameraStarted gets called each time the activity resumes, but we only want to do this once.
        if (!haveAddedSidePackets) {
            float focalLength = cameraHelper.getFocalLengthPixels();
            if (focalLength != Float.MIN_VALUE) {
                Log.v(TAG, "inside focalLength != Float.MIN_VALUE : " + focalLength);
                Packet focalLengthSidePacket = processor.getPacketCreator().createFloat32(focalLength);
                Map<String, Packet> inputSidePackets = new HashMap<>();
                inputSidePackets.put(FOCAL_LENGTH_STREAM_NAME, focalLengthSidePacket);
                processor.setInputSidePackets(inputSidePackets);
            }

            processor.addPacketCallback(
                RIGHT_IRIS_DEPTH_MM,
                (packet) -> {
                    if (checkPhoneScreenLocked()) {
                        float right_depth_tmp = PacketGetter.getFloat32(packet);
                        right_depth = right_depth_tmp / 10;
                        textViewR.setText(String.valueOf(right_depth));
                        Log.v("BACKGROUND R", String.valueOf(right_depth));
                    }
                });

            processor.addPacketCallback(
                LEFT_IRIS_DEPTH_MM,
                (packet) -> {
                    if (checkPhoneScreenLocked()) {
                        float left_depth_tmp = PacketGetter.getFloat32(packet);
                        left_depth = left_depth_tmp / 10;
                        textViewL.setText(String.valueOf(left_depth));
                        Log.v("BACKGROUND L", String.valueOf(left_depth));
                    }
                });

            haveAddedSidePackets = true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        textViewR = findViewById(R.id.right);
        textViewL = findViewById(R.id.left);

        // To show verbose logging, run:
        // adb shell setprop log.tag.MainActivity VERBOSE
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            processor.addPacketCallback(
                    OUTPUT_LANDMARKS_STREAM_NAME,
                    (packet) -> {
                        byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                        try {
                            NormalizedLandmarkList landmarks = NormalizedLandmarkList.parseFrom(landmarksRaw);
                            if (landmarks == null) {
                                Log.v(TAG, "[TS:" + packet.getTimestamp() + "] No landmarks.");
                                return;
                            }
                            Log.v(
                                    TAG,
                                    "[TS:"
                                            + packet.getTimestamp()
                                            + "] #Landmarks for face (including iris): "
                                            + landmarks.getLandmarkCount());
                            Log.v(TAG, getLandmarksDebugString(landmarks));
                        } catch (InvalidProtocolBufferException e) {
                            Log.e(TAG, "Couldn't Exception received - " + e);
                            return;
                        }
                    });
        }
    }

    private static String getLandmarksDebugString(NormalizedLandmarkList landmarks) {
        int landmarkIndex = 0;
        String landmarksString = "";
        for (NormalizedLandmark landmark : landmarks.getLandmarkList()) {
            landmarksString +=
                    "\t\tLandmark["
                            + landmarkIndex
                            + "]: ("
                            + landmark.getX()
                            + ", "
                            + landmark.getY()
                            + ", "
                            + landmark.getZ()
                            + ")\n";
            ++landmarkIndex;
        }
        return landmarksString;
    }

    private void getGamma(){
        if (left_depth >= right_depth) {
            difference = left_depth - right_depth;
        } else {
            difference = right_depth - left_depth;
        }

        if (difference < distance_left_right) {
            double cosA = (Math.pow(left_depth, 2) - Math.pow(distance_left_right, 2) - Math.pow(right_depth, 2)) / (-2 * distance_left_right * right_depth);
            alpha = Math.acos(cosA);
            Log.v("ALPHA", String.valueOf(alpha));

            double cosB = (Math.pow(distance_left_right, 2) - Math.pow(left_depth, 2) - Math.pow(right_depth, 2)) / (-2 * left_depth * right_depth);
            beta = Math.acos(cosB);
            Log.v("BETA", String.valueOf(beta));

            gamma = 180 - alpha - beta;
            Log.v("GAMMA", String.valueOf(gamma));
        }
    }
}
