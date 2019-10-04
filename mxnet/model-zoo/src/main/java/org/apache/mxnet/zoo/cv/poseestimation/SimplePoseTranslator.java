/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package org.apache.mxnet.zoo.cv.poseestimation;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import software.amazon.ai.modality.cv.ImageTranslator;
import software.amazon.ai.modality.cv.Joints;
import software.amazon.ai.modality.cv.Joints.Joint;
import software.amazon.ai.modality.cv.util.BufferedImageUtils;
import software.amazon.ai.modality.cv.util.NDImageUtils;
import software.amazon.ai.ndarray.NDArray;
import software.amazon.ai.ndarray.NDList;
import software.amazon.ai.ndarray.index.NDIndex;
import software.amazon.ai.ndarray.types.Shape;
import software.amazon.ai.translate.TranslatorContext;

public class SimplePoseTranslator extends ImageTranslator<Joints> {

    private int imageWidth = 192;
    private int imageHeight = 256;

    @Override
    public NDList processInput(TranslatorContext ctx, BufferedImage input) {
        input = BufferedImageUtils.resize(input, imageWidth, imageHeight);
        return super.processInput(ctx, input);
    }

    @Override
    public Joints processOutput(TranslatorContext ctx, NDList list) {
        NDArray pred = list.head();
        int numJoints = (int) pred.getShape().get(1);
        int height = (int) pred.getShape().get(2);
        int width = (int) pred.getShape().get(3);
        NDArray predReshaped = pred.reshape(new Shape(1, numJoints, -1));
        NDArray maxIndices = predReshaped.argmax(2, true);
        NDArray maxValues = predReshaped.max(new int[] {2}, true);

        NDArray result = maxIndices.tile(2, 2);

        result.set(new NDIndex(":, :, 0"), result.get(":, :, 0").mod(width));
        result.set(new NDIndex(":, :, 1"), result.get(":, :, 1").div(width).floor());

        NDArray predMask = maxValues.gt(0.0).tile(2, 2);
        float[] flattened = result.mul(predMask).toFloatArray();
        float[] flattenedConfidence = maxValues.toFloatArray();
        List<Joint> joints = new ArrayList<>(numJoints);
        for (int i = 0; i < numJoints; i++) {
            joints.add(
                    new Joint(
                            flattened[i * 2] / width,
                            flattened[i * 2 + 1] / height,
                            flattenedConfidence[i]));
        }
        return new Joints(joints);
    }

    @Override
    protected NDArray normalize(NDArray array) {
        float[] mean = {0.485f, 0.456f, 0.406f};
        float[] std = {0.229f, 0.224f, 0.225f};
        return NDImageUtils.normalize(array, mean, std);
    }
}
