// DO NOT EDIT THIS FILE - it is automatically generated, ALL YOUR CHANGES WILL BE OVERWRITTEN, edit the file under $JAVA_SRC_PATH dir
/*
Simple DirectMedia Layer
Java source code (C) 2009-2014 Sergii Pylypenko

This software is provided 'as-is', without any express or implied
warranty.  In no event will the authors be held liable for any damages
arising from the use of this software.

Permission is granted to anyone to use this software for any purpose,
including commercial applications, and to alter it and redistribute it
freely, subject to the following restrictions:

1. The origin of this software must not be misrepresented; you must not
   claim that you wrote the original software. If you use this software
   in a product, an acknowledgment in the product documentation would be
   appreciated but is not required. 
2. Altered source versions must be plainly marked as such, and must not be
   misrepresented as being the original software.
3. This notice may not be removed or altered from any source distribution.
*/

package hilled.pwnterm;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;


@SuppressWarnings("JniMissingFunction")
class AccelerometerReader implements SensorEventListener {

  private SensorManager _manager = null;
  public boolean openedBySDL = false;
  public static final GyroscopeListener gyro = new GyroscopeListener();
  public static final OrientationListener orientation = new OrientationListener();

  public AccelerometerReader(Context context) {
    _manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
  }

  public synchronized void stop() {
    if (_manager != null) {
      Log.i("SDL", "libSDL: stopping accelerometer/gyroscope/orientation");
      _manager.unregisterListener(this);
      _manager.unregisterListener(gyro);
      _manager.unregisterListener(orientation);
    }
  }

