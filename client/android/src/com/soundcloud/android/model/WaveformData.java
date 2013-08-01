package com.soundcloud.android.model;


/**
 * Waveform sample data.
 * <p/>
 * <pre>
 *  $ curl 'http://wis.sndcdn.com/H9uGzKOYK5Ph_m.png'
 *  {
 *      "width": 1800,
 *      "height": 140,
 *      "samples": [3,6,7,8,24,63,130,...]
 *  }
 * </pre>
 *
 * @see <a href="https://github.com/soundcloud/waveform-image-samples">Waveform Image Samples</a>
 */
public class WaveformData {
    public final int maxAmplitude;
    public final int[] samples;

    public WaveformData(int height, int[] samples) {
        maxAmplitude = height;
        this.samples = samples;
    }

    /**
     * @param requiredWidth the new width
     * @return the waveform data downsampled to the required width
     */
    public WaveformData scale(int requiredWidth) {
        if (requiredWidth <= 0) throw new IllegalArgumentException("Invalid width: " + requiredWidth);
        if (requiredWidth == samples.length) {
            return this;
        } else {
            int[] newSamples = new int[requiredWidth];
            int newMax = 0;
            for (int i = 0; i < requiredWidth; i++) {
                final int offset = (int) Math.floor(samples.length / (double) requiredWidth * i);
                newSamples[i] = samples[Math.min(samples.length - 1, offset)];
                if (newSamples[i] > newMax) {
                    newMax = newSamples[i];
                }
            }
            return new WaveformData(newMax, newSamples);
        }
    }
}
