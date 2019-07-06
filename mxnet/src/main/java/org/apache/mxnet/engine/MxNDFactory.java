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
package org.apache.mxnet.engine;

import com.sun.jna.Pointer;
import java.nio.Buffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.mxnet.jna.JnaUtils;
import software.amazon.ai.Context;
import software.amazon.ai.ndarray.NDArray;
import software.amazon.ai.ndarray.NDFactory;
import software.amazon.ai.ndarray.types.DataDesc;
import software.amazon.ai.ndarray.types.DataType;
import software.amazon.ai.ndarray.types.Shape;
import software.amazon.ai.util.PairList;

public class MxNDFactory implements NDFactory {

    /**
     * A global {@link NDFactory} singleton instance.
     *
     * <p>This NDFactory is the root of all the other NDFactories. NDArrays created by this factory
     * are un-managed, user has to close them manually. Those NDArrays will be released on GC, and
     * might be run into out of native memory issue.
     */
    static final MxNDFactory SYSTEM_FACTORY = new SystemFactory();

    private static final NDArray[] EMPTY = new NDArray[0];

    private NDFactory parent;
    private Context context;
    private Map<AutoCloseable, AutoCloseable> resources;
    private AtomicBoolean closed = new AtomicBoolean(false);

    public static MxNDFactory getSystemFactory() {
        return SYSTEM_FACTORY;
    }

    private MxNDFactory(NDFactory parent, Context context) {
        this.parent = parent;
        this.context = context;
        resources = new ConcurrentHashMap<>();
    }

    public MxNDArray create(Pointer handle) {
        MxNDArray array = new MxNDArray(this, handle);
        attach(array);
        return array;
    }

    /** {@inheritDoc} */
    @Override
    public MxNDArray create(Context context, Shape shape, DataType dataType) {
        if (context == null) {
            context = this.context;
        }

        Pointer handle = JnaUtils.createNdArray(context, shape, dataType, shape.dimension(), false);
        MxNDArray array = new MxNDArray(this, handle, context, shape, dataType);
        attach(array);
        return array;
    }

    /** {@inheritDoc} */
    @Override
    public MxNDArray create(DataDesc dataDesc) {
        return create(dataDesc.getContext(), dataDesc.getShape(), dataDesc.getDataType());
    }

    @Override
    public MxNDArray create(float[] data, Context context, Shape shape) {
        MxNDArray array = create(context, shape, DataType.FLOAT32);
        array.set(data);
        return array;
    }

    @Override
    public MxNDArray create(int[] data, Context context, Shape shape) {
        MxNDArray array = create(context, shape, DataType.INT32);
        array.set(data);
        return array;
    }

    @Override
    public MxNDArray create(double[] data, Context context, Shape shape) {
        MxNDArray array = create(context, shape, DataType.FLOAT64);
        array.set(data);
        return array;
    }

    @Override
    public MxNDArray create(long[] data, Context context, Shape shape) {
        MxNDArray array = create(context, shape, DataType.INT64);
        array.set(data);
        return array;
    }

    @Override
    public MxNDArray create(byte[] data, Context context, Shape shape) {
        MxNDArray array = create(context, shape, DataType.INT8);
        array.set(data);
        return array;
    }

    /** {@inheritDoc} */
    @Override
    public MxNDArray create(DataDesc dataDesc, Buffer data) {
        MxNDArray array =
                create(dataDesc.getContext(), dataDesc.getShape(), dataDesc.getDataType());
        array.set(data);
        return array;
    }