  public synchronized void start() {
    if ((Globals.UseAccelerometerAsArrowKeys || Globals.AppUsesAccelerometer) &&
      _manager != null && _manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
      Log.i("SDL", "libSDL: starting accelerometer");
      _manager.registerListener(this, _manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
    }
    if ((Globals.AppUsesGyroscope || Globals.MoveMouseWithGyroscope) &&
      _manager != null && _manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
      Log.i("SDL", "libSDL: starting gyroscope");
      _manager.registerListener(gyro, _manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME);
    }
    if ((Globals.AppUsesOrientationSensor) && _manager != null &&
      _manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null) {
      Log.i("SDL", "libSDL: starting orientation sensor");
      _manager.registerListener(orientation, _manager.getDefaultSensor(
        Sensor.TYPE_GAME_ROTATION_VECTOR),
        SensorManager.SENSOR_DELAY_GAME);
    }
  }

  public void onSensorChanged(SensorEvent event) {
    if (Globals.HorizontalOrientation) {
      if (gyro.invertedOrientation)
        nativeAccelerometer(-event.values[1], event.values[0], event.values[2]);
      else
        nativeAccelerometer(event.values[1], -event.values[0], event.values[2]);
    } else
      nativeAccelerometer(event.values[0], event.values[1], event.values[2]); // TODO: not tested!
  }

  public void onAccuracyChanged(Sensor s, int a) {
  }

  static class GyroscopeListener implements SensorEventListener {
    public boolean invertedOrientation = false;

    // Noise filter with sane initial values, so user will be able
    // to move gyroscope during the first 10 seconds, while the noise is measured.
    // After that the values are replaced by noiseMin/noiseMax.
    final float filterMin[] = new float[]{-0.05f, -0.05f, -0.05f};
    final float filterMax[] = new float[]{0.05f, 0.05f, 0.05f};

    // The noise levels we're measuring.
    // Large initial values, they will decrease, but never increase.
    float noiseMin[] = new float[]{-1.0f, -1.0f, -1.0f};
    float noiseMax[] = new float[]{1.0f, 1.0f, 1.0f};

    // The gyro data buffer, from which we care calculating min/max noise values.
    // The bigger it is, the more precise the calclations, and the longer it takes to converge.
    float noiseData[][] = new float[200][noiseMin.length];
    int noiseDataIdx = 0;

    // When we detect movement, we remove last few values of the measured data.
    // The movement is detected by comparing values to noiseMin/noiseMax of the previous iteration.
    int movementBackoff = 0;

    // Difference between min/max in the previous measurement iteration,
    // used to determine when we should stop measuring, when the change becomes negligilbe.
    float measuredNoiseRange[] = null;

    // How long the algorithm is running, to stop it if it does not converge.
    int measurementIteration = 0;

    public GyroscopeListener() {
    }

    void collectNoiseData(final float[] data) {
      for (int i = 0; i < noiseMin.length; i++) {
        if (data[i] < noiseMin[i] || data[i] > noiseMax[i]) {
          // Movement detected, this can converge our min/max too early, so we're discarding last few values
          if (movementBackoff < 0) {
            int discard = 10;
            if (-movementBackoff < discard)
              discard = -movementBackoff;
            noiseDataIdx -= discard;
            if (noiseDataIdx < 0)
              noiseDataIdx = 0;
          }
          movementBackoff = 10;
          return;
        }
        noiseData[noiseDataIdx][i] = data[i];
      }
      movementBackoff--;
      if (movementBackoff >= 0)
        return; // Also discard several values after the movement stopped
      noiseDataIdx++;

      if (noiseDataIdx < noiseData.length)
        return;

      measurementIteration++;
      Log.d("SDL", "GYRO_NOISE: Measuring in progress... " + measurementIteration);
      if (measurementIteration > 5) {
        // We've collected enough data to use our noise min/max values as a new filter
        System.arraycopy(noiseMin, 0, filterMin, 0, filterMin.length);
        System.arraycopy(noiseMax, 0, filterMax, 0, filterMax.length);
      }
      if (measurementIteration > 15) {
        Log.d("SDL", "GYRO_NOISE: Measuring done! Maximum number of iterations reached: " + measurementIteration);
        noiseData = null;
        measuredNoiseRange = null;
        return;
      }

      noiseDataIdx = 0;
      boolean changed = false;
      for (int i = 0; i < noiseMin.length; i++) {
        float min = 1.0f;
        float max = -1.0f;
        for (int ii = 0; ii < noiseData.length; ii++) {
          if (min > noiseData[ii][i])
            min = noiseData[ii][i];
          if (max < noiseData[ii][i])
            max = noiseData[ii][i];
        }
        // Increase the range a bit, for safe conservative filtering
        float middle = (min + max) / 2.0f;
        min += (min - middle) * 0.2f;
        max += (max - middle) * 0.2f;
        // Check if range between min/max is less then the current range, as a safety measure,
        // and min/max range is not jumping outside of previously measured range
        if (max - min < noiseMax[i] - noiseMin[i] && min >= noiseMin[i] && max <= noiseMax[i]) {
          // Move old min/max closer to the measured min/max, but do not replace the values altogether
          noiseMin[i] = (noiseMin[i] + min * 4.0f) / 5.0f;
          noiseMax[i] = (noiseMax[i] + max * 4.0f) / 5.0f;
          changed = true;
        }
      }

      Log.d("SDL", "GYRO_NOISE: MIN MAX: " + Arrays.toString(noiseMin) + " " + Arrays.toString(noiseMax));

      if (!changed)
        return;

      // Determine when to stop measuring - check that the previous min/max range is close enough to the current one

      float range[] = new float[noiseMin.length];
      for (int i = 0; i < noiseMin.length; i++)
        range[i] = noiseMax[i] - noiseMin[i];

      Log.d("SDL", "GYRO_NOISE: RANGE:   " + Arrays.toString(range) + " " + Arrays.toString(measuredNoiseRange));

      if (measuredNoiseRange == null) {
        measuredNoiseRange = range;
        return; // First iteration, skip further checks
      }

      for (int i = 0; i < range.length; i++) {
        if (measuredNoiseRange[i] / range[i] > 1.2f) {
          measuredNoiseRange = range;
          return;
        }
      }

      // We converged to the final min/max filter values, stop measuring
      System.arraycopy(noiseMin, 0, filterMin, 0, filterMin.length);
      System.arraycopy(noiseMax, 0, filterMax, 0, filterMax.length);
      noiseData = null;
      measuredNoiseRange = null;
      Log.d("SDL", "GYRO_NOISE: Measuring done! Range converged on iteration " + measurementIteration);
    }

    public void onSensorChanged(final SensorEvent event) {
      boolean filtered = true;
      final float[] data = event.values;

      if (noiseData != null)
        collectNoiseData(data);

      for (int i = 0; i < 3; i++) {
        if (data[i] < filterMin[i]) {
          filtered = false;
          data[i] -= filterMin[i];
        } else if (data[i] > filterMax[i]) {
          filtered = false;
          data[i] -= filterMax[i];
        }
      }

      if (filtered)
        return;

      if (Globals.HorizontalOrientation) {
        if (invertedOrientation)
          nativeGyroscope(-data[0], -data[1], data[2]);
        else
          nativeGyroscope(data[0], data[1], data[2]);
      } else {
        if (invertedOrientation)
          nativeGyroscope(-data[1], data[0], data[2]);
        else
          nativeGyroscope(data[1], -data[0], data[2]);
      }
    }

    public void onAccuracyChanged(Sensor s, int a) {
    }

    public boolean available(AppCompatActivity context) {
      SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
      return (manager != null && manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null);
    }

    public void registerListener(AppCompatActivity context, SensorEventListener l) {
      SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
      if (manager == null && manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) == null)
        return;
      manager.registerListener(gyro, manager.getDefaultSensor(
        Globals.AppUsesOrientationSensor ? Sensor.TYPE_GAME_ROTATION_VECTOR : Sensor.TYPE_GYROSCOPE),
        SensorManager.SENSOR_DELAY_GAME);
    }

    public void unregisterListener(AppCompatActivity context, SensorEventListener l) {
      SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
      if (manager == null)
        return;
      manager.unregisterListener(l);
    }
  }

  static class OrientationListener implements SensorEventListener {
    public OrientationListener() {
    }

    public void onSensorChanged(SensorEvent event) {
      nativeOrientation(event.values[0], event.values[1], event.values[2]);
    }

    public void onAccuracyChanged(Sensor s, int a) {
    }
  }

  private static native void nativeAccelerometer(float accX, float accY, float accZ);

  private static native void nativeGyroscope(float X, float Y, float Z);

  private static native void nativeOrientation(float X, float Y, float Z);
}
