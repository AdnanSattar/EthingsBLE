package demo.ethings.com.ethingsble.fusion.sensors;

public abstract class ISensorManager {

    public interface SensorEventListener {
        void onSensorChanged(int sensorType, float[] values);
    }

    protected SensorEventListener listener;

    public void setListener(SensorEventListener listener) {
        this.listener = listener;
    }

    public abstract ISensor getSensor(int sensorType);

    public abstract void enable() throws SensorManagerException;

    public abstract void disable();

    public abstract void registerSensor(int sensorType);

    public abstract void unregisterSensor(int sensorType);

    public abstract double[] patchSensorFusion(float[] values);
}