    /** {@inheritDoc} */
    @Override
    public NDArray zeros(Context context, Shape shape, DataType dataType) {
        return fill("_zeros", context, shape, dataType);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray zeros(DataDesc dataDesc) {
        return zeros(dataDesc.getContext(), dataDesc.getShape(), dataDesc.getDataType());
    }

    /** {@inheritDoc} */
    @Override
    public NDArray ones(Context context, Shape shape, DataType dataType) {
        return fill("_ones", context, shape, dataType);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray ones(DataDesc dataDesc) {
        return ones(dataDesc.getContext(), dataDesc.getShape(), dataDesc.getDataType());
    }

    /** {@inheritDoc} */
    @Override
    public NDArray arange(int start, int stop, int step, Context context, DataType dataType) {
        MxOpParams params = new MxOpParams();
        params.addParam("start", start);
        params.addParam("stop", stop);
        params.addParam("step", step);
        params.setDataType(dataType);
        params.setContext(context);
        return invoke("_npi_arange", EMPTY, params)[0];
    }

    /** {@inheritDoc} */
    @Override
    public NDArray linspace(double start, double stop, int num, boolean endpoint, Context context) {
        if (num < 0) {
            throw new IllegalArgumentException("Num argument must be non-negative");
        }
        MxOpParams params = new MxOpParams();
        params.addParam("start", start);
        params.addParam("stop", stop);
        params.addParam("num", num);
        params.addParam("endpoint", endpoint);
        params.setDataType(DataType.FLOAT32);
        params.setContext(context);
        return invoke("_npi_linspace", EMPTY, params)[0];
    }

    /** {@inheritDoc} */
    @Override
    public NDArray randomUniform(
            double low, double high, Shape shape, Context context, DataType dataType) {
        MxOpParams params = new MxOpParams();
        params.addParam("low", low);
        params.addParam("high", high);
        params.setShape(shape);
        params.setContext(context);
        params.setDataType(dataType);
        return invoke("_npi_random_uniform", EMPTY, params)[0];
    }

    /** {@inheritDoc} */
    @Override
    public NDArray randomNormal(
            double loc, double scale, Shape shape, Context context, DataType dataType) {
        MxOpParams params = new MxOpParams();
        params.addParam("loc", loc);
        params.addParam("scale", scale);
        params.setShape(shape);
        params.setContext(context);
        params.setDataType(dataType);
        return invoke("_npi_random_normal", EMPTY, params)[0];
    }

    /** {@inheritDoc} */
    @Override
    public NDArray randomNormal(Shape shape, Context context, DataType dataType) {
        return randomNormal(0f, 1f, shape, context, dataType);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray randomMultinomial(int n, NDArray pValues, Shape shape) {
        MxOpParams params = new MxOpParams();
        params.addParam("n", n);
        params.setShape("size", shape);
        return invoke("_npi_multinomial", pValues, params);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray randomMultinomial(int n, NDArray pValues) {
        MxOpParams params = new MxOpParams();
        params.addParam("n", n);
        return invoke("_npi_multinomial", pValues, params);
    }

    /** {@inheritDoc} */
    @Override
    public NDFactory getParentFactory() {
        return parent;
    }

    /** {@inheritDoc} */
    @Override
    public MxNDFactory newSubFactory() {
        return newSubFactory(context);
    }

    /** {@inheritDoc} */
    @Override
    public MxNDFactory newSubFactory(Context context) {
        MxNDFactory factory = new MxNDFactory(this, context);
        attach(factory);
        return factory;
    }

    /** {@inheritDoc} */
    @Override
    public Context getContext() {
        return context;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void attach(AutoCloseable resource) {
        if (closed.get()) {
            throw new IllegalStateException("NDFactor has been closed already.");
        }
        resources.put(resource, resource);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void detach(AutoCloseable resource) {
        if (closed.get()) {
            throw new IllegalStateException("NDFactor has been closed already.");
        }
        resources.remove(resource);
    }

    /** {@inheritDoc} */
    @Override
    public void invoke(
            String operation, NDArray[] src, NDArray[] dest, PairList<String, ?> params) {
        JnaUtils.op(operation).invoke(this, src, dest, params);
    }

    /** {@inheritDoc} */
    @Override
    public NDArray[] invoke(String operation, NDArray[] src, PairList<String, ?> params) {
        return JnaUtils.op(operation).invoke(this, src, params);
    }

    public NDArray invoke(String operation, NDArray src, PairList<String, ?> params) {
        return JnaUtils.op(operation).invoke(this, src, params)[0];
    }

    public NDArray invoke(String operation, PairList<String, ?> params) {
        return JnaUtils.op(operation).invoke(this, EMPTY, params)[0];
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void close() {
        if (!closed.getAndSet(true)) {
            for (AutoCloseable resource : resources.keySet()) {
                try {
                    resource.close();
                } catch (Exception ignore) {
                    // ignore
                }
            }
            parent.detach(this);
            resources.clear();
        }
    }

    boolean isOpen() {
        return !closed.get();
    }

    private NDArray fill(String opName, Context context, Shape shape, DataType dataType) {
        MxOpParams params = new MxOpParams();
        if (shape == null) {
            throw new IllegalArgumentException("Shape is required for " + opName.substring(1));
        }
        params.setShape(shape);
        params.setContext(context == null ? this.context : context);
        params.setDataType(dataType);
        return invoke(opName, params);
    }

    private static final class SystemFactory extends MxNDFactory {

        SystemFactory() {
            super(null, Context.defaultContext());
        }

        /** {@inheritDoc} */
        @Override
        public void attach(AutoCloseable resource) {}

        /** {@inheritDoc} */
        @Override
        public void detach(AutoCloseable resource) {}

        /** {@inheritDoc} */
        @Override
        public void close() {}
    }
}
